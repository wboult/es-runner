package io.github.wboult.esrunner.gradle.docker;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.time.Duration;
/**
 * DSL model for one shared Docker-backed search cluster definition.
 */
public abstract class DockerClusterSpec {
    private final String name;
    private final Property<String> distribution;
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
        this.distribution = objects.property(String.class);
        this.version = objects.property(String.class);
        this.image = objects.property(String.class);
        this.clusterName = objects.property(String.class);
        this.startupTimeoutMillis = objects.property(Long.class);
        this.envVars = objects.mapProperty(String.class, String.class);

        this.distribution.convention(DockerDistribution.ELASTICSEARCH.id());
        this.version.convention(this.distribution.map(raw -> DockerDistribution.from(raw).defaultVersion()));
        this.image.convention(this.distribution.flatMap(raw ->
                this.version.map(version -> DockerDistribution.from(raw).defaultImage(version))));
        this.clusterName.convention(name);
        this.startupTimeoutMillis.convention(Duration.ofMinutes(3).toMillis());
        this.envVars.convention(this.distribution.map(raw -> DockerDistribution.from(raw).defaultEnvVars()));
    }

    /** @return logical cluster name in the Gradle container */
    public String getName() {
        return name;
    }

    /** @return distribution family used to derive the default version, image, and env vars */
    public Property<String> getDistribution() {
        return distribution;
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
     * Selects the Docker distribution family.
     *
     * @param distribution supported values: {@code elasticsearch}, {@code opensearch}
     */
    public void distribution(String distribution) {
        this.distribution.set(distribution);
    }

    /** Uses the default Elasticsearch image family. */
    public void elasticsearch() {
        this.distribution.set(DockerDistribution.ELASTICSEARCH.id());
    }

    /** Uses the default OpenSearch image family. */
    public void opensearch() {
        this.distribution.set(DockerDistribution.OPENSEARCH.id());
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

    /**
     * Copies the configured DSL values into one build-service parameter object.
     *
     * @param params target parameter object
     */
    void copyTo(DockerClusterService.Params params) {
        params.getName().set(getName());
        params.getDistribution().set(getDistribution());
        params.getImage().set(getImage());
        params.getClusterName().set(getClusterName());
        params.getStartupTimeoutMillis().set(getStartupTimeoutMillis());
        params.getEnvVars().set(getEnvVars());
    }
}
