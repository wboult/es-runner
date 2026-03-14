package io.github.wboult.esrunner;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * Common API for a started Elasticsearch server, regardless of whether it runs
 * in a separate process or inside the current JVM.
 */
public interface ElasticServerHandle extends AutoCloseable {
    /**
     * Returns the hosting model for this server.
     *
     * @return server runtime type
     */
    ElasticServerType type();

    /**
     * Returns the HTTP base URI.
     *
     * @return cluster base URI
     */
    URI baseUri();

    /**
     * Returns the bound HTTP port.
     *
     * @return HTTP port
     */
    int httpPort();

    /**
     * Returns the shared HTTP client wrapper for this server.
     *
     * @return Elasticsearch client
     */
    ElasticClient client();

    /**
     * Checks whether the root endpoint currently responds.
     *
     * @return {@code true} when reachable
     */
    default boolean ping() {
        return client().ping();
    }

    /**
     * Returns the raw cluster root payload.
     *
     * @return cluster root JSON
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String root() throws IOException, InterruptedException {
        return client().root();
    }

    /**
     * Returns typed cluster information.
     *
     * @return cluster info
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default ClusterInfoResponse info() throws IOException, InterruptedException {
        return client().info();
    }

    /**
     * Returns the cluster name reported by the root endpoint.
     *
     * @return cluster name
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String clusterName() throws IOException, InterruptedException {
        return client().clusterName();
    }

    /**
     * Returns the version number reported by the root endpoint.
     *
     * @return version number
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String version() throws IOException, InterruptedException {
        return client().version();
    }

    /**
     * Returns typed cluster health.
     *
     * @return cluster health
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default ClusterHealthResponse clusterHealth() throws IOException, InterruptedException {
        return client().clusterHealth();
    }

    /**
     * Returns the current cluster health status string.
     *
     * @return health status such as {@code green} or {@code yellow}
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String clusterHealthStatus() throws IOException, InterruptedException {
        return client().clusterHealthStatus();
    }

    /**
     * Waits until the cluster reaches at least the requested health status.
     *
     * @param status requested health status
     * @param timeout maximum wait time
     * @return {@code true} if observed before timeout
     * @throws IOException if a final request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default boolean waitForStatus(String status, Duration timeout) throws IOException, InterruptedException {
        return client().waitForStatus(status, timeout);
    }

    /**
     * Waits for at least yellow cluster health.
     *
     * @param timeout maximum wait time
     * @return {@code true} if yellow or green was reached
     * @throws IOException if a final request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default boolean waitForYellow(Duration timeout) throws IOException, InterruptedException {
        return client().waitForYellow(timeout);
    }

    /**
     * Waits for green cluster health.
     *
     * @param timeout maximum wait time
     * @return {@code true} if green was reached
     * @throws IOException if a final request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default boolean waitForGreen(Duration timeout) throws IOException, InterruptedException {
        return client().waitForGreen(timeout);
    }

    /**
     * Performs a GET request.
     *
     * @param path cluster-relative path
     * @return response body
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String get(String path) throws IOException, InterruptedException {
        return client().get(path);
    }

    /**
     * Performs a POST request with a JSON body.
     *
     * @param path cluster-relative path
     * @param body JSON request body
     * @return response body
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String post(String path, String body) throws IOException, InterruptedException {
        return client().post(path, body);
    }

    /**
     * Performs a PUT request with an optional JSON body.
     *
     * @param path cluster-relative path
     * @param body JSON request body or {@code null}
     * @return response body
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String put(String path, String body) throws IOException, InterruptedException {
        return client().put(path, body);
    }

    /**
     * Performs a DELETE request.
     *
     * @param path cluster-relative path
     * @return response body
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String delete(String path) throws IOException, InterruptedException {
        return client().delete(path);
    }

    /**
     * Checks whether the given path exists.
     *
     * @param path cluster-relative path
     * @return {@code true} when the resource exists
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default boolean exists(String path) throws IOException, InterruptedException {
        return client().exists(path);
    }

    /**
     * Creates an empty index.
     *
     * @param index index name
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String createIndex(String index) throws IOException, InterruptedException {
        return client().createIndex(index);
    }

    /**
     * Creates an index using the supplied JSON body.
     *
     * @param index index name
     * @param json JSON index creation body
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String createIndex(String index, String json) throws IOException, InterruptedException {
        return client().createIndex(index, json);
    }

    /**
     * Checks whether the given index exists.
     *
     * @param index index name
     * @return {@code true} when it exists
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default boolean indexExists(String index) throws IOException, InterruptedException {
        return client().indexExists(index);
    }

    /**
     * Deletes an index.
     *
     * @param index index name
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String deleteIndex(String index) throws IOException, InterruptedException {
        return client().deleteIndex(index);
    }

    /**
     * Refreshes an index.
     *
     * @param index index name
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String refresh(String index) throws IOException, InterruptedException {
        return client().refresh(index);
    }

    /**
     * Indexes one JSON document under a fixed id.
     *
     * @param index index name
     * @param id document id
     * @param json document JSON
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String indexDocument(String index, String id, String json) throws IOException, InterruptedException {
        return client().indexDocument(index, id, json);
    }

    /**
     * Indexes one JSON document and lets the server generate the document id.
     *
     * @param index index name
     * @param json document JSON
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String indexDocument(String index, String json) throws IOException, InterruptedException {
        return client().indexDocument(index, json);
    }

    /**
     * Executes a search request.
     *
     * @param index index name
     * @param jsonQuery JSON search body
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String search(String index, String jsonQuery) throws IOException, InterruptedException {
        return client().search(index, jsonQuery);
    }

    /**
     * Returns the raw count response for an index.
     *
     * @param index index name
     * @return raw count response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String count(String index) throws IOException, InterruptedException {
        return client().count(index);
    }

    /**
     * Returns the parsed document count for an index.
     *
     * @param index index name
     * @return parsed document count
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default long countValue(String index) throws IOException, InterruptedException {
        return client().countValue(index);
    }

    /**
     * Returns `_cat/indices`.
     *
     * @return raw cat output
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String catIndices() throws IOException, InterruptedException {
        return client().catIndices();
    }

    /**
     * Returns `_nodes`.
     *
     * @return raw nodes info response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String nodesInfo() throws IOException, InterruptedException {
        return client().nodesInfo();
    }

    /**
     * Returns `_nodes/stats`.
     *
     * @return raw nodes stats response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String nodesStats() throws IOException, InterruptedException {
        return client().nodesStats();
    }

    /**
     * Creates or updates an index template.
     *
     * @param name template name
     * @param json template JSON
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String putIndexTemplate(String name, String json) throws IOException, InterruptedException {
        return client().putIndexTemplate(name, json);
    }

    /**
     * Returns index templates.
     *
     * @param name template name
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String getIndexTemplate(String name) throws IOException, InterruptedException {
        return client().getIndexTemplate(name);
    }

    /**
     * Deletes an index template.
     *
     * @param name template name
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String deleteIndexTemplate(String name) throws IOException, InterruptedException {
        return client().deleteIndexTemplate(name);
    }

    /**
     * Sends an NDJSON bulk request.
     *
     * @param ndjson newline-delimited bulk payload
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String bulk(String ndjson) throws IOException, InterruptedException {
        return client().bulk(ndjson);
    }

    /**
     * Builds and sends a bulk indexing request for one index from plain JSON payloads.
     *
     * @param index index name
     * @param jsonDocuments document JSON payloads
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    default String bulkIndexDocuments(String index, List<String> jsonDocuments) throws IOException, InterruptedException {
        return client().bulkIndexDocuments(index, jsonDocuments);
    }
}
