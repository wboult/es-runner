package io.github.wboult.esrunner.gradle.testsupport;

import io.github.wboult.esrunner.ElasticClient;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Test-side view of the system properties injected by the shared Gradle test
 * cluster plugin.
 */
public final class ElasticGradleTestEnv {
    private final URI baseUri;
    private final int httpPort;
    private final String clusterName;
    private final String buildId;
    private final String suiteId;
    private final String namespace;

    /**
     * Creates an environment wrapper from explicit values.
     *
     * @param baseUri cluster base URI
     * @param httpPort cluster HTTP port
     * @param clusterName configured cluster name
     * @param buildId build-scoped cluster identifier
     * @param suiteId suite task path
     * @param namespace suite namespace prefix
     */
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

    /**
     * Builds an environment wrapper from JVM system properties injected by the
     * Gradle plugin.
     *
     * @return parsed test environment
     */
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

    /**
     * Returns the shared cluster base URI.
     *
     * @return base URI
     */
    public URI baseUri() {
        return baseUri;
    }

    /**
     * Returns the shared cluster HTTP port.
     *
     * @return HTTP port
     */
    public int httpPort() {
        return httpPort;
    }

    /**
     * Returns the configured cluster name.
     *
     * @return cluster name
     */
    public String clusterName() {
        return clusterName;
    }

    /**
     * Returns the build-scoped identifier injected by the plugin.
     *
     * @return build id
     */
    public String buildId() {
        return buildId;
    }

    /**
     * Returns the bound suite identifier, usually the Gradle task path.
     *
     * @return suite id
     */
    public String suiteId() {
        return suiteId;
    }

    /**
     * Returns the namespace prefix assigned to this suite.
     *
     * @return namespace prefix
     */
    public String namespace() {
        return namespace;
    }

    /**
     * Creates a client for the shared cluster.
     *
     * @return client bound to the injected base URI
     */
    public ElasticClient client() {
        return new ElasticClient(baseUri);
    }

    /**
     * Returns a namespaced index name for the logical resource.
     *
     * <p>This helper is for concrete index names only. Use
     * {@link #indexPattern(String)} for template {@code index_patterns} or
     * other wildcard-based matching.</p>
     *
     * @param logicalName stable logical index name used by the suite
     * @return namespaced index name
     */
    public String index(String logicalName) {
        return concreteResourceName("index", logicalName);
    }

    /**
     * Returns a namespaced index pattern for the logical resource prefix.
     *
     * <p>Use this for template {@code index_patterns} or other wildcard-based
     * matching. For concrete index names, use {@link #index(String)}.</p>
     *
     * @param logicalPrefix stable logical resource prefix used by the suite
     * @return namespaced index pattern ending in {@code -*}
     */
    public String indexPattern(String logicalPrefix) {
        return concreteResourceName("index prefix", logicalPrefix) + "-*";
    }

    /**
     * Returns a namespaced alias name for the logical resource.
     *
     * @param logicalName stable logical alias name used by the suite
     * @return namespaced alias name
     */
    public String alias(String logicalName) {
        return concreteResourceName("alias", logicalName);
    }

    /**
     * Returns a namespaced data stream name for the logical resource.
     *
     * @param logicalName stable logical data stream name used by the suite
     * @return namespaced data stream name
     */
    public String dataStream(String logicalName) {
        return concreteResourceName("data stream", logicalName);
    }

    /**
     * Returns a namespaced index template name for the logical resource.
     *
     * @param logicalName stable logical template name used by the suite
     * @return namespaced template name
     */
    public String template(String logicalName) {
        return concreteResourceName("template", logicalName);
    }

    /**
     * Returns a namespaced ingest pipeline name for the logical resource.
     *
     * @param logicalName stable logical pipeline name used by the suite
     * @return namespaced pipeline name
     */
    public String pipeline(String logicalName) {
        return concreteResourceName("pipeline", logicalName);
    }

    private String concreteResourceName(String resourceKind, String logicalName) {
        rejectPatternLikeInput(resourceKind, logicalName);
        String sanitized = sanitize(logicalName);
        return namespace + "-" + sanitized;
    }

    private static void rejectPatternLikeInput(String resourceKind, String logicalName) {
        Objects.requireNonNull(logicalName, "logicalName");
        if (logicalName.contains("*") || logicalName.contains("?") || logicalName.contains(",")) {
            throw new IllegalArgumentException(
                    resourceKind + " logical names must be concrete. "
                            + "Use indexPattern(...) for wildcard-based index patterns instead: "
                            + logicalName
            );
        }
    }

    private static String sanitize(String logicalName) {
        String normalized = logicalName
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
