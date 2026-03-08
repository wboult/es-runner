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

public final class ElasticClient implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final URI baseUri;
    private final Duration requestTimeout;
    private final Duration bulkTimeout;
    private transient HttpClient httpClient;

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

    public URI baseUri() {
        return baseUri;
    }

    public ElasticClient withRequestTimeout(Duration timeout) {
        return new ElasticClient(baseUri, Objects.requireNonNull(timeout, "timeout"), bulkTimeout, httpClient);
    }

    public ElasticClient withBulkTimeout(Duration timeout) {
        return new ElasticClient(baseUri, requestTimeout, Objects.requireNonNull(timeout, "timeout"), httpClient);
    }

    public boolean ping() {
        try {
            return request("GET", "/", null).statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    public String root() throws IOException, InterruptedException {
        return get("/");
    }

    public ClusterInfoResponse info() throws IOException, InterruptedException {
        return ClusterInfoResponse.fromJson(root());
    }

    public String clusterName() throws IOException, InterruptedException {
        return info().clusterName();
    }

    public String version() throws IOException, InterruptedException {
        return info().versionNumber();
    }

    public ClusterHealthResponse clusterHealth() throws IOException, InterruptedException {
        return ClusterHealthResponse.fromJson(get("/_cluster/health"));
    }

    public String clusterHealthStatus() throws IOException, InterruptedException {
        return clusterHealth().status();
    }

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

    public boolean waitForYellow(Duration timeout) throws IOException, InterruptedException {
        return waitForStatus("yellow", timeout);
    }

    public boolean waitForGreen(Duration timeout) throws IOException, InterruptedException {
        return waitForStatus("green", timeout);
    }

    public String get(String path) throws IOException, InterruptedException {
        return request("GET", path, null).body();
    }

    public String post(String path, String body) throws IOException, InterruptedException {
        return request("POST", path, body).body();
    }

    public String put(String path, String body) throws IOException, InterruptedException {
        return request("PUT", path, body).body();
    }

    public String delete(String path) throws IOException, InterruptedException {
        return request("DELETE", path, null).body();
    }

    public boolean exists(String path) throws IOException, InterruptedException {
        return request("HEAD", path, null).statusCode() == 200;
    }

    public String createIndex(String index) throws IOException, InterruptedException {
        return put("/" + index, null);
    }

    public String createIndex(String index, String json) throws IOException, InterruptedException {
        return put("/" + index, json);
    }

    public boolean indexExists(String index) throws IOException, InterruptedException {
        return exists("/" + index);
    }

    public String deleteIndex(String index) throws IOException, InterruptedException {
        return delete("/" + index);
    }

    public String refresh(String index) throws IOException, InterruptedException {
        return post("/" + index + "/_refresh", "");
    }

    public String indexDocument(String index, String id, String json) throws IOException, InterruptedException {
        return put("/" + index + "/_doc/" + id, json);
    }

    public String search(String index, String jsonQuery) throws IOException, InterruptedException {
        return post("/" + index + "/_search", jsonQuery);
    }

    public String count(String index) throws IOException, InterruptedException {
        return get("/" + index + "/_count");
    }

    public long countValue(String index) throws IOException, InterruptedException {
        String json = count(index);
        String value = jsonField(json, "count");
        if (value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    public String catIndices() throws IOException, InterruptedException {
        return get("/_cat/indices?format=json");
    }

    public String nodesInfo() throws IOException, InterruptedException {
        return get("/_nodes");
    }

    public String nodesStats() throws IOException, InterruptedException {
        return get("/_nodes/stats");
    }

    public String putIndexTemplate(String name, String json) throws IOException, InterruptedException {
        return put("/_index_template/" + name, json);
    }

    public String getIndexTemplate(String name) throws IOException, InterruptedException {
        return get("/_index_template/" + name);
    }

    public String deleteIndexTemplate(String name) throws IOException, InterruptedException {
        return delete("/_index_template/" + name);
    }

    public String bulk(String ndjson) throws IOException, InterruptedException {
        return requestNdjson("/_bulk", ndjson).body();
    }

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

    private HttpClient httpClient() {
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
