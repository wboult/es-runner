package io.github.wboult.esrunner.gradle.docker;

import io.github.wboult.esrunner.gradle.ElasticClusterMetadata;
import io.github.wboult.esrunner.gradle.SharedClusterBackend;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Shared Gradle build service that lazily starts one Docker-backed
 * Elasticsearch cluster and reuses it for all bound test tasks in the build.
 */
public abstract class DockerClusterService
        implements BuildService<DockerClusterService.Params>, SharedClusterBackend {
    /**
     * Build service parameters mirroring the Docker cluster DSL.
     */
    public interface Params extends BuildServiceParameters {
        /** @return cluster definition name */
        Property<String> getName();
        /** @return full Docker image reference */
        Property<String> getImage();
        /** @return configured cluster name */
        Property<String> getClusterName();
        /** @return startup timeout in milliseconds */
        Property<Long> getStartupTimeoutMillis();
        /** @return container environment variables */
        MapProperty<String, String> getEnvVars();
    }

    private static final Logger LOGGER = Logging.getLogger(DockerClusterService.class);
    private static final int HTTP_PORT = 9200;
    private static final int TRANSPORT_PORT = 9300;

    private final Object lock = new Object();
    private GenericContainer<?> container;
    private ElasticClusterMetadata metadata;

    @Override
    public ElasticClusterMetadata metadata() {
        synchronized (lock) {
            if (container == null || !container.isRunning()) {
                startContainer();
            }
            return metadata;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (container != null) {
                container.stop();
                container = null;
                metadata = null;
            }
        }
    }

    private void startContainer() {
        Params params = getParameters();
        String image = params.getImage().get();
        String clusterName = params.getClusterName().get();
        Duration timeout = Duration.ofMillis(params.getStartupTimeoutMillis().get());

        GenericContainer<?> started = new GenericContainer<>(DockerImageName.parse(image))
                .withExposedPorts(HTTP_PORT, TRANSPORT_PORT)
                .waitingFor(Wait.forLogMessage(".*(\"message\":\\s?\"started[\\s?|\"].*|] started\\n$)", 1))
                .withStartupTimeout(timeout);
        for (Map.Entry<String, String> entry : params.getEnvVars().get().entrySet()) {
            started.withEnv(entry.getKey(), entry.getValue());
        }
        started.withEnv("cluster.name", clusterName);
        started.withEnv("xpack.security.enabled", "false");

        try {
            started.start();
            waitForReady(started, timeout);
            metadata = new ElasticClusterMetadata(
                    "http://" + started.getHost() + ":" + started.getMappedPort(HTTP_PORT),
                    started.getMappedPort(HTTP_PORT),
                    clusterName
            );
            container = started;
            LOGGER.lifecycle("Shared Docker cluster '{}' started from image {} with container id {} on {}:{}.",
                    params.getName().get(),
                    image,
                    started.getContainerId(),
                    started.getHost(),
                    started.getMappedPort(HTTP_PORT));
        } catch (Exception e) {
            DockerStartupDiagnostics.DockerContainerSnapshot snapshot =
                    DockerStartupDiagnostics.snapshot(image, started);
            String diagnostics = DockerStartupDiagnostics.renderFailure(
                    params.getName().get(),
                    clusterName,
                    image,
                    timeout,
                    params.getEnvVars().get(),
                    snapshot,
                    asRuntimeException(e)
            );
            try {
                started.stop();
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
            }
            throw new IllegalStateException(diagnostics, e);
        }
    }

    private void waitForReady(GenericContainer<?> started, Duration timeout)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + started.getHost() + ":" + started.getMappedPort(HTTP_PORT)
                        + "/_cluster/health?wait_for_status=yellow&timeout=" + timeout.toSeconds() + "s"))
                .timeout(timeout)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("Cluster health probe returned " + response.statusCode()
                    + ": " + response.body());
        }
    }

    private RuntimeException asRuntimeException(Exception failure) {
        return failure instanceof RuntimeException runtime ? runtime : new IllegalStateException(failure.getMessage(), failure);
    }
}
