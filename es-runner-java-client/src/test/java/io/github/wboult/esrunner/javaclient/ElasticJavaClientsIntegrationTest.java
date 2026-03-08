package io.github.wboult.esrunner.javaclient;

import io.github.wboult.esrunner.ElasticRunner;
import io.github.wboult.esrunner.ElasticRunnerConfig;
import io.github.wboult.esrunner.ElasticServer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticJavaClientsIntegrationTest {
    @Test
    void officialClientTalksToLiveServer() throws Exception {
        String version = System.getenv().getOrDefault("ES_VERSION", "9.3.1");
        String major = version.split("\\.")[0];

        Path workDir = Files.createTempDirectory("es-runner-java-client-it-");
        ElasticRunnerConfig config = JavaClientIntegrationTestSupport.config(
                version,
                workDir,
                "java-client-it",
                "256m",
                java.time.Duration.ofSeconds(120),
                true
        );

        try (ElasticServer server = ElasticRunner.start(config);
             ManagedElasticsearchClient managed = ElasticJavaClients.create(server)) {
            var info = managed.client().info();

            assertEquals(server.baseUri(), managed.baseUri());
            assertEquals(server.clusterName(), info.clusterName());
            assertTrue(info.version().number().startsWith(major + "."));
            assertTrue(server.ping());
        }
    }
}
