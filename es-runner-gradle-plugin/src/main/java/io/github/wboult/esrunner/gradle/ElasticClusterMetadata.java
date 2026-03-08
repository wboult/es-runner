package io.github.wboult.esrunner.gradle;

/**
 * Runtime metadata exposed by a shared Gradle cluster service.
 *
 * @param baseUri cluster base URI
 * @param httpPort bound HTTP port
 * @param clusterName configured cluster name
 */
public record ElasticClusterMetadata(String baseUri, int httpPort, String clusterName) {
}
