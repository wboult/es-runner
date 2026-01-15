package com.elastic.runner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class ElasticServer implements AutoCloseable {
    private final ElasticRunnerConfig config;
    private final Process process;
    private final Path homeDir;
    private final Path workDir;
    private final Path logFile;
    private final Path pidFile;
    private final Path stateFile;
    private final int httpPort;
    private final URI baseUri;
    private final HttpClient httpClient;
    private final Instant startTime;

    ElasticServer(ElasticRunnerConfig config,
                  Process process,
                  Path homeDir,
                  Path workDir,
                  Path logFile,
                  Path pidFile,
                  Path stateFile,
                  int httpPort,
                  HttpClient httpClient,
                  Instant startTime) {
        this.config = Objects.requireNonNull(config, "config");
        this.process = Objects.requireNonNull(process, "process");
        this.homeDir = Objects.requireNonNull(homeDir, "homeDir");
        this.workDir = Objects.requireNonNull(workDir, "workDir");
        this.logFile = Objects.requireNonNull(logFile, "logFile");
        this.pidFile = Objects.requireNonNull(pidFile, "pidFile");
        this.stateFile = Objects.requireNonNull(stateFile, "stateFile");
        this.httpPort = httpPort;
        this.baseUri = URI.create("http://localhost:" + httpPort + "/");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
    }

    public ElasticRunnerConfig config() {
        return config;
    }

    public Path homeDir() {
        return homeDir;
    }

    public Path workDir() {
        return workDir;
    }

    public Path logFile() {
        return logFile;
    }

    public int httpPort() {
        return httpPort;
    }

    public URI baseUri() {
        return baseUri;
    }

    public boolean isRunning() {
        return process.isAlive();
    }

    public Instant startTime() {
        return startTime;
    }

    public String root() throws IOException, InterruptedException {
        return get("/");
    }

    public String info() throws IOException, InterruptedException {
        return root();
    }

    public String clusterName() throws IOException, InterruptedException {
        return jsonField(info(), "cluster_name");
    }

    public String version() throws IOException, InterruptedException {
        return jsonField(info(), "number");
    }

    public String clusterHealth() throws IOException, InterruptedException {
        return get("/_cluster/health");
    }

    public String clusterHealthStatus() throws IOException, InterruptedException {
        return jsonField(clusterHealth(), "status");
    }

    public boolean waitForStatus(String status, Duration timeout) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        String expected = "\"status\":\"" + status + "\"";
        while (Instant.now().isBefore(deadline)) {
            if (!isRunning()) {
                return false;
            }
            String health = clusterHealth();
            if (health.contains(expected)) {
                return true;
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

    public boolean ping() {
        try {
            return request("GET", "/", null).statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
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

    public String createIndex(String index) throws IOException, InterruptedException {
        return put("/" + index, null);
    }

    public String createIndex(String index, String json) throws IOException, InterruptedException {
        return put("/" + index, json);
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

    public String bulk(String ndjson) throws IOException, InterruptedException {
        return requestNdjson("/_bulk", ndjson).body();
    }

    public HttpResponse<String> request(String method, String path, String body)
            throws IOException, InterruptedException {
        URI uri = baseUri.resolve(path.startsWith("/") ? path.substring(1) : path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> requestNdjson(String path, String ndjson)
            throws IOException, InterruptedException {
        URI uri = baseUri.resolve(path.startsWith("/") ? path.substring(1) : path);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofString(ndjson))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public RunnerState state() {
        String version = config.version() != null ? config.version() : DistroVersion.fromZip(config.distroZip());
        return new RunnerState(
                process.pid(),
                httpPort,
                config.clusterName(),
                version,
                startTime,
                workDir,
                baseUri.toString()
        );
    }

    public void stop() {
        stop(config.shutdownTimeout());
    }

    public void stop(Duration timeout) {
        if (!process.isAlive()) {
            cleanup();
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cleanup();
        }
    }

    @Override
    public void close() {
        stop();
    }

    private void cleanup() {
        deleteIfExists(pidFile);
        deleteIfExists(stateFile);
    }

    private static void deleteIfExists(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    private static String jsonField(String json, String key) {
        return JsonUtils.parseFlatJson(json).getOrDefault(key, "");
    }
}
