package io.github.wboult.esrunner.embedded;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.http.HttpServerTransport;
import org.elasticsearch.node.InternalSettingsPreparer;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Manages an Elasticsearch 8.1.x node running inside the current JVM process.
 *
 * <h2>Strategy: filesystem-modules</h2>
 * <p>{@code esHome} must point at the root of a fully-extracted Elasticsearch 8.1.x
 * distribution (the directory that contains {@code bin/}, {@code lib/}, {@code modules/},
 * {@code plugins/}).  The {@link org.elasticsearch.node.Node} public constructor is used,
 * which makes the node's {@code PluginsService} load every standard module
 * (analysis-common, transport-netty4, x-pack-core, …) straight from
 * {@code esHome/modules/} at startup — no classpath hacks required.
 *
 * <h2>Why this sidesteps the "unpublished modules" problem</h2>
 * <p>Elastic does not publish internal module jars (like {@code analysis-common}) to any
 * Maven repository.  By pointing {@code path.home} at the real extracted distribution and
 * calling {@code new Node(environment)}, we let ES load those jars the same way it does
 * in production — from the filesystem — while still running inside our JVM.
 *
 * <h2>Data / logs isolation</h2>
 * <p>The caller supplies {@code dataDir} and {@code logsDir}.  These should normally be
 * temporary directories so each test run starts with a clean state.
 *
 * <h2>Limitations (8.1.1)</h2>
 * <ul>
 *   <li>Security is disabled ({@code xpack.security.enabled=false}); TLS/auth not available.</li>
 *   <li>ML native code is disabled ({@code xpack.ml.enabled=false}).</li>
 *   <li>The ES node shares the JVM with the calling code — bootstrap checks are relaxed
 *       via {@code -Des.distribution.type=integ_test_zip} on the test JVM.</li>
 *   <li>Only tested with ES 8.1.x; later versions changed {@code PluginsService}
 *       significantly (see docs/embedded-jvm-server.md).</li>
 * </ul>
 */
public final class EmbeddedElasticServer implements Closeable {

    private final EmbeddedNode node;
    private final URI baseUri;

    private EmbeddedElasticServer(EmbeddedNode node, URI baseUri) {
        this.node = node;
        this.baseUri = baseUri;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Starts an embedded Elasticsearch node using the filesystem-modules approach.
     *
     * @param esHome   root of an extracted ES 8.1.x distribution
     * @param dataDir  where ES writes its data files (created if absent)
     * @param logsDir  where ES writes its log files (created if absent)
     * @return a started {@code EmbeddedElasticServer}; caller must {@link #close()} it
     * @throws Exception if the node fails to start
     */
    public static EmbeddedElasticServer start(Path esHome, Path dataDir, Path logsDir)
            throws Exception {
        return start(esHome, dataDir, logsDir, Collections.emptyMap());
    }

    /**
     * Starts an embedded Elasticsearch node with additional settings overrides.
     *
     * @param esHome         root of an extracted ES 8.1.x distribution
     * @param dataDir        where ES writes its data files (created if absent)
     * @param logsDir        where ES writes its log files (created if absent)
     * @param extraSettings  additional elasticsearch.yml settings; applied last
     * @return a started {@code EmbeddedElasticServer}; caller must {@link #close()} it
     * @throws Exception if the node fails to start
     */
    public static EmbeddedElasticServer start(
            Path esHome, Path dataDir, Path logsDir, Map<String, String> extraSettings)
            throws Exception {
        return start(esHome, dataDir, logsDir, sb -> extraSettings.forEach(sb::put));
    }

    /**
     * Starts an embedded Elasticsearch node, allowing full {@link Settings.Builder} customisation.
     *
     * @param esHome          root of an extracted ES 8.1.x distribution
     * @param dataDir         where ES writes its data files (created if absent)
     * @param logsDir         where ES writes its log files (created if absent)
     * @param settingsCustomiser callback that can add or override settings before node start
     * @return a started {@code EmbeddedElasticServer}; caller must {@link #close()} it
     * @throws Exception if the node fails to start
     */
    public static EmbeddedElasticServer start(
            Path esHome, Path dataDir, Path logsDir,
            Consumer<Settings.Builder> settingsCustomiser) throws Exception {

        Files.createDirectories(dataDir);
        Files.createDirectories(logsDir);

        Settings.Builder sb = Settings.builder()
                // ---- Paths ----
                // path.home must point at the extracted ES distribution so that
                // PluginsService can discover modules from esHome/modules/.
                .put("path.home", esHome.toAbsolutePath().toString())
                .put("path.data", dataDir.toAbsolutePath().toString())
                .put("path.logs", logsDir.toAbsolutePath().toString())

                // ---- Network ----
                // Use loopback only; port 0 lets the OS pick a free port.
                .put("network.host", "127.0.0.1")
                .put("http.port", "0")
                .put("transport.port", "0")

                // ---- Cluster ----
                .put("cluster.name", "embedded-test")
                .put("node.name", "embedded-node")
                .put("discovery.type", "single-node")

                // ---- Disable features that can't run embedded ----
                .put("xpack.security.enabled", "false")
                .put("xpack.ml.enabled", "false")
                .put("xpack.watcher.enabled", "false")
                .put("bootstrap.memory_lock", "false")

                // Disable system-call filter (seccomp) — may not be available inside test JVMs
                .put("bootstrap.system_call_filter", "false");

        settingsCustomiser.accept(sb);
        Settings settings = sb.build();

        // InternalSettingsPreparer is in the internal ES package but is the documented
        // way to bootstrap an embedded node.  With configPath=null it reads config from
        // path.home/config/ (i.e. the real config dir from the extracted distribution).
        Environment env = InternalSettingsPreparer.prepareEnvironment(
                settings, Collections.emptyMap(), null, () -> "embedded-node");

        // Use the filesystem-modules constructor: no classpath plugins, Node loads
        // everything from esHome/modules/ automatically.
        EmbeddedNode node = new EmbeddedNode(env);
        node.start();

        // Retrieve the actual HTTP port chosen by the OS.
        HttpServerTransport http = node.injector().getInstance(HttpServerTransport.class);
        int port = http.boundAddress().publishAddress().getPort();

        URI baseUri = URI.create("http://127.0.0.1:" + port);
        return new EmbeddedElasticServer(node, baseUri);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** HTTP base URI, e.g. {@code http://127.0.0.1:51234}. */
    public URI baseUri() {
        return baseUri;
    }

    /** The HTTP port chosen by the OS. */
    public int httpPort() {
        return baseUri.getPort();
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
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Error while closing embedded Elasticsearch node", e);
        }
    }
}
