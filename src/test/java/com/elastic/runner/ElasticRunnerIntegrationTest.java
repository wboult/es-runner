package com.elastic.runner;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticRunnerIntegrationTest {

    @Test
    void startsAndRespondsToHealthChecks() throws Exception {
        String zipPath = System.getenv("ES_DISTRO_ZIP");
        String version = System.getenv().getOrDefault("ES_VERSION", "9.2.4");

        Path workDir = Files.createTempDirectory("es-runner-it-");
        ElasticRunnerConfig config = IntegrationTestSupport.config(
                version,
                workDir,
                "it-cluster",
                "256m",
                java.time.Duration.ofSeconds(120),
                true
        );

        try (ElasticServer server = ElasticRunner.start(config)) {
            assertTrue(server.ping());
            assertTrue(server.clusterHealth().contains("\"status\""));
            assertTrue(server.clusterName().contains("it-cluster"));
            assertTrue(server.version().startsWith("9."));
        }
    }
}
