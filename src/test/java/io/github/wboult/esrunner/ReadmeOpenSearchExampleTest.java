package io.github.wboult.esrunner;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadmeOpenSearchExampleTest {

    @Test
    void readmeOpenSearchExampleWorks() throws Exception {
        String version = System.getenv().getOrDefault("OPENSEARCH_VERSION", "3.5.0");
        Path workDir = Files.createTempDirectory("es-runner-readme-opensearch-");
        ElasticRunnerConfig config = IntegrationTestSupport.configFromExample(
                DistroFamily.OPENSEARCH,
                version,
                workDir,
                "readme-opensearch",
                Duration.ofSeconds(120),
                true,
                builder -> builder.setting("discovery.type", "single-node")
        );

        try (ElasticServer server = ElasticRunner.start(config)) {
            ElasticClient client = server.client();
            assertTrue(!client.clusterHealth().status().isEmpty());
            assertTrue(client.version().startsWith(version.substring(0, version.indexOf('.')) + "."));
            SearchWorkflowAssertions.assertRealisticOrderWorkflow(server, "readme-opensearch");
        }
    }
}
