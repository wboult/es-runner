package com.elastic.runner.gradle;

import org.gradle.api.tasks.testing.Test;

import java.util.Locale;

final class ElasticNamespace {
    private ElasticNamespace() {
    }

    static String namespace(String buildId, Test task, NamespaceMode mode) {
        String projectPart = sanitize(task.getProject().getPath());
        return switch (mode) {
            case PROJECT -> buildId + "_" + projectPart;
            case SUITE -> buildId + "_" + projectPart + "_" + sanitize(task.getName());
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
