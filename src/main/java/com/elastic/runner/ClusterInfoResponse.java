package com.elastic.runner;

import java.util.Map;
import java.util.Objects;

/**
 * Strongly-typed response for Elasticsearch cluster info.
 */
public record ClusterInfoResponse(
        String clusterName,
        String versionNumber
) {
    public ClusterInfoResponse {
        Objects.requireNonNull(clusterName, "clusterName");
        Objects.requireNonNull(versionNumber, "versionNumber");
    }

    /**
     * Parses the flat JSON representation into a ClusterInfoResponse.
     */
    public static ClusterInfoResponse fromJson(String json) {
        Map<String, String> fields = JsonUtils.parseFlatJson(json);
        return new ClusterInfoResponse(
                fields.getOrDefault("cluster_name", ""),
                fields.getOrDefault("number", "")
        );
    }
}
