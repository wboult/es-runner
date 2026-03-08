package io.github.wboult.esrunner.gradle;

import io.github.wboult.esrunner.ElasticRunner;
import io.github.wboult.esrunner.ElasticRunnerConfig;
import io.github.wboult.esrunner.ElasticServer;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Shared Gradle build service that lazily starts one Elasticsearch cluster and
 * reuses it for all bound test tasks in the build.
 */
public abstract class ElasticClusterService implements BuildService<ElasticClusterService.Params>, AutoCloseable {
    /**
     * Build service parameters mirroring the cluster DSL and
     * {@link ElasticRunnerConfig}.
     */
    public interface Params extends BuildServiceParameters {
        /** @return cluster definition name */
        Property<String> getName();
        /** @return optional local ZIP path */
        Property<String> getDistroZip();
        /** @return optional Elasticsearch version */
        Property<String> getVersion();
        /** @return distro cache directory */
        Property<String> getDistrosDir();
        /** @return whether downloads should refresh the cache */
        Property<Boolean> getDownload();
        /** @return download base URL or mirror prefix */
        Property<String> getDownloadBaseUrl();
        /** @return cluster working directory */
        Property<String> getWorkDir();
        /** @return configured cluster name */
        Property<String> getClusterName();
        /** @return fixed HTTP port, or zero for ranged allocation */
        Property<Integer> getHttpPort();
        /** @return start of the HTTP port range */
        Property<Integer> getPortRangeStart();
        /** @return end of the HTTP port range */
        Property<Integer> getPortRangeEnd();
        /** @return heap setting used for Xms and Xmx */
        Property<String> getHeap();
        /** @return startup timeout in milliseconds */
        Property<Long> getStartupTimeoutMillis();
        /** @return shutdown timeout in milliseconds */
        Property<Long> getShutdownTimeoutMillis();
        /** @return extra Elasticsearch settings */
        MapProperty<String, String> getSettings();
        /** @return plugin install list */
        ListProperty<String> getPlugins();
        /** @return quiet output flag */
        Property<Boolean> getQuiet();
    }

    private final Object lock = new Object();
    private ElasticServer server;
    private ElasticClusterMetadata metadata;

    /**
     * Starts the cluster on first use and returns the stable connection
     * metadata.
     *
     * @return cluster metadata
     */
    public ElasticClusterMetadata metadata() {
        synchronized (lock) {
            if (server == null || !server.isRunning()) {
                if (server != null) {
                    try {
                        server.close();
                    } catch (Exception ignored) {
                    }
                }
                ElasticServer started = ElasticRunner.start(toConfig());
                try {
                    metadata = new ElasticClusterMetadata(
                            started.baseUri().toString(),
                            started.httpPort(),
                            started.config().clusterName()
                    );
                } catch (Exception e) {
                    try {
                        started.close();
                    } catch (Exception suppressed) {
                        e.addSuppressed(suppressed);
                    }
                    throw e;
                }
                server = started;
            }
            return metadata;
        }
    }

    /**
     * Stops the shared cluster when Gradle disposes of the build service.
     */
    @Override
    public void close() {
        synchronized (lock) {
            if (server != null) {
                server.close();
                server = null;
                metadata = null;
            }
        }
    }

    private ElasticRunnerConfig toConfig() {
        Params params = getParameters();
        ElasticRunnerConfig.Builder builder = ElasticRunnerConfig.defaults().toBuilder()
                .distrosDir(Path.of(params.getDistrosDir().get()))
                .download(params.getDownload().get())
                .downloadBaseUrl(params.getDownloadBaseUrl().get())
                .workDir(Path.of(params.getWorkDir().get()))
                .clusterName(params.getClusterName().get())
                .httpPort(params.getHttpPort().get())
                .portRange(params.getPortRangeStart().get(), params.getPortRangeEnd().get())
                .heap(params.getHeap().get())
                .startupTimeout(Duration.ofMillis(params.getStartupTimeoutMillis().get()))
                .shutdownTimeout(Duration.ofMillis(params.getShutdownTimeoutMillis().get()))
                .settings(params.getSettings().get())
                .plugins(params.getPlugins().get())
                .quiet(params.getQuiet().get());
        if (params.getDistroZip().isPresent() && !params.getDistroZip().get().isBlank()) {
            builder.distroZip(Path.of(params.getDistroZip().get()));
        }
        if (params.getVersion().isPresent() && !params.getVersion().get().isBlank()) {
            builder.version(params.getVersion().get());
        }
        return builder.build();
    }
}
