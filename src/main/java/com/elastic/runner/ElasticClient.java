package com.elastic.runner;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Small HTTP client wrapper for one Elasticsearch base URI.
 */
public final class ElasticClient implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Cluster base URI. */
    private final URI baseUri;
    /** Default timeout for standard requests. */
    private final Duration requestTimeout;
    /** Default timeout for bulk requests. */
    private final Duration bulkTimeout;
    private transient HttpClient httpClient;

    /**
     * Creates a client with default request and bulk timeouts.
     *
     * @param baseUri cluster base URI
     */
    public ElasticClient(URI baseUri) {
        this(baseUri, Duration.ofSeconds(30), Duration.ofMinutes(5), null);
    }

    ElasticClient(URI baseUri, HttpClient httpClient) {
        this(baseUri, Duration.ofSeconds(30), Duration.ofMinutes(5), httpClient);
    }

    private ElasticClient(URI baseUri, Duration requestTimeout, Duration bulkTimeout, HttpClient httpClient) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.bulkTimeout = Objects.requireNonNull(bulkTimeout, "bulkTimeout");
        this.httpClient = httpClient;
    }

    /**
     * Returns the cluster base URI used by this client.
     *
     * @return cluster base URI
     */
    public URI baseUri() {
        return baseUri;
    }

    /**
     * Returns a copy of this client with a different default request timeout.
     *
     * @param timeout request timeout
     * @return copied client
     */
    public ElasticClient withRequestTimeout(Duration timeout) {
        return new ElasticClient(baseUri, Objects.requireNonNull(timeout, "timeout"), bulkTimeout, httpClient);
    }

    /**
     * Returns a copy of this client with a different bulk request timeout.
     *
     * @param timeout bulk timeout
     * @return copied client
     */
    public ElasticClient withBulkTimeout(Duration timeout) {
        return new ElasticClient(baseUri, requestTimeout, Objects.requireNonNull(timeout, "timeout"), httpClient);
    }

    /**
     * Checks whether the cluster root endpoint currently responds with HTTP 200.
     *
     * @return {@code true} when the cluster is reachable
     */
    public boolean ping() {
        try {
            return request("GET", "/", null).statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Returns the raw JSON payload from the cluster root endpoint.
     *
     * @return cluster root response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String root() throws IOException, InterruptedException {
        return get("/");
    }

    /**
     * Returns typed cluster information from the root endpoint.
     *
     * @return cluster info
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public ClusterInfoResponse info() throws IOException, InterruptedException {
        return ClusterInfoResponse.fromJson(root());
    }

    /**
     * Returns the cluster name reported by Elasticsearch.
     *
     * @return cluster name
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String clusterName() throws IOException, InterruptedException {
        return info().clusterName();
    }

    /**
     * Returns the Elasticsearch version reported by the root endpoint.
     *
     * @return version number
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String version() throws IOException, InterruptedException {
        return info().versionNumber();
    }

    /**
     * Returns typed cluster health information.
     *
     * @return cluster health
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public ClusterHealthResponse clusterHealth() throws IOException, InterruptedException {
        return ClusterHealthResponse.fromJson(get("/_cluster/health"));
    }

    /**
     * Returns the current cluster health status string.
     *
     * @return health status such as {@code green} or {@code yellow}
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String clusterHealthStatus() throws IOException, InterruptedException {
        return clusterHealth().status();
    }

    /**
     * Waits until the cluster reaches at least the requested health status.
     *
     * @param status requested status
     * @param timeout maximum wait time
     * @return {@code true} if the requested status was observed before timeout
     * @throws IOException if a final request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean waitForStatus(String status, Duration timeout) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                ClusterHealthResponse health = clusterHealth();
                String currentStatus = health.status();
                if (status.equals(currentStatus) 
                    || ("yellow".equals(status) && "green".equals(currentStatus))) {
                    return true;
                }
            } catch (Exception ignored) {
                // Ignore transient errors before cluster is fully up
            }
            Thread.sleep(500);
        }
        return false;
    }

    /**
     * Waits for at least yellow cluster health.
     *
     * @param timeout maximum wait time
     * @return {@code true} if yellow or green was reached
     * @throws IOException if a final request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean waitForYellow(Duration timeout) throws IOException, InterruptedException {
        return waitForStatus("yellow", timeout);
    }

    /**
     * Waits for green cluster health.
     *
     * @param timeout maximum wait time
     * @return {@code true} if green was reached
     * @throws IOException if a final request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean waitForGreen(Duration timeout) throws IOException, InterruptedException {
        return waitForStatus("green", timeout);
    }

    /**
     * Performs a GET request and returns the response body.
     *
     * @param path relative or absolute cluster path
     * @return response body
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String get(String path) throws IOException, InterruptedException {
        return request("GET", path, null).body();
    }

    /**
     * Performs a POST request with a JSON body and returns the response body.
     *
     * @param path relative or absolute cluster path
     * @param body JSON request body
     * @return response body
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String post(String path, String body) throws IOException, InterruptedException {
        return request("POST", path, body).body();
    }

    /**
     * Performs a PUT request with an optional JSON body and returns the response
     * body.
     *
     * @param path relative or absolute cluster path
     * @param body JSON request body, or {@code null}
     * @return response body
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String put(String path, String body) throws IOException, InterruptedException {
        return request("PUT", path, body).body();
    }

    /**
     * Performs a DELETE request and returns the response body.
     *
     * @param path relative or absolute cluster path
     * @return response body
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String delete(String path) throws IOException, InterruptedException {
        return request("DELETE", path, null).body();
    }

    /**
     * Performs a HEAD request and checks for HTTP 200.
     *
     * @param path relative or absolute cluster path
     * @return {@code true} when the resource exists
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean exists(String path) throws IOException, InterruptedException {
        return request("HEAD", path, null).statusCode() == 200;
    }

    /**
     * Creates an empty index.
     *
     * @param index index name
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String createIndex(String index) throws IOException, InterruptedException {
        return put("/" + index, null);
    }

    /**
     * Creates an index using the supplied JSON body.
     *
     * @param index index name
     * @param json index creation body
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String createIndex(String index, String json) throws IOException, InterruptedException {
        return put("/" + index, json);
    }

    /**
     * Checks whether an index exists.
     *
     * @param index index name
     * @return {@code true} when the index exists
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean indexExists(String index) throws IOException, InterruptedException {
        return exists("/" + index);
    }

    /**
     * Deletes an index.
     *
     * @param index index name
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String deleteIndex(String index) throws IOException, InterruptedException {
        return delete("/" + index);
    }

    /**
     * Refreshes an index so indexed documents become searchable.
     *
     * @param index index name
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String refresh(String index) throws IOException, InterruptedException {
        return post("/" + index + "/_refresh", "");
    }

    /**
     * Indexes one document by id.
     *
     * @param index index name
     * @param id document id
     * @param json document JSON
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String indexDocument(String index, String id, String json) throws IOException, InterruptedException {
        return put("/" + index + "/_doc/" + id, json);
    }

    /**
     * Executes a search request against an index.
     *
     * @param index index name
     * @param jsonQuery search body
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String search(String index, String jsonQuery) throws IOException, InterruptedException {
        return post("/" + index + "/_search", jsonQuery);
    }

    /**
     * Returns the raw JSON count response for an index.
     *
     * @param index index name
     * @return raw count response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String count(String index) throws IOException, InterruptedException {
        return get("/" + index + "/_count");
    }

    /**
     * Returns just the numeric count value for an index.
     *
     * @param index index name
     * @return document count, or {@code 0} if unavailable
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public long countValue(String index) throws IOException, InterruptedException {
        String json = count(index);
        String value = jsonField(json, "count");
        if (value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    /**
     * Returns the JSON-formatted cat indices view.
     *
     * @return cat indices response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String catIndices() throws IOException, InterruptedException {
        return get("/_cat/indices?format=json");
    }

    /**
     * Returns node info for the cluster.
     *
     * @return node info response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String nodesInfo() throws IOException, InterruptedException {
        return get("/_nodes");
    }

    /**
     * Returns node stats for the cluster.
     *
     * @return node stats response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String nodesStats() throws IOException, InterruptedException {
        return get("/_nodes/stats");
    }

    /**
     * Creates or updates an index template.
     *
     * @param name template name
     * @param json template body
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String putIndexTemplate(String name, String json) throws IOException, InterruptedException {
        return put("/_index_template/" + name, json);
    }

    /**
     * Retrieves an index template.
     *
     * @param name template name
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String getIndexTemplate(String name) throws IOException, InterruptedException {
        return get("/_index_template/" + name);
    }

    /**
     * Deletes an index template.
     *
     * @param name template name
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String deleteIndexTemplate(String name) throws IOException, InterruptedException {
        return delete("/_index_template/" + name);
    }

    /**
     * Executes a bulk API request using NDJSON.
     *
     * @param ndjson bulk request body
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String bulk(String ndjson) throws IOException, InterruptedException {
        return requestNdjson("/_bulk", ndjson).body();
    }

    /**
     * Executes a raw HTTP request against the cluster.
     *
     * @param method HTTP method
     * @param path relative or absolute cluster path
     * @param body JSON request body, or {@code null}
     * @return raw response wrapper
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public ElasticResponse request(String method, String path, String body)
            throws IOException, InterruptedException {
        URI uri = baseUri.resolve(path.startsWith("/") ? path.substring(1) : path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = httpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new ElasticResponse(response.statusCode(), response.body(), response.headers().map());
    }

    /**
     * Executes a raw NDJSON request against the cluster.
     *
     * @param path relative or absolute cluster path
     * @param ndjson NDJSON request body
     * @return raw response wrapper
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public ElasticResponse requestNdjson(String path, String ndjson)
            throws IOException, InterruptedException {
        URI uri = baseUri.resolve(path.startsWith("/") ? path.substring(1) : path);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(bulkTimeout)
                .header("Content-Type", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofString(ndjson))
                .build();
        // Bulk requests are long-lived and hit the JDK connection-pool flake seen in CI, so use a fresh client.
        HttpResponse<String> response = buildHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        return new ElasticResponse(response.statusCode(), response.body(), response.headers().map());
    }

    private synchronized HttpClient httpClient() {
        if (httpClient == null) {
            httpClient = buildHttpClient();
        }
        return httpClient;
    }

    private static HttpClient buildHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static String jsonField(String json, String key) {
        return JsonUtils.parseFlatJson(json).getOrDefault(key, "");
    }
}
