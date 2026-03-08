package com.elastic.runner;

import java.util.Map;
import java.util.Objects;

/**
 * Strongly-typed response for Elasticsearch cluster health.
 */
public record ClusterHealthResponse(
        String clusterName,
        String status,
        int numberOfNodes,
        int numberOfDataNodes
) {
    /**
     * Validates record components.
     *
     * @param clusterName cluster name
     * @param status health status
     * @param numberOfNodes total node count
     * @param numberOfDataNodes data node count
     */
    public ClusterHealthResponse {
        Objects.requireNonNull(clusterName, "clusterName");
        Objects.requireNonNull(status, "status");
    }

    /**
     * Parses the flat JSON representation into a ClusterHealthResponse.
     *
     * @param json flat JSON response body
     * @return parsed cluster health response
     */
    public static ClusterHealthResponse fromJson(String json) {
        Map<String, String> fields = JsonUtils.parseFlatJson(json);
        return new ClusterHealthResponse(
                fields.getOrDefault("cluster_name", ""),
                fields.getOrDefault("status", "red"),
                parseIntOrZero(fields.get("number_of_nodes")),
                parseIntOrZero(fields.get("number_of_data_nodes"))
        );
    }

    private static int parseIntOrZero(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
