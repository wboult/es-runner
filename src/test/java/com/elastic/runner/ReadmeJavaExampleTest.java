package com.elastic.runner;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadmeJavaExampleTest {

    @Test
    void readmeJavaExampleWorks() throws Exception {
        String version = System.getenv().getOrDefault("ES_VERSION", "9.3.1");
        Path workDir = Files.createTempDirectory("es-runner-readme-java-");
        ElasticRunnerConfig config = IntegrationTestSupport.configFromExample(
                version,
                workDir,
                "readme-java",
                Duration.ofSeconds(120),
                true,
                builder -> builder.setting("discovery.type", "single-node")
        );

        try (ElasticServer server = ElasticRunner.start(config)) {
            ElasticClient client = server.client();
            assertTrue(!client.clusterHealth().status().isEmpty());
            assertTrue(client.version().startsWith(version.substring(0, version.indexOf('.')) + "."));
        }
    }
}
