package io.github.wboult.esrunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class StartupFailureDiagnostics {
    private static final int LOG_LINES = 40;

    private StartupFailureDiagnostics() {
    }

    static ElasticRunnerException wrap(RuntimeException failure,
                                       ElasticRunnerConfig config,
                                       DistroFamily family,
                                       ResolvedDistro resolvedDistro,
                                       Path versionDir,
                                       Path logFile,
                                       Process process) {
        if (failure instanceof ElasticRunnerException existing && existing.diagnosticsFile().isPresent()) {
            return existing;
        }

        Path diagnosticsFile = versionDir.resolve("logs").resolve("startup-diagnostics.txt").toAbsolutePath();
        Integer exitCode = readExitCode(process);
        String logTail = logFile == null ? "Log file not available." : LogTail.read(logFile, LOG_LINES);
        List<String> hints = remediationHints(failure.getMessage(), config, resolvedDistro);
        String rendered = render(failure.getMessage(), config, family, resolvedDistro, diagnosticsFile, logFile, exitCode, logTail, hints);

        write(diagnosticsFile, rendered);

        String summary = summary(failure.getMessage(), resolvedDistro, diagnosticsFile, exitCode, logTail, hints);
        return new ElasticRunnerException(summary, failure, diagnosticsFile);
    }

    private static Integer readExitCode(Process process) {
        if (process == null || process.isAlive()) {
            return null;
        }
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException ignored) {
            return null;
        }
    }

    private static void write(Path diagnosticsFile, String rendered) {
        try {
            Files.createDirectories(diagnosticsFile.getParent());
            Files.writeString(diagnosticsFile, rendered, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Best-effort; the exception message still includes inline diagnostics.
        }
    }

    private static String render(String failureSummary,
                                 ElasticRunnerConfig config,
                                 DistroFamily family,
                                 ResolvedDistro resolvedDistro,
                                 Path diagnosticsFile,
                                 Path logFile,
                                 Integer exitCode,
                                 String logTail,
                                 List<String> hints) {
        StringBuilder builder = new StringBuilder();
        builder.append("ES Runner startup diagnostics").append(System.lineSeparator())
                .append("generated: ").append(DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now())).append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("Failure summary").append(System.lineSeparator())
                .append("- family: ").append(family.displayName()).append(System.lineSeparator())
                .append("- message: ").append(clean(failureSummary)).append(System.lineSeparator())
                .append("- diagnostics file: ").append(diagnosticsFile).append(System.lineSeparator());

        if (exitCode != null) {
            builder.append("- exit code: ").append(exitCode).append(System.lineSeparator());
        } else {
            builder.append("- exit code: process still alive or not started").append(System.lineSeparator());
        }

        builder.append(System.lineSeparator())
                .append("Resolved distro").append(System.lineSeparator())
                .append("- source kind: ").append(resolvedDistro.sourceKind()).append(System.lineSeparator())
                .append("- archive: ").append(resolvedDistro.archive().toAbsolutePath()).append(System.lineSeparator());

        if (resolvedDistro.downloadUri() != null) {
            builder.append("- download uri: ").append(resolvedDistro.downloadUri()).append(System.lineSeparator());
            builder.append("- download base URL: ").append(resolvedDistro.downloadBaseUrl()).append(System.lineSeparator());
        }

        builder.append(System.lineSeparator())
                .append("Captured config").append(System.lineSeparator());

        for (Map.Entry<String, String> entry : capturedConfig(config).entrySet()) {
            builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
        }

        builder.append(System.lineSeparator())
                .append("Recent log output").append(System.lineSeparator())
                .append(logTail).append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("Common remediation hints").append(System.lineSeparator());

        for (String hint : hints) {
            builder.append("- ").append(hint).append(System.lineSeparator());
        }

        if (logFile != null) {
            builder.append(System.lineSeparator())
                    .append("Runner log file").append(System.lineSeparator())
                    .append("- ").append(logFile.toAbsolutePath()).append(System.lineSeparator());
        }

        return builder.toString();
    }

    private static Map<String, String> capturedConfig(ElasticRunnerConfig config) {
        Map<String, String> captured = new LinkedHashMap<>();
        captured.put("family", config.family().name());
        captured.put("version", String.valueOf(config.version()));
        captured.put("distroZip", String.valueOf(config.distroZip()));
        captured.put("distrosDir", config.distrosDir().toAbsolutePath().toString());
        captured.put("download", Boolean.toString(config.download()));
        captured.put("downloadBaseUrl", config.downloadBaseUrl());
        captured.put("workDir", config.workDir().toAbsolutePath().toString());
        captured.put("clusterName", config.clusterName());
        captured.put("httpPort", Integer.toString(config.httpPort()));
        captured.put("portRange", config.portRangeStart() + "-" + config.portRangeEnd());
        captured.put("heap", config.heap());
        captured.put("startupTimeout", config.startupTimeout().toString());
        captured.put("shutdownTimeout", config.shutdownTimeout().toString());
        captured.put("plugins", config.plugins().toString());
        captured.put("quiet", Boolean.toString(config.quiet()));
        captured.put("settings", sanitizeSettings(config.settings()).toString());
        return captured;
    }

    private static Map<String, String> sanitizeSettings(Map<String, String> settings) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            sanitized.put(entry.getKey(), isSensitive(entry.getKey()) ? "<redacted>" : entry.getValue());
        }
        return sanitized;
    }

    private static boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("credential")
                || (lower.contains("api") && lower.contains("key"))
                || (lower.contains("access") && lower.contains("key"))
                || (lower.contains("ssl") && lower.contains("key"));
    }

    private static List<String> remediationHints(String failureSummary,
                                                 ElasticRunnerConfig config,
                                                 ResolvedDistro resolvedDistro) {
        String lower = clean(failureSummary).toLowerCase(Locale.ROOT);
        List<String> hints = new ArrayList<>();
        hints.add("Inspect the full diagnostics file and runner log shown above before retrying.");

        if (lower.contains("timed out")) {
            hints.add("Increase startupTimeout or heap if the node is starting slowly on this machine or CI runner.");
        }
        if (lower.contains("http port") || lower.contains("bind") || config.httpPort() > 0) {
            hints.add("Check for port collisions; prefer a wider port range if multiple local nodes may already be running.");
        }
        if (!config.settings().isEmpty()) {
            hints.add("Review custom settings for typos or unsupported keys. Start from defaults and add overrides back incrementally.");
        }
        if (!config.plugins().isEmpty()) {
            hints.add("Verify each plugin is compatible with " + resolvedDistro.family().displayName() + " " + resolvedDistro.version() + ".");
        }
        if (resolvedDistro.downloadUri() != null) {
            hints.add("Verify the resolved distro source exists and matches the requested family/version: " + resolvedDistro.downloadUri());
        }
        hints.add("If the process exited immediately, check filesystem permissions for workDir, distrosDir, and any custom data/log paths.");
        return hints;
    }

    private static String summary(String failureSummary,
                                  ResolvedDistro resolvedDistro,
                                  Path diagnosticsFile,
                                  Integer exitCode,
                                  String logTail,
                                  List<String> hints) {
        StringBuilder builder = new StringBuilder();
        builder.append(clean(failureSummary)).append(System.lineSeparator())
                .append("Resolved archive: ").append(resolvedDistro.archive().toAbsolutePath()).append(System.lineSeparator());
        if (resolvedDistro.downloadUri() != null) {
            builder.append("Resolved download URI: ").append(resolvedDistro.downloadUri()).append(System.lineSeparator());
        }
        if (exitCode != null) {
            builder.append("Exit code: ").append(exitCode).append(System.lineSeparator());
        }
        builder.append("Startup diagnostics: ").append(diagnosticsFile).append(System.lineSeparator())
                .append(logTail).append(System.lineSeparator())
                .append("Hints:").append(System.lineSeparator());
        for (String hint : hints) {
            builder.append("- ").append(hint).append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private static String clean(String text) {
        return text == null ? "Unknown startup failure." : text.trim();
    }
}
