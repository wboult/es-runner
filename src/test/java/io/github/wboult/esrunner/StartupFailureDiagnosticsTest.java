package io.github.wboult.esrunner;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupFailureDiagnosticsTest {

    @Test
    void diagnosticsRedactSensitiveSettingsAndExposeDiagnosticsPath() throws Exception {
        Path versionDir = Files.createTempDirectory("es-runner-startup-diagnostics-");
        Path logFile = versionDir.resolve("logs").resolve("runner.log");
        Files.createDirectories(logFile.getParent());
        Files.writeString(logFile, "[INFO] invalid config");

        ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
                .version("9.3.1")
                .download(true)
                .downloadBaseUrl("https://mirror.example.com/elasticsearch/")
                .workDir(versionDir.getParent())
                .setting("xpack.security.http.ssl.keystore.secure_password", "secret")
                .setting("cluster.routing.allocation.enable", "all"));
        ResolvedDistro resolvedDistro = new ResolvedDistro(
                DistroFamily.ELASTICSEARCH,
                "9.3.1",
                versionDir.resolve("elasticsearch-9.3.1.zip"),
                "version-download",
                "https://mirror.example.com/elasticsearch/",
                URI.create("https://mirror.example.com/elasticsearch/elasticsearch-9.3.1-windows-x86_64.zip")
        );

        ElasticRunnerException ex = StartupFailureDiagnostics.wrap(
                new StartupTimeoutException("Timed out waiting for Elasticsearch."),
                config,
                DistroFamily.ELASTICSEARCH,
                resolvedDistro,
                versionDir,
                logFile,
                null
        );

        Path diagnosticsFile = ex.diagnosticsFile().orElseThrow();
        assertTrue(ex instanceof StartupTimeoutException);
        assertEquals(ElasticRunnerException.Kind.STARTUP_TIMEOUT, ex.kind());
        assertEquals(versionDir.resolve("logs").resolve("startup-diagnostics.txt").toAbsolutePath(), diagnosticsFile);
        assertTrue(Files.exists(diagnosticsFile));

        String diagnostics = Files.readString(diagnosticsFile);
        assertTrue(diagnostics.contains("<redacted>"));
        assertTrue(diagnostics.contains("cluster.routing.allocation.enable=all"));
        assertTrue(diagnostics.contains("download uri: https://mirror.example.com/elasticsearch/elasticsearch-9.3.1-windows-x86_64.zip"));
        assertTrue(ex.getMessage().contains("Resolved download URI:"));
        assertTrue(ex.getMessage().contains("Hints:"));
    }

    @Test
    void diagnosticsIncludePluginHintWhenPluginsAreConfigured() throws Exception {
        Path versionDir = Files.createTempDirectory("es-runner-startup-diagnostics-");
        ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
                .version("9.3.1")
                .plugin("analysis-icu")
                .startupTimeout(Duration.ofSeconds(10)));
        ResolvedDistro resolvedDistro = new ResolvedDistro(
                DistroFamily.ELASTICSEARCH,
                "9.3.1",
                versionDir.resolve("elasticsearch-9.3.1.zip"),
                "version-download",
                "https://artifacts.elastic.co/downloads/elasticsearch/",
                URI.create("https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-9.3.1-windows-x86_64.zip")
        );

        ElasticRunnerException ex = StartupFailureDiagnostics.wrap(
                new PluginInstallException("Failed to install plugin: analysis-icu"),
                config,
                DistroFamily.ELASTICSEARCH,
                resolvedDistro,
                versionDir,
                versionDir.resolve("logs").resolve("runner.log"),
                null
        );

        assertTrue(ex instanceof PluginInstallException);
        assertEquals(ElasticRunnerException.Kind.PLUGIN_INSTALL, ex.kind());
        String diagnostics = Files.readString(ex.diagnosticsFile().orElseThrow());
        assertTrue(diagnostics.contains("Verify each plugin is compatible"));
    }
}
