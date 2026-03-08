package com.elastic.runner.gradle.testsupport;

import com.elastic.runner.ElasticClient;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ElasticGradleTestEnv {
    private final URI baseUri;
    private final int httpPort;
    private final String clusterName;
    private final String buildId;
    private final String suiteId;
    private final String namespace;

    public ElasticGradleTestEnv(URI baseUri,
                                int httpPort,
                                String clusterName,
                                String buildId,
                                String suiteId,
                                String namespace) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.httpPort = httpPort;
        this.clusterName = Objects.requireNonNull(clusterName, "clusterName");
        this.buildId = Objects.requireNonNull(buildId, "buildId");
        this.suiteId = Objects.requireNonNull(suiteId, "suiteId");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
    }

    public static ElasticGradleTestEnv fromSystemProperties() {
        return fromSystemProperties(System.getProperties().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        entry -> entry.getValue().toString()
                )));
    }

    static ElasticGradleTestEnv fromSystemProperties(Map<String, String> properties) {
        String baseUri = require(properties, "elastic.runner.baseUri");
        String clusterName = require(properties, "elastic.runner.clusterName");
        String buildId = require(properties, "elastic.runner.buildId");
        String suiteId = require(properties, "elastic.runner.suiteId");
        String namespace = require(properties, "elastic.runner.namespace");
        int httpPort = Integer.parseInt(require(properties, "elastic.runner.httpPort"));
        return new ElasticGradleTestEnv(
                URI.create(baseUri),
                httpPort,
                clusterName,
                buildId,
                suiteId,
                namespace
        );
    }

    public URI baseUri() {
        return baseUri;
    }

    public int httpPort() {
        return httpPort;
    }

    public String clusterName() {
        return clusterName;
    }

    public String buildId() {
        return buildId;
    }

    public String suiteId() {
        return suiteId;
    }

    public String namespace() {
        return namespace;
    }

    public ElasticClient client() {
        return new ElasticClient(baseUri);
    }

    public String index(String logicalName) {
        return namespaced(logicalName);
    }

    public String alias(String logicalName) {
        return namespaced(logicalName);
    }

    public String dataStream(String logicalName) {
        return namespaced(logicalName);
    }

    public String template(String logicalName) {
        return namespaced(logicalName);
    }

    public String pipeline(String logicalName) {
        return namespaced(logicalName);
    }

    private String namespaced(String logicalName) {
        String sanitized = sanitize(logicalName);
        return namespace + "-" + sanitized;
    }

    private static String sanitize(String logicalName) {
        String normalized = Objects.requireNonNull(logicalName, "logicalName")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return normalized.isEmpty() ? "resource" : normalized;
    }

    private static String require(Map<String, String> properties, String key) {
        String value = properties.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required system property: " + key);
        }
        return value;
    }
}
