package io.github.wboult.esrunner;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElasticRunnerFailureIntegrationTest {

    @Test
    void startupFailureCleansPidAndStateFilesAndWritesDiagnostics() throws Exception {
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

        ElasticRunnerException ex = assertThrows(ElasticRunnerException.class, () -> ElasticRunner.start(config));

        Path versionDir = workDir.resolve(version);
        assertFalse(Files.exists(versionDir.resolve("es.pid")));
        assertFalse(Files.exists(versionDir.resolve("state.json")));

        Path diagnosticsFile = versionDir.resolve("logs").resolve("startup-diagnostics.txt").toAbsolutePath();
        assertEquals(diagnosticsFile, ex.diagnosticsFile().orElseThrow());
        assertTrue(Files.exists(diagnosticsFile));
        assertTrue(ex.getMessage().contains("Resolved archive:"));
        assertTrue(ex.getMessage().contains("Startup diagnostics:"));

        String diagnostics = Files.readString(diagnosticsFile);
        assertTrue(diagnostics.contains("Failure summary"));
        assertTrue(diagnostics.contains("Resolved distro"));
        assertTrue(diagnostics.contains("Captured config"));
        assertTrue(diagnostics.contains("Recent log output"));
        assertTrue(diagnostics.contains("Common remediation hints"));
        assertTrue(diagnostics.contains("runner.invalid.setting"));
        assertTrue(diagnostics.contains("Resolved archive".replace("archive", "distro")) || diagnostics.contains("archive:"));
    }
}
