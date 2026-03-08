package com.elastic.runner.gradle;

import com.elastic.runner.ElasticRunner;
import com.elastic.runner.ElasticRunnerConfig;
import com.elastic.runner.ElasticServer;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import java.nio.file.Path;
import java.time.Duration;

public abstract class ElasticClusterService implements BuildService<ElasticClusterService.Params>, AutoCloseable {
    public interface Params extends BuildServiceParameters {
        Property<String> getName();
        Property<String> getDistroZip();
        Property<String> getVersion();
        Property<String> getDistrosDir();
        Property<Boolean> getDownload();
        Property<String> getDownloadBaseUrl();
        Property<String> getWorkDir();
        Property<String> getClusterName();
        Property<Integer> getHttpPort();
        Property<Integer> getPortRangeStart();
        Property<Integer> getPortRangeEnd();
        Property<String> getHeap();
        Property<Long> getStartupTimeoutMillis();
        Property<Long> getShutdownTimeoutMillis();
        MapProperty<String, String> getSettings();
        ListProperty<String> getPlugins();
        Property<Boolean> getQuiet();
    }

    private final Object lock = new Object();
    private ElasticServer server;
    private ElasticClusterMetadata metadata;

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
