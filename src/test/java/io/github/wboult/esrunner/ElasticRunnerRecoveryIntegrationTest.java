package io.github.wboult.esrunner;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticRunnerRecoveryIntegrationTest {

    @Test
    void successfulStartReplacesStalePidAndStateFilesInExistingWorkDir() throws Exception {
        String version = System.getenv().getOrDefault("ES_VERSION", "9.3.1");
        Path workDir = Files.createTempDirectory("es-runner-recovery-it-");
        Path versionDir = workDir.resolve(version);
        Files.createDirectories(versionDir.resolve("logs"));
        Files.writeString(versionDir.resolve("es.pid"), "999999");
        Files.writeString(versionDir.resolve("state.json"), "{\"pid\":1,\"httpPort\":9200,\"clusterName\":\"stale\",\"version\":\""
                + version + "\",\"startTime\":\"2026-01-01T00:00:00Z\",\"workDir\":\".\",\"baseUri\":\"http://localhost:9200/\"}");

        ElasticRunnerConfig config = IntegrationTestSupport.config(
                version,
                workDir,
                "recovery-it",
                "256m",
                Duration.ofSeconds(60),
                true
        );

        try (ElasticServer server = ElasticRunner.start(config)) {
            assertTrue(server.ping());

            RunnerState state = RunnerState.read(versionDir.resolve("state.json"));
            assertEquals(server.pid(), state.pid());
            assertEquals(server.httpPort(), state.httpPort());
            assertEquals(server.baseUri().toString(), state.baseUri());
            assertEquals("recovery-it", state.clusterName());
        }

        assertFalse(Files.exists(versionDir.resolve("es.pid")));
        assertFalse(Files.exists(versionDir.resolve("state.json")));
    }
}
