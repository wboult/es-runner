package io.github.wboult.esrunner.gradle.docker;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DSL model for one shared Docker-backed Elasticsearch cluster definition.
 */
public abstract class DockerClusterSpec {
    private final String name;
    private final Property<String> version;
    private final Property<String> image;
    private final Property<String> clusterName;
    private final Property<Long> startupTimeoutMillis;
    private final MapProperty<String, String> envVars;

    /**
     * Creates a Docker-backed cluster specification.
     *
     * @param name cluster name in the Gradle container
     * @param objects Gradle object factory
     */
    @Inject
    public DockerClusterSpec(String name, ObjectFactory objects) {
        this.name = name;
        this.version = objects.property(String.class);
        this.image = objects.property(String.class);
        this.clusterName = objects.property(String.class);
        this.startupTimeoutMillis = objects.property(Long.class);
        this.envVars = objects.mapProperty(String.class, String.class);

        this.version.convention("9.3.1");
        this.image.convention(this.version.map(v -> "docker.elastic.co/elasticsearch/elasticsearch:" + v));
        this.clusterName.convention(name);
        this.startupTimeoutMillis.convention(Duration.ofMinutes(3).toMillis());

        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("discovery.type", "single-node");
        defaults.put("xpack.security.enabled", "false");
        defaults.put("cluster.routing.allocation.disk.threshold_enabled", "false");
        defaults.put("ES_JAVA_OPTS", "-Xms256m -Xmx256m");
        this.envVars.convention(defaults);
    }

    /** @return logical cluster name in the Gradle container */
    public String getName() {
        return name;
    }

    /** @return convenience Elasticsearch version used to derive the default image */
    public Property<String> getVersion() {
        return version;
    }

    /** @return full Docker image reference */
    public Property<String> getImage() {
        return image;
    }

    /** @return configured cluster name visible to Elasticsearch */
    public Property<String> getClusterName() {
        return clusterName;
    }

    /** @return startup timeout in milliseconds */
    public Property<Long> getStartupTimeoutMillis() {
        return startupTimeoutMillis;
    }

    /** @return Docker environment variables applied before startup */
    public MapProperty<String, String> getEnvVars() {
        return envVars;
    }

    /**
     * Sets the startup timeout using a {@link Duration}.
     *
     * @param duration startup timeout
     */
    public void startupTimeout(Duration duration) {
        startupTimeoutMillis.set(duration.toMillis());
    }

    /**
     * Adds or overrides one Docker environment variable.
     *
     * @param name environment variable name
     * @param value environment variable value
     */
    public void env(String name, String value) {
        envVars.put(name, value);
    }
}
