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
                server = ElasticRunner.start(toConfig());
                metadata = new ElasticClusterMetadata(
                        server.baseUri().toString(),
                        server.httpPort(),
                        server.config().clusterName()
                );
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
        ElasticRunnerConfig config = ElasticRunnerConfig.defaults()
                .withDistrosDir(Path.of(params.getDistrosDir().get()))
                .withDownload(params.getDownload().get())
                .withDownloadBaseUrl(params.getDownloadBaseUrl().get())
                .withWorkDir(Path.of(params.getWorkDir().get()))
                .withClusterName(params.getClusterName().get())
                .withHttpPort(params.getHttpPort().get())
                .withPortRange(params.getPortRangeStart().get(), params.getPortRangeEnd().get())
                .withHeap(params.getHeap().get())
                .withStartupTimeout(Duration.ofMillis(params.getStartupTimeoutMillis().get()))
                .withShutdownTimeout(Duration.ofMillis(params.getShutdownTimeoutMillis().get()))
                .withSettings(params.getSettings().get())
                .withPlugins(params.getPlugins().get())
                .withQuiet(params.getQuiet().get());
        if (params.getDistroZip().isPresent() && !params.getDistroZip().get().isBlank()) {
            config = config.withDistroZip(Path.of(params.getDistroZip().get()));
        }
        if (params.getVersion().isPresent() && !params.getVersion().get().isBlank()) {
            config = config.withVersion(params.getVersion().get());
        }
        return config;
    }
}
