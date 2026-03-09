package io.github.wboult.esrunner.embedded.opensearch.v3;

import io.github.wboult.esrunner.ElasticClient;
import io.github.wboult.esrunner.ElasticServerHandle;
import io.github.wboult.esrunner.ElasticServerType;
import io.github.wboult.esrunner.embedded.EmbeddedElasticServerConfig;
import io.github.wboult.esrunner.embedded.EmbeddedHome;
import org.opensearch.common.logging.LogConfigurator;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.http.HttpServerTransport;
import org.opensearch.node.InternalSettingsPreparer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public final class EmbeddedOpenSearchServer implements ElasticServerHandle {

    public static final String ANALYSIS_COMMON_PLUGIN = "org.opensearch.analysis.common.CommonAnalysisModulePlugin";
    public static final String TRANSPORT_NETTY4_MODULE = "transport-netty4";
    private static final Set<String> DEFAULT_CLASSPATH_PLUGINS = Set.of(ANALYSIS_COMMON_PLUGIN);
    private static final Set<String> DEFAULT_INCLUDED_MODULES = Set.of(TRANSPORT_NETTY4_MODULE);
    private static final AtomicBoolean LOGGING_BOOTSTRAPPED = new AtomicBoolean();

    private final EmbeddedElasticServerConfig config;
    private final EmbeddedNode node;
    private final URI baseUri;
    private final ElasticClient client;
    private final Path stagedHome;
    private final Path workDir;
    private final Path dataDir;
    private final Path logsDir;

    private EmbeddedOpenSearchServer(EmbeddedElasticServerConfig config,
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

    public static EmbeddedOpenSearchServer start(Path openSearchHome) throws Exception {
        return start(defaultConfig(openSearchHome));
    }

    public static EmbeddedOpenSearchServer start(EmbeddedElasticServerConfig config) throws Exception {
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
            node = new EmbeddedNode(env, resolved.classpathPlugins());
            node.start();

            HttpServerTransport http = node.injector().getInstance(HttpServerTransport.class);
            int port = http.boundAddress().publishAddress().getPort();
            URI baseUri = URI.create("http://127.0.0.1:" + port + "/");

            EmbeddedOpenSearchServer server = new EmbeddedOpenSearchServer(
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

    public static EmbeddedOpenSearchServer start(Consumer<EmbeddedElasticServerConfig.Builder> consumer) throws Exception {
        EmbeddedElasticServerConfig.Builder builder = defaultBuilder();
        consumer.accept(builder);
        return start(builder.build());
    }

    public static void withServer(EmbeddedElasticServerConfig config, Consumer<EmbeddedOpenSearchServer> action) {
        try (EmbeddedOpenSearchServer server = start(config)) {
            action.accept(server);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start embedded OpenSearch 3.x", e);
        }
    }

    public static <T> T withServer(EmbeddedElasticServerConfig config, Function<EmbeddedOpenSearchServer, T> action) {
        try (EmbeddedOpenSearchServer server = start(config)) {
            return action.apply(server);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start embedded OpenSearch 3.x", e);
        }
    }

    public static void withServer(Consumer<EmbeddedElasticServerConfig.Builder> consumer,
                                  Consumer<EmbeddedOpenSearchServer> action) {
        EmbeddedElasticServerConfig.Builder builder = defaultBuilder();
        consumer.accept(builder);
        withServer(builder.build(), action);
    }

    public static <T> T withServer(Consumer<EmbeddedElasticServerConfig.Builder> consumer,
                                   Function<EmbeddedOpenSearchServer, T> action) {
        EmbeddedElasticServerConfig.Builder builder = defaultBuilder();
        consumer.accept(builder);
        return withServer(builder.build(), action);
    }

    public static EmbeddedOpenSearchServer start(Path openSearchHome,
                                                 Path dataDir,
                                                 Path logsDir,
                                                 Map<String, String> extraSettings) throws Exception {
        Path workDir = inferWorkDir(dataDir, logsDir);
        EmbeddedElasticServerConfig.Builder builder = defaultBuilder()
                .esHome(openSearchHome)
                .workDir(workDir);
        extraSettings.forEach(builder::setting);
        return start(builder.build());
    }

    public static EmbeddedElasticServerConfig defaultConfig(Path openSearchHome) {
        return defaultBuilder()
                .esHome(openSearchHome)
                .build();
    }

    private static EmbeddedElasticServerConfig.Builder defaultBuilder() {
        return EmbeddedElasticServerConfig.builder()
                .settings(defaultSettings())
                .includedModules(DEFAULT_INCLUDED_MODULES)
                .classpathPlugins(DEFAULT_CLASSPATH_PLUGINS);
    }

    private static Map<String, String> defaultSettings() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("discovery.type", "single-node");
        defaults.put("bootstrap.memory_lock", "false");
        return defaults;
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
                throw new IOException("Interrupted while waiting for embedded OpenSearch to close", e);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Error while closing embedded OpenSearch node", e);
        } finally {
            resetProcessGlobalLoggingState();
        }
    }

    private static void bootstrapLogging(Settings settings) {
        if (LOGGING_BOOTSTRAPPED.compareAndSet(false, true)) {
            LogConfigurator.registerErrorListener();
            LogConfigurator.configureWithoutConfig(settings);
        }
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

    private static void awaitStartup(EmbeddedOpenSearchServer server, Duration timeout)
            throws IOException, InterruptedException {
        if (server.client().waitForYellow(timeout) || server.ping()) {
            return;
        }
        throw new IOException("Timed out waiting for embedded OpenSearch HTTP readiness at " + server.baseUri());
    }

    private static void closeQuietly(EmbeddedNode node, Duration shutdownTimeout) {
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

    private static void resetProcessGlobalLoggingState() {
        resetSetOnce("org.opensearch.common.logging.NodeAndClusterIdConverter", "nodeAndClusterId");
        resetSetOnce("org.opensearch.common.logging.NodeNamePatternConverter", "NODE_NAME");
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
            throw new IllegalStateException("Failed to reset embedded OpenSearch global state for "
                    + className + "." + fieldName, e);
        }
    }
}
