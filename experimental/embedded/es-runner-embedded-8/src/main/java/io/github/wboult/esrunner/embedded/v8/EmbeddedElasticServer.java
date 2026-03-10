package io.github.wboult.esrunner.embedded.v8;

import io.github.wboult.esrunner.ElasticClient;
import io.github.wboult.esrunner.ElasticServerHandle;
import io.github.wboult.esrunner.ElasticServerType;
import io.github.wboult.esrunner.embedded.EmbeddedElasticServerConfig;
import io.github.wboult.esrunner.embedded.EmbeddedHome;
import io.github.wboult.esrunner.embedded.EmbeddedModuleProfile;
import org.elasticsearch.common.logging.LogConfigurator;
import org.elasticsearch.common.settings.Settings;
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
 * Manages an Elasticsearch 8.19.x node running inside the current JVM process.
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

    public static EmbeddedElasticServer start(Path esHome) throws Exception {
        return start(defaultConfig(esHome));
    }

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

    public static EmbeddedElasticServer start(Consumer<EmbeddedElasticServerConfig.Builder> consumer) throws Exception {
        EmbeddedElasticServerConfig.Builder builder = EmbeddedElasticServerConfig.builder()
                .includedModules(PROFILE.includedModules());
        consumer.accept(builder);
        return start(builder.build());
    }

    public static void withServer(EmbeddedElasticServerConfig config, Consumer<EmbeddedElasticServer> action) {
        try (EmbeddedElasticServer server = start(config)) {
            action.accept(server);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start embedded Elasticsearch", e);
        }
    }

    public static <T> T withServer(EmbeddedElasticServerConfig config, Function<EmbeddedElasticServer, T> action) {
        try (EmbeddedElasticServer server = start(config)) {
            return action.apply(server);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start embedded Elasticsearch", e);
        }
    }

    public static void withServer(Consumer<EmbeddedElasticServerConfig.Builder> consumer,
                                  Consumer<EmbeddedElasticServer> action) {
        EmbeddedElasticServerConfig.Builder builder = EmbeddedElasticServerConfig.builder()
                .includedModules(PROFILE.includedModules());
        consumer.accept(builder);
        withServer(builder.build(), action);
    }

    public static <T> T withServer(Consumer<EmbeddedElasticServerConfig.Builder> consumer,
                                   Function<EmbeddedElasticServer, T> action) {
        EmbeddedElasticServerConfig.Builder builder = EmbeddedElasticServerConfig.builder()
                .includedModules(PROFILE.includedModules());
        consumer.accept(builder);
        return withServer(builder.build(), action);
    }

    public static EmbeddedElasticServer start(Path esHome, Path dataDir, Path logsDir) throws Exception {
        Path workDir = inferWorkDir(dataDir, logsDir);
        return start(EmbeddedElasticServerConfig.builder()
                .esHome(esHome)
                .workDir(workDir)
                .includedModules(PROFILE.includedModules())
                .build());
    }

    public static EmbeddedElasticServer start(Path esHome,
                                              Path dataDir,
                                              Path logsDir,
                                              Map<String, String> extraSettings) throws Exception {
        return start(esHome, dataDir, logsDir, builder -> extraSettings.forEach(builder::put));
    }

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

    public static EmbeddedModuleProfile profile() {
        return PROFILE;
    }

    public static EmbeddedElasticServerConfig defaultConfig(Path esHome) {
        return EmbeddedElasticServerConfig.builder()
                .esHome(esHome)
                .includedModules(PROFILE.includedModules())
                .build();
    }

    private static void bootstrapLogging(Settings settings) {
        if (LOGGING_BOOTSTRAPPED.compareAndSet(false, true)) {
            LogConfigurator.registerErrorListener();
            LogConfigurator.configureWithoutConfig(settings);
        }
    }

    public EmbeddedElasticServerConfig config() {
        return config;
    }

    @Override
    public ElasticServerType type() {
        return ElasticServerType.EMBEDDED;
    }

    @Override
    public URI baseUri() {
        return baseUri;
    }

    @Override
    public int httpPort() {
        return baseUri.getPort();
    }

    @Override
    public ElasticClient client() {
        return client;
    }

    public Path workDir() {
        return workDir;
    }

    public Path dataDir() {
        return dataDir;
    }

    public Path logsDir() {
        return logsDir;
    }

    public Path stagedHome() {
        return stagedHome;
    }

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
