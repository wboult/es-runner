package com.elastic.runner.gradle;

public record ElasticClusterMetadata(String baseUri, int httpPort, String clusterName) {
}
