package io.github.wboult.esrunner.gradle.docker;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

enum DockerDistribution {
    ELASTICSEARCH("elasticsearch", "9.3.1"),
    OPENSEARCH("opensearch", "3.5.0");

    private final String id;
    private final String defaultVersion;

    DockerDistribution(String id, String defaultVersion) {
        this.id = id;
        this.defaultVersion = defaultVersion;
    }

    String id() {
        return id;
    }

    String defaultVersion() {
        return defaultVersion;
    }

    String defaultImage(String version) {
        return switch (this) {
            case ELASTICSEARCH -> "docker.elastic.co/elasticsearch/elasticsearch:" + version;
            case OPENSEARCH -> "opensearchproject/opensearch:" + version;
        };
    }

    Map<String, String> defaultEnvVars() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("discovery.type", "single-node");
        switch (this) {
            case ELASTICSEARCH -> {
                defaults.put("cluster.routing.allocation.disk.threshold_enabled", "false");
                defaults.put("xpack.security.enabled", "false");
                defaults.put("ES_JAVA_OPTS", "-Xms256m -Xmx256m");
            }
            case OPENSEARCH -> {
                defaults.put("DISABLE_SECURITY_PLUGIN", "true");
                defaults.put("OPENSEARCH_JAVA_OPTS", "-Xms256m -Xmx256m");
            }
        }
        return defaults;
    }

    static DockerDistribution from(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        for (DockerDistribution distribution : values()) {
            if (distribution.id.equals(normalized)) {
                return distribution;
            }
        }
        throw new IllegalArgumentException(
                "Unsupported Docker distribution '" + rawValue + "'. Supported values: elasticsearch, opensearch.");
    }
}
