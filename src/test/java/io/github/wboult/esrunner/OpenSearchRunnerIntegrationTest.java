package io.github.wboult.esrunner;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSearchRunnerIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"2.19.4", "3.5.0"})
    void startsAndRespondsToHealthChecks(String version) throws Exception {
        Path workDir = Files.createTempDirectory("os-runner-it-");
        ElasticRunnerConfig config = IntegrationTestSupport.config(
                DistroFamily.OPENSEARCH,
                version,
                workDir,
                "os-it",
                "512m",
                Duration.ofSeconds(180),
                true
        );

        String major = version.split("\\.")[0];
        try (ElasticServer server = ElasticRunner.start(config)) {
            assertTrue(server.ping());
            assertTrue(!server.clusterHealth().status().isEmpty());
            assertTrue(server.clusterName().contains("os-it"));
            assertTrue(server.version().startsWith(major + "."));

            server.createIndex("docs");
            server.indexDocument("docs", "1", "{\"title\":\"hello world\"}");
            server.refresh("docs");
            assertTrue(server.countValue("docs") >= 1);
            assertTrue(server.search("docs", "{\"query\":{\"match\":{\"title\":\"hello\"}}}").contains("\"hits\""));
        }
    }
}
