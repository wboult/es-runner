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
    private final ElasticClient client;
    private final Instant startTime;
    private final Thread logThread;

    ElasticServer(ElasticRunnerConfig config,
                  Process process,
                  Path homeDir,
                  Path workDir,
                  Path logFile,
                  Path pidFile,
                  Path stateFile,
                  int httpPort,
                  HttpClient httpClient,
                  Instant startTime,
                  Thread logThread) {
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
        this.client = new ElasticClient(baseUri, httpClient);
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.logThread = Objects.requireNonNull(logThread, "logThread");
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

    public Path pidFile() {
        return pidFile;
    }

    public Path stateFile() {
        return stateFile;
    }

    public int httpPort() {
        return httpPort;
    }

    public URI baseUri() {
        return baseUri;
    }

    public ElasticClient client() {
        return client;
    }

    public boolean isRunning() {
        return process.isAlive();
    }

    public long pid() {
        return process.pid();
    }

    public Instant startTime() {
        return startTime;
    }

    public String logTail() {
        return LogTail.read(logFile, 20);
    }

    public String logTail(int maxLines) {
        return LogTail.read(logFile, maxLines);
    }

    public String root() throws IOException, InterruptedException {
        return client.get("/");
    }

    public String info() throws IOException, InterruptedException {
        return client.info();
    }

    public String clusterName() throws IOException, InterruptedException {
        return client.clusterName();
    }

    public String version() throws IOException, InterruptedException {
        return client.version();
    }

    public String clusterHealth() throws IOException, InterruptedException {
        return client.clusterHealth();
    }

    public String clusterHealthStatus() throws IOException, InterruptedException {
        return client.clusterHealthStatus();
    }

    public boolean waitForStatus(String status, Duration timeout) throws IOException, InterruptedException {
        return client.waitForStatus(status, timeout);
    }

    public boolean waitForYellow(Duration timeout) throws IOException, InterruptedException {
        return waitForStatus("yellow", timeout);
    }

    public boolean waitForGreen(Duration timeout) throws IOException, InterruptedException {
        return waitForStatus("green", timeout);
    }

    public boolean ping() {
        return client.ping();
    }

    public String get(String path) throws IOException, InterruptedException {
        return client.get(path);
    }

    public String post(String path, String body) throws IOException, InterruptedException {
        return client.post(path, body);
    }

    public String put(String path, String body) throws IOException, InterruptedException {
        return client.put(path, body);
    }

    public String delete(String path) throws IOException, InterruptedException {
        return client.delete(path);
    }

    public String createIndex(String index) throws IOException, InterruptedException {
        return client.createIndex(index);
    }

    public String createIndex(String index, String json) throws IOException, InterruptedException {
        return client.createIndex(index, json);
    }

    public boolean indexExists(String index) throws IOException, InterruptedException {
        return client.indexExists(index);
    }

    public String deleteIndex(String index) throws IOException, InterruptedException {
        return client.deleteIndex(index);
    }

    public String refresh(String index) throws IOException, InterruptedException {
        return client.refresh(index);
    }

    public String indexDocument(String index, String id, String json) throws IOException, InterruptedException {
        return client.indexDocument(index, id, json);
    }

    public String search(String index, String jsonQuery) throws IOException, InterruptedException {
        return client.search(index, jsonQuery);
    }

    public String count(String index) throws IOException, InterruptedException {
        return client.count(index);
    }

    public long countValue(String index) throws IOException, InterruptedException {
        return client.countValue(index);
    }

    public String catIndices() throws IOException, InterruptedException {
        return client.catIndices();
    }

    public String nodesInfo() throws IOException, InterruptedException {
        return client.nodesInfo();
    }

    public String nodesStats() throws IOException, InterruptedException {
        return client.nodesStats();
    }

    public String putIndexTemplate(String name, String json) throws IOException, InterruptedException {
        return client.putIndexTemplate(name, json);
    }

    public String getIndexTemplate(String name) throws IOException, InterruptedException {
        return client.getIndexTemplate(name);
    }

    public String deleteIndexTemplate(String name) throws IOException, InterruptedException {
        return client.deleteIndexTemplate(name);
    }

    public String bulk(String ndjson) throws IOException, InterruptedException {
        return client.bulk(ndjson);
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
        stopWithResult(config.shutdownTimeout());
    }

    public void stop(Duration timeout) {
        stopWithResult(timeout);
    }

    public StopResult stopWithResult() {
        return stopWithResult(config.shutdownTimeout());
    }

    public StopResult stopWithResult(Duration timeout) {
        boolean wasRunning = process.isAlive();
        if (!wasRunning) {
            cleanup();
            stopLogThread(Duration.ofSeconds(1));
            return new StopResult(false, false, false, Duration.ZERO);
        }

        Instant waitStart = Instant.now();
        boolean graceful = false;
        boolean forced = false;
        process.destroy();
        try {
            if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                graceful = true;
            } else {
                process.destroyForcibly();
                forced = true;
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cleanup();
            stopLogThread(Duration.ofSeconds(2));
        }

        Duration waited = Duration.between(waitStart, Instant.now());
        return new StopResult(true, graceful, forced, waited);
    }

    @Override
    public void close() {
        stop();
    }

    private void cleanup() {
        deleteIfExists(pidFile);
        deleteIfExists(stateFile);
    }

    private void stopLogThread(Duration timeout) {
        if (!logThread.isAlive()) {
            return;
        }
        try {
            logThread.join(timeout.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
