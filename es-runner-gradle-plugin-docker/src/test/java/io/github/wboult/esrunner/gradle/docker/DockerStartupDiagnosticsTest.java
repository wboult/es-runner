package io.github.wboult.esrunner.gradle.docker;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerStartupDiagnosticsTest {
    @Test
    void renderFailureIncludesUsefulDockerAndContainerContext() {
        DockerStartupDiagnostics.DockerContainerSnapshot snapshot =
                new DockerStartupDiagnostics.DockerContainerSnapshot(
                        "docker.elastic.co/elasticsearch/elasticsearch:9.3.1",
                        "abc123",
                        "/shared-es",
                        "127.0.0.1",
                        49213,
                        49214,
                        "line one\nline two\ncluster boot failed"
                );

        String diagnostics = DockerStartupDiagnostics.renderFailure(
                "integration",
                "elasticsearch",
                "shared-es-docker",
                "docker.elastic.co/elasticsearch/elasticsearch:9.3.1",
                Duration.ofMinutes(4),
                Map.of(
                        "xpack.security.enabled", "false",
                        "ELASTIC_PASSWORD", "super-secret"
                ),
                snapshot,
                new IllegalStateException("Cluster health probe returned 503: service unavailable")
        );

        assertTrue(diagnostics.contains("Failed to start shared Docker cluster 'integration'."));
        assertTrue(diagnostics.contains("- distribution: elasticsearch"));
        assertTrue(diagnostics.contains("- configured cluster name: shared-es-docker"));
        assertTrue(diagnostics.contains("- image: docker.elastic.co/elasticsearch/elasticsearch:9.3.1"));
        assertTrue(diagnostics.contains("- container id: abc123"));
        assertTrue(diagnostics.contains("- container name: /shared-es"));
        assertTrue(diagnostics.contains("- host: 127.0.0.1"));
        assertTrue(diagnostics.contains("- mapped http port: 49213"));
        assertTrue(diagnostics.contains("- mapped transport port: 49214"));
        assertTrue(diagnostics.contains("- ELASTIC_PASSWORD: <redacted>"));
        assertTrue(diagnostics.contains("line two"));
        assertTrue(diagnostics.contains("The container started but Elasticsearch never reached the expected health probe."));
    }

    @Test
    void renderFailureExplainsDockerConnectivityFailuresWithoutContainerState() {
        String diagnostics = DockerStartupDiagnostics.renderFailure(
                "integration",
                "elasticsearch",
                "shared-es-docker",
                "docker.elastic.co/elasticsearch/elasticsearch:9.3.1",
                Duration.ofMinutes(3),
                Map.of("xpack.security.enabled", "false"),
                new DockerStartupDiagnostics.DockerContainerSnapshot(
                        "docker.elastic.co/elasticsearch/elasticsearch:9.3.1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new IllegalStateException("Could not find a valid Docker environment. Permission denied")
        );

        assertTrue(diagnostics.contains("- container id: <unavailable>"));
        assertTrue(diagnostics.contains("Container logs unavailable."));
        assertTrue(diagnostics.contains("Verify the Docker daemon is reachable from this Gradle process"));
    }

    @Test
    void renderFailureIncludesOpenSearchDistributionContext() {
        String diagnostics = DockerStartupDiagnostics.renderFailure(
                "integration",
                "opensearch",
                "shared-os-docker",
                "opensearchproject/opensearch:3.5.0",
                Duration.ofMinutes(3),
                Map.of(
                        "DISABLE_SECURITY_PLUGIN", "true",
                        "OPENSEARCH_INITIAL_ADMIN_PASSWORD", "super-secret"
                ),
                new DockerStartupDiagnostics.DockerContainerSnapshot(
                        "opensearchproject/opensearch:3.5.0",
                        "os123",
                        "/shared-os",
                        "127.0.0.1",
                        49213,
                        49214,
                        null
                ),
                new IllegalStateException("manifest for opensearchproject/opensearch:3.5.0 not found")
        );

        assertTrue(diagnostics.contains("- distribution: opensearch"));
        assertTrue(diagnostics.contains("- configured cluster name: shared-os-docker"));
        assertTrue(diagnostics.contains("- OPENSEARCH_INITIAL_ADMIN_PASSWORD: <redacted>"));
        assertTrue(diagnostics.contains("Verify the configured image exists and that the registry is reachable"));
    }
}
