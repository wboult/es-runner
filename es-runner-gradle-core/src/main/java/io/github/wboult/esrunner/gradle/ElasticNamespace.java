package io.github.wboult.esrunner.gradle;

import java.util.Locale;

/**
 * Derives stable, namespace-safe prefixes for shared test resources.
 */
public final class ElasticNamespace {
    private ElasticNamespace() {
    }

    /**
     * Builds the namespace prefix for one test task or project.
     *
     * @param buildId build-scoped unique identifier
     * @param projectPath Gradle project path
     * @param taskName Gradle task name
     * @param mode namespace derivation mode
     * @return namespace-safe resource prefix
     */
    public static String namespace(String buildId, String projectPath, String taskName, NamespaceMode mode) {
        String projectPart = sanitize(projectPath);
        return switch (mode) {
            case PROJECT -> buildId + "_" + projectPart;
            case SUITE -> buildId + "_" + projectPart + "_" + sanitize(taskName);
        };
    }

    static String sanitize(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        normalized = normalized.replace(':', '_');
        normalized = normalized.replaceAll("[^a-z0-9_]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+", "");
        normalized = normalized.replaceAll("_+$", "");
        return normalized.isEmpty() ? "root" : normalized;
    }
}
