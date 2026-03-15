package io.github.wboult.esrunner.gradle;

/**
 * Minimal backend contract used by shared Gradle test-cluster integrations.
 */
public interface SharedClusterBackend extends AutoCloseable {
    /**
     * Lazily starts the backend if needed and returns stable cluster metadata.
     *
     * @return cluster metadata
     */
    ElasticClusterMetadata metadata();
}
