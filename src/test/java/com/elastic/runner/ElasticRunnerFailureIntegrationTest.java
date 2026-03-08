package com.elastic.runner;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElasticRunnerFailureIntegrationTest {

    @Test
    void startupFailureCleansPidAndStateFiles() throws Exception {
        String version = System.getenv().getOrDefault("ES_VERSION", "9.3.1");
        Path workDir = Files.createTempDirectory("es-runner-failure-it-");
        ElasticRunnerConfig config = IntegrationTestSupport.config(
                version,
                workDir,
                "failure-it",
                "256m",
                Duration.ofSeconds(60),
                true
        ).toBuilder()
                .setting("runner.invalid.setting", "true")
                .build();

        assertThrows(ElasticRunnerException.class, () -> ElasticRunner.start(config));

        Path versionDir = workDir.resolve(version);
        assertFalse(Files.exists(versionDir.resolve("es.pid")));
        assertFalse(Files.exists(versionDir.resolve("state.json")));
    }
}
