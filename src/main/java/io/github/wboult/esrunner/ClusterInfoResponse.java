package io.github.wboult.esrunner;

import java.util.Map;
import java.util.Objects;

/**
 * Strongly-typed response for Elasticsearch cluster info.
 */
public record ClusterInfoResponse(
        String clusterName,
        String versionNumber
) {
    /**
     * Validates record components.
     *
     * @param clusterName cluster name
     * @param versionNumber Elasticsearch version
     */
    public ClusterInfoResponse {
        Objects.requireNonNull(clusterName, "clusterName");
        Objects.requireNonNull(versionNumber, "versionNumber");
    }

    /**
     * Parses the flat JSON representation into a ClusterInfoResponse.
     *
     * @param json flat JSON response body
     * @return parsed cluster info response
     */
    public static ClusterInfoResponse fromJson(String json) {
        Map<String, String> fields = JsonUtils.parseFlatJson(json);
        return new ClusterInfoResponse(
                fields.getOrDefault("cluster_name", ""),
                fields.getOrDefault("number", "")
        );
    }
}
