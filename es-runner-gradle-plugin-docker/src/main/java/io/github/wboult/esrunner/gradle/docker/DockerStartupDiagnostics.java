package io.github.wboult.esrunner.gradle.docker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DockerStartupDiagnostics {
    private static final int LOG_CHARS = 4000;

    private DockerStartupDiagnostics() {
    }

    static String renderFailure(String clusterDefinitionName,
                                String configuredClusterName,
                                String image,
                                Duration startupTimeout,
                                Map<String, String> envVars,
                                DockerContainerSnapshot snapshot,
                                RuntimeException failure) {
        String failureSummary = clean(failure == null ? null : failure.getMessage());
        StringBuilder builder = new StringBuilder();
        builder.append("Failed to start shared Docker cluster '")
                .append(clusterDefinitionName)
                .append("'.")
                .append(System.lineSeparator())
                .append("Cluster diagnostics")
                .append(System.lineSeparator())
                .append("- configured cluster name: ").append(configuredClusterName).append(System.lineSeparator())
                .append("- image: ").append(image).append(System.lineSeparator())
                .append("- startup timeout: ").append(startupTimeout).append(System.lineSeparator())
                .append("- failure: ").append(failureSummary).append(System.lineSeparator());

        appendDockerEnvironment(builder);
        appendContainerSnapshot(builder, snapshot);

        builder.append("Configured container env").append(System.lineSeparator());
        for (Map.Entry<String, String> entry : sanitizeEnv(envVars).entrySet()) {
            builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
        }

        builder.append("Recent container logs").append(System.lineSeparator())
                .append(snapshot == null ? "Container logs unavailable." : cleanLogs(snapshot.logsTail()))
                .append(System.lineSeparator())
                .append("Common remediation hints")
                .append(System.lineSeparator());

        for (String hint : remediationHints(failureSummary, image, startupTimeout)) {
            builder.append("- ").append(hint).append(System.lineSeparator());
        }

        return builder.toString().trim();
    }

    static DockerContainerSnapshot snapshot(String image, org.testcontainers.containers.GenericContainer<?> container) {
        if (container == null) {
            return new DockerContainerSnapshot(image, null, null, null, null, null, null);
        }

        String containerId = null;
        String containerName = null;
        String host = null;
        Integer httpPort = null;
        Integer transportPort = null;
        String logsTail = null;

        try {
            containerId = container.getContainerId();
        } catch (Exception ignored) {
        }
        try {
            if (container.getContainerInfo() != null && container.getContainerInfo().getName() != null) {
                containerName = container.getContainerInfo().getName();
            }
        } catch (Exception ignored) {
        }
        try {
            host = container.getHost();
        } catch (Exception ignored) {
        }
        try {
            httpPort = container.getMappedPort(9200);
        } catch (Exception ignored) {
        }
        try {
            transportPort = container.getMappedPort(9300);
        } catch (Exception ignored) {
        }
        try {
            logsTail = container.getLogs();
        } catch (Exception ignored) {
        }

        return new DockerContainerSnapshot(
                image,
                containerId,
                containerName,
                host,
                httpPort,
                transportPort,
                logsTail
        );
    }

    private static void appendDockerEnvironment(StringBuilder builder) {
        builder.append("Docker/Testcontainers environment").append(System.lineSeparator());
        appendEnv(builder, "DOCKER_HOST");
        appendEnv(builder, "TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE");
        appendEnv(builder, "TESTCONTAINERS_HOST_OVERRIDE");
        appendEnv(builder, "TESTCONTAINERS_DOCKER_CLIENT_STRATEGY");
    }

    private static void appendEnv(StringBuilder builder, String key) {
        String value = System.getenv(key);
        builder.append("- ")
                .append(key)
                .append(": ")
                .append(value == null || value.isBlank() ? "<default>" : value)
                .append(System.lineSeparator());
    }

    private static void appendContainerSnapshot(StringBuilder builder, DockerContainerSnapshot snapshot) {
        builder.append("Container state").append(System.lineSeparator());
        if (snapshot == null) {
            builder.append("- state: container never started").append(System.lineSeparator());
            return;
        }

        builder.append("- container id: ").append(orUnavailable(snapshot.containerId())).append(System.lineSeparator())
                .append("- container name: ").append(orUnavailable(snapshot.containerName())).append(System.lineSeparator())
                .append("- host: ").append(orUnavailable(snapshot.host())).append(System.lineSeparator())
                .append("- mapped http port: ")
                .append(snapshot.mappedHttpPort() == null ? "<unavailable>" : snapshot.mappedHttpPort())
                .append(System.lineSeparator())
                .append("- mapped transport port: ")
                .append(snapshot.mappedTransportPort() == null ? "<unavailable>" : snapshot.mappedTransportPort())
                .append(System.lineSeparator());
    }

    private static Map<String, String> sanitizeEnv(Map<String, String> envVars) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            sanitized.put(entry.getKey(), isSensitive(entry.getKey()) ? "<redacted>" : entry.getValue());
        }
        return sanitized;
    }

    private static boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("secret")
                || lower.contains("token")
                || (lower.contains("api") && lower.contains("key"))
                || (lower.contains("access") && lower.contains("key"))
                || (lower.contains("ssl") && lower.contains("key"));
    }

    private static List<String> remediationHints(String failureSummary, String image, Duration startupTimeout) {
        String lower = failureSummary.toLowerCase(Locale.ROOT);
        List<String> hints = new ArrayList<>();
        hints.add("Inspect the container log tail above before changing the build configuration.");

        if (lower.contains("docker environment") || lower.contains("docker host") || lower.contains("permission denied")) {
            hints.add("Verify the Docker daemon is reachable from this Gradle process and that the runner user can access the Docker socket.");
        }
        if (lower.contains("manifest") || lower.contains("pull") || lower.contains("not found")) {
            hints.add("Verify the configured image exists and that the registry is reachable: " + image);
        }
        if (lower.contains("timed out")) {
            hints.add("Increase startupTimeoutMillis if image pull or node startup is slow on this machine or CI runner. Current timeout: " + startupTimeout + ".");
        }
        if (lower.contains("cluster health probe returned")) {
            hints.add("The container started but Elasticsearch never reached the expected health probe. Check cluster settings, security settings, and the log tail.");
        }

        hints.add("If Docker is already healthy, rerun with the same image locally and compare the mapped port and container logs shown above.");
        return hints;
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? "Unknown Docker startup failure." : text.trim();
    }

    private static String cleanLogs(String logs) {
        if (logs == null || logs.isBlank()) {
            return "Container logs unavailable.";
        }
        String normalized = logs.strip();
        if (normalized.length() <= LOG_CHARS) {
            return normalized;
        }
        return normalized.substring(normalized.length() - LOG_CHARS);
    }

    private static String orUnavailable(String value) {
        return value == null || value.isBlank() ? "<unavailable>" : value;
    }

    record DockerContainerSnapshot(
            String image,
            String containerId,
            String containerName,
            String host,
            Integer mappedHttpPort,
            Integer mappedTransportPort,
            String logsTail
    ) {
    }
}
