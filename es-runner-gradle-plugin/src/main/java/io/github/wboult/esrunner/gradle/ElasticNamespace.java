package io.github.wboult.esrunner.gradle;

import java.util.Locale;

final class ElasticNamespace {
    private ElasticNamespace() {
    }

    static String namespace(String buildId, String projectPath, String taskName, NamespaceMode mode) {
        String projectPart = sanitize(projectPath);
        return switch (mode) {
            case PROJECT -> buildId + "_" + projectPart;
            case SUITE -> buildId + "_" + projectPart + "_" + sanitize(taskName);
        };
    }

    private static String sanitize(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        normalized = normalized.replace(':', '_');
        normalized = normalized.replaceAll("[^a-z0-9_]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+", "");
        normalized = normalized.replaceAll("_+$", "");
        return normalized.isEmpty() ? "root" : normalized;
    }
}
