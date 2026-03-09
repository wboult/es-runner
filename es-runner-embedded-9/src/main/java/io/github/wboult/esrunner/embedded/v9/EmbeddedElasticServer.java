package io.github.wboult.esrunner.embedded.v9;

import io.github.wboult.esrunner.ElasticClient;
import io.github.wboult.esrunner.ElasticServerHandle;
import io.github.wboult.esrunner.ElasticServerType;
import io.github.wboult.esrunner.embedded.EmbeddedElasticServerConfig;
import io.github.wboult.esrunner.embedded.EmbeddedHome;
import io.github.wboult.esrunner.embedded.EmbeddedModuleProfile;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.logging.LogConfigurator;
import org.elasticsearch.env.Environment;
import org.elasticsearch.http.HttpServerTransport;
import org.elasticsearch.node.InternalSettingsPreparer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Manages an Elasticsearch 9.3.x node running inside the current JVM process.
 *
 * <h2>Strategy: filesystem-modules</h2>
 * <p>{@code esHome} must point at the root of a fully-extracted Elasticsearch 9.3.x
 * distribution (the directory that contains {@code bin/}, {@code lib/}, {@code modules/},
 * {@code plugins/}). Before node startup, ES Runner stages a smaller embedded home from
 * that extracted distro. The current "simple embedded" profile keeps the standard modules
 * needed for HTTP/index/search and excludes the modules that currently break or complicate
 * in-JVM startup on 9.3.1.
 *
 * <p>The staged home is then used with Elasticsearch's public
 * {@link org.elasticsearch.node.Node#Node(Environment, org.elasticsearch.plugins.PluginsLoader)}
 * constructor, which loads modules from the staged embedded home rather than relying on a
 * normal standalone server bootstrap.
 *
 * <h2>Why this sidesteps the "unpublished modules" problem</h2>
 * <p>Elastic does not publish internal module jars (like {@code analysis-common}) to any
 * Maven repository. By staging a filtered home from the real extracted distribution and
 * calling {@code new Node(environment)}, we let ES load those jars the same way it does
 * in production — from the filesystem — while still controlling which built-in modules
 * participate in the embedded profile.
 *
 * <h2>Data / logs isolation</h2>
 * <p>The caller normally configures a {@code workDir} through
 * {@link EmbeddedElasticServerConfig}. ES Runner derives {@code data/}, {@code logs/},
 * and staged {@code home/} directories under that work directory.
 *
 * <h2>Limitations (9.3.1)</h2>
 * <ul>
 *   <li>The current simple profile excludes the {@code x-pack-security} module, so
 *       TLS/auth are not available.</li>
 *   <li>The current simple profile excludes {@code x-pack-ml}, because ML native startup
 *       fails inside the shared test JVM on Windows.</li>
 *   <li>The current simple profile also excludes {@code x-pack-esql}, which depends on
 *       {@code x-pack-ml} in 8.19.</li>
 *   <li>Staged module descriptors have {@code modulename=} stripped so ES loads them through
 *       plain classloaders rather than Java module layers, which do not work cleanly from this
 *       embedded classpath bootstrap.</li>
 *   <li>The ES node shares the JVM with the calling code, so this remains an experimental
 *       mode rather than a general replacement for the external-process runner.</li>
 *   <li>Repeated embedded start/stop in one JVM requires resetting a small amount of
 *       Elasticsearch logging state after shutdown.</li>
 *   <li>The configured work directory is retained on close, rather than being deleted
 *       eagerly, because Windows can keep staged module jars locked briefly after the
 *       node shuts down.</li>
 * </ul>
 */
public final class EmbeddedElasticServer implements ElasticServerHandle {

    private static final AtomicBoolean LOGGING_BOOTSTRAPPED = new AtomicBoolean();
    private static final EmbeddedModuleProfile PROFILE =
            EmbeddedModuleProfile.load(EmbeddedElasticServer.class, "embedded-profile.properties");

    private final EmbeddedElasticServerConfig config;
    private final EmbeddedNode node;
    private final URI baseUri;
    private final ElasticClient client;
    private final Path stagedHome;
    private final Path workDir;
    private final Path dataDir;
    private final Path logsDir;

    private EmbeddedElasticServer(EmbeddedElasticServerConfig config,
                                  EmbeddedNode node,
                                  URI baseUri,
                                  Path stagedHome,
                                  Path workDir,
                                  Path dataDir,
                                  Path logsDir) {
        this.config = Objects.requireNonNull(config, "config");
        this.node = node;
        this.baseUri = baseUri;
        this.client = new ElasticClient(baseUri);
        this.stagedHome = stagedHome;
        this.workDir = workDir;
        this.dataDir = dataDir;
        this.logsDir = logsDir;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Starts an embedded Elasticsearch node using the filesystem-modules approach.
     *
     * @param esHome root of an extracted ES 9.3.x distribution
     * @return a started {@code EmbeddedElasticServer}; caller must {@link #close()} it
     * @throws Exception if the node fails to start
     */
    public static EmbeddedElasticServer start(Path esHome) throws Exception {
        return start(defaultConfig(esHome));
    }

    /**
     * Starts an embedded Elasticsearch node using the provided configuration.
     *
     * @param config embedded server configuration
     * @return a started {@code EmbeddedElasticServer}; caller must {@link #close()} it
     * @throws Exception if the node fails to start
     */
    public static EmbeddedElasticServer start(EmbeddedElasticServerConfig config) throws Exception {
        EmbeddedElasticServerConfig resolved = Objects.requireNonNull(config, "config");
        resetProcessGlobalLoggingState();
        Files.createDirectories(resolved.workDir());
        Files.createDirectories(resolved.dataDir());
        Files.createDirectories(resolved.logsDir());
        EmbeddedHome.deleteTreeQuietly(resolved.stagedHomeDir());
        Path stagedHome = EmbeddedHome.prepareBundledHome(
                resolved.esHome(),
                resolved.stagedHomeDir(),
                resolved.includedModules());

        Settings settings = buildSettings(resolved);
        Environment env = InternalSettingsPreparer.prepareEnvironment(
                settings, Collections.emptyMap(), null, resolved::nodeName);

        bootstrapLogging(settings);

        EmbeddedNode node = null;
        try {
            node = new EmbeddedNode(env);
            node.start();

            HttpServerTransport http = node.injector().getInstance(HttpServerTransport.class);
            int port = http.boundAddress().publishAddress().getPort();
            URI baseUri = URI.create("http://127.0.0.1:" + port + "/");

            EmbeddedElasticServer server = new EmbeddedElasticServer(
                    resolved,
                    node,
                    baseUri,
                    stagedHome,
                    resolved.workDir(),
                    resolved.dataDir(),
                    resolved.logsDir());
            awaitStartup(server, resolved.startupTimeout());
            return server;
        } catch (Exception e) {
            closeQuietly(node, resolved.shutdownTimeout());
            EmbeddedHome.deleteTreeQuietly(stagedHome);
            resetProcessGlobalLoggingState();
            throw e;
        }
    }

    /**
     * Builds a configuration from the defaults, then starts an embedded node.
     *
     * @param consumer config customizer
     * @return a started {@code EmbeddedElasticServer}; caller must {@link #close()} it
     * @throws Exception if the node fails to start
     */
    public static EmbeddedElasticServer start(Consumer<EmbeddedElasticServerConfig.Builder> consumer) throws Exception {
        EmbeddedElasticServerConfig.Builder builder = EmbeddedElasticServerConfig.builder()
                .includedModules(PROFILE.includedModules());
        consumer.accept(builder);
        return start(builder.build());
    }

    /**
     * Starts an embedded node, runs an action, and closes the node afterwards.
     *
     * @param config embedded server configuration
     * @param action action to run
     */
    public static void withServer(EmbeddedElasticServerConfig config, Consumer<EmbeddedElasticServer> action) {
        try (EmbeddedElasticServer server = start(config)) {
            action.accept(server);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start embedded Elasticsearch", e);
        }
    }

    /**
     * Starts an embedded node, runs a function, and closes the node afterwards.
     *
     * @param config embedded server configuration
     * @param action function to run
     * @param <T> return type
     * @return function result
     */
    public static <T> T withServer(EmbeddedElasticServerConfig config, Function<EmbeddedElasticServer, T> action) {
        try (EmbeddedElasticServer server = start(config)) {
            return action.apply(server);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start embedded Elasticsearch", e);
        }
    }

    /**
     * Starts an embedded node, runs an action, and closes the node afterwards.
     *
     * @param consumer config customizer
     * @param action action to run
     */
    public static void withServer(Consumer<EmbeddedElasticServerConfig.Builder> consumer,
                                  Consumer<EmbeddedElasticServer> action) {
        EmbeddedElasticServerConfig.Builder builder = EmbeddedElasticServerConfig.builder()
                .includedModules(PROFILE.includedModules());
        consumer.accept(builder);
        withServer(builder.build(), action);
    }

    /**
     * Starts an embedded node, runs a function, and closes the node afterwards.
     *
     * @param consumer config customizer
     * @param action function to run
     * @param <T> return type
     * @return function result
     */
    public static <T> T withServer(Consumer<EmbeddedElasticServerConfig.Builder> consumer,
                                   Function<EmbeddedElasticServer, T> action) {
        EmbeddedElasticServerConfig.Builder builder = EmbeddedElasticServerConfig.builder()
                .includedModules(PROFILE.includedModules());
        consumer.accept(builder);
        return withServer(builder.build(), action);
    }

    /**
     * Legacy convenience overload retained for callers already passing explicit data/log paths.
     *
     * @param esHome root of an extracted Elasticsearch distribution
     * @param dataDir data directory
     * @param logsDir logs directory
     * @return started embedded server
     * @throws Exception if startup fails
     */
    public static EmbeddedElasticServer start(Path esHome, Path dataDir, Path logsDir) throws Exception {
        Path workDir = inferWorkDir(dataDir, logsDir);
        return start(EmbeddedElasticServerConfig.builder()
                .esHome(esHome)
                .workDir(workDir)
                .includedModules(PROFILE.includedModules())
                .build());
    }

    /**
     * Legacy convenience overload retained for callers already passing explicit data/log paths.
     *
     * @param esHome root of an extracted Elasticsearch distribution
     * @param dataDir data directory
     * @param logsDir logs directory
     * @param extraSettings additional settings
     * @return started embedded server
     * @throws Exception if startup fails
     */
    public static EmbeddedElasticServer start(Path esHome,
                                              Path dataDir,
                                              Path logsDir,
                                              Map<String, String> extraSettings) throws Exception {
        return start(esHome, dataDir, logsDir, builder -> extraSettings.forEach(builder::put));
    }

    /**
     * Legacy convenience overload retained for callers already passing explicit data/log paths.
     *
     * @param esHome root of an extracted Elasticsearch distribution
     * @param dataDir data directory
     * @param logsDir logs directory
     * @param settingsCustomiser settings customizer
     * @return started embedded server
     * @throws Exception if startup fails
     */
    public static EmbeddedElasticServer start(Path esHome,
                                              Path dataDir,
                                              Path logsDir,
                                              Consumer<Settings.Builder> settingsCustomiser) throws Exception {
        Path workDir = inferWorkDir(dataDir, logsDir);
        Settings.Builder settingsBuilder = Settings.builder();
        settingsCustomiser.accept(settingsBuilder);
        Settings extraSettings = settingsBuilder.build();
        EmbeddedElasticServerConfig.Builder builder = EmbeddedElasticServerConfig.builder()
                .esHome(esHome)
                .workDir(workDir)
                .includedModules(PROFILE.includedModules());
        for (String key : extraSettings.keySet()) {
            builder.setting(key, extraSettings.get(key));
        }
        return start(builder.build());
    }

    /**
     * Returns the bundled module profile shipped with this 9.x runner.
     *
     * @return embedded module profile
     */
    public static EmbeddedModuleProfile profile() {
        return PROFILE;
    }

    /**
     * Returns the default 9.x embedded configuration for the supplied extracted home.
     *
     * @param esHome extracted Elasticsearch home
     * @return default 9.x embedded config
     */
    public static EmbeddedElasticServerConfig defaultConfig(Path esHome) {
        return EmbeddedElasticServerConfig.builder()
                .esHome(esHome)
                .includedModules(PROFILE.includedModules())
                .build();
    }

    private static void bootstrapLogging(Settings settings) {
        if (LOGGING_BOOTSTRAPPED.compareAndSet(false, true)) {
            // ES 8.19 initializes logging before it builds the plugin/module loader.
            // The minimal bootstrap is enough to install the logger provider that
            // PluginsLoader/ModuleQualifiedExportsService expect, while avoiding
            // process-global node-name/config-file state being reset on every test.
            LogConfigurator.registerErrorListener();
            LogConfigurator.configureWithoutConfig(settings);
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the immutable configuration used to start this embedded node.
     *
     * @return embedded server configuration
     */
    public EmbeddedElasticServerConfig config() {
        return config;
    }

    @Override
    public ElasticServerType type() {
        return ElasticServerType.EMBEDDED;
    }

    /** HTTP base URI, e.g. {@code http://127.0.0.1:51234}. */
    @Override
    public URI baseUri() {
        return baseUri;
    }

    /** The HTTP port chosen by the OS. */
    @Override
    public int httpPort() {
        return baseUri.getPort();
    }

    /**
     * Returns the shared HTTP client wrapper for this server.
     *
     * @return Elasticsearch client
     */
    @Override
    public ElasticClient client() {
        return client;
    }

    /**
     * Returns the embedded work directory.
     *
     * @return work directory
     */
    public Path workDir() {
        return workDir;
    }

    /**
     * Returns the embedded data directory.
     *
     * @return data directory
     */
    public Path dataDir() {
        return dataDir;
    }

    /**
     * Returns the embedded logs directory.
     *
     * @return logs directory
     */
    public Path logsDir() {
        return logsDir;
    }

    /**
     * Returns the staged filtered Elasticsearch home used for embedded startup.
     *
     * @return staged embedded home
     */
    public Path stagedHome() {
        return stagedHome;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Stops the embedded Elasticsearch node.
     *
     * <p>Idempotent: safe to call more than once.
     */
    @Override
    public void close() throws IOException {
        try {
            node.close();
            try {
                node.awaitClose(config.shutdownTimeout().toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for embedded Elasticsearch to close", e);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Error while closing embedded Elasticsearch node", e);
        } finally {
            resetProcessGlobalLoggingState();
        }
    }

    private static void resetProcessGlobalLoggingState() {
        resetSetOnce("org.elasticsearch.common.logging.NodeAndClusterIdStateListener", "nodeAndClusterId");
        resetSetOnce("org.elasticsearch.common.logging.NodeNamePatternConverter", "NODE_NAME");
    }

    private static Settings buildSettings(EmbeddedElasticServerConfig config) {
        Settings.Builder builder = Settings.builder()
                .put("path.home", config.stagedHomeDir().toAbsolutePath().toString())
                .put("path.data", config.dataDir().toAbsolutePath().toString())
                .put("path.logs", config.logsDir().toAbsolutePath().toString())
                .put("network.host", "127.0.0.1")
                .put("transport.port", "0")
                .put("cluster.name", config.clusterName())
                .put("node.name", config.nodeName());

        if (config.httpPort() > 0) {
            builder.put("http.port", Integer.toString(config.httpPort()));
        } else {
            builder.put("http.port", config.portRangeStart() + "-" + config.portRangeEnd());
        }
        config.settings().forEach(builder::put);
        return builder.build();
    }

    private static void awaitStartup(EmbeddedElasticServer server, java.time.Duration timeout)
            throws IOException, InterruptedException {
        if (server.client().waitForYellow(timeout) || server.ping()) {
            return;
        }
        throw new IOException("Timed out waiting for embedded Elasticsearch HTTP readiness at " + server.baseUri());
    }

    private static void closeQuietly(EmbeddedNode node, java.time.Duration shutdownTimeout) {
        if (node == null) {
            return;
        }
        try {
            node.close();
            node.awaitClose(shutdownTimeout.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Best effort cleanup during failed startup.
        }
    }

    private static Path inferWorkDir(Path dataDir, Path logsDir) {
        Path dataParent = dataDir.toAbsolutePath().getParent();
        Path logsParent = logsDir.toAbsolutePath().getParent();
        if (dataParent != null && dataParent.equals(logsParent)) {
            return dataParent;
        }
        return dataDir.toAbsolutePath().getParent() != null
                ? dataDir.toAbsolutePath().getParent()
                : dataDir.toAbsolutePath();
    }

    private static void resetSetOnce(String className, String fieldName) {
        try {
            Class<?> owner = Class.forName(className);
            Field setOnceField = owner.getDeclaredField(fieldName);
            setOnceField.setAccessible(true);
            Object setOnce = setOnceField.get(null);
            Field refField = setOnce.getClass().getDeclaredField("set");
            refField.setAccessible(true);
            @SuppressWarnings("unchecked")
            AtomicReference<Object> ref = (AtomicReference<Object>) refField.get(setOnce);
            ref.set(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to reset embedded Elasticsearch global state for "
                    + className + "." + fieldName, e);
        }
    }

}
