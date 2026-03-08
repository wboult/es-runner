package io.github.wboult.esrunner;

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

/**
 * Handle for one running Elasticsearch process started by {@link ElasticRunner}.
 */
public final class ElasticServer implements AutoCloseable {
    private final ElasticRunnerConfig config;
    private final Process process;
    private final Path homeDir;
    private final Path workDir;
    private final Path logFile;
    private final Path pidFile;
    private final Path stateFile;
    private final long serverPid;
    private final int httpPort;
    private final URI baseUri;
    private final HttpClient httpClient;
    private final ElasticClient client;
    private final Instant startTime;
    private final Thread logThread;
    private final Thread shutdownHook;

    ElasticServer(ElasticRunnerConfig config,
                  Process process,
                  Path homeDir,
                  Path workDir,
                  Path logFile,
                  Path pidFile,
                  Path stateFile,
                  long serverPid,
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
        this.serverPid = serverPid;
        this.httpPort = httpPort;
        this.baseUri = URI.create("http://localhost:" + httpPort + "/");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.client = new ElasticClient(baseUri, httpClient);
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.logThread = Objects.requireNonNull(logThread, "logThread");

        this.shutdownHook = new Thread(() -> {
            if (ProcessTree.isAlive(process, serverPid)) {
                try {
                    ProcessTree.terminate(process, serverPid, Duration.ofSeconds(10));
                } catch (RuntimeException ignored) {
                    // Best effort during JVM shutdown.
                } finally {
                    cleanup();
                }
            }
        }, "elastic-runner-shutdown");
        Runtime.getRuntime().addShutdownHook(this.shutdownHook);
    }

    /**
     * Returns the immutable configuration used to start the server.
     *
     * @return runner configuration
     */
    public ElasticRunnerConfig config() {
        return config;
    }

    /**
     * Returns the extracted Elasticsearch home directory.
     *
     * @return Elasticsearch home directory
     */
    public Path homeDir() {
        return homeDir;
    }

    /**
     * Returns the version-specific working directory that stores state, logs,
     * and extracted content.
     *
     * @return working directory
     */
    public Path workDir() {
        return workDir;
    }

    /**
     * Returns the log file populated by the process gobbler.
     *
     * @return log file path
     */
    public Path logFile() {
        return logFile;
    }

    /**
     * Returns the PID file written by the Elasticsearch launcher.
     *
     * @return PID file path
     */
    public Path pidFile() {
        return pidFile;
    }

    /**
     * Returns the JSON state file written by ES Runner.
     *
     * @return state file path
     */
    public Path stateFile() {
        return stateFile;
    }

    /**
     * Returns the bound HTTP port.
     *
     * @return HTTP port
     */
    public int httpPort() {
        return httpPort;
    }

    /**
     * Returns the cluster base URI.
     *
     * @return base URI
     */
    public URI baseUri() {
        return baseUri;
    }

    /**
     * Returns a reusable client bound to this server's base URI.
     *
     * @return cluster client
     */
    public ElasticClient client() {
        return client;
    }

    /**
     * Returns whether the underlying process tree still appears alive.
     *
     * @return {@code true} when the server is still running
     */
    public boolean isRunning() {
        return ProcessTree.isAlive(process, serverPid);
    }

    /**
     * Returns the Elasticsearch server PID.
     *
     * @return server PID
     */
    public long pid() {
        return serverPid;
    }

    /**
     * Returns the recorded start time.
     *
     * @return start time
     */
    public Instant startTime() {
        return startTime;
    }

    /**
     * Returns the last 20 lines of the server log.
     *
     * @return log tail
     */
    public String logTail() {
        return LogTail.read(logFile, 20);
    }

    /**
     * Returns the last {@code maxLines} of the server log.
     *
     * @param maxLines maximum lines to return
     * @return log tail
     */
    public String logTail(int maxLines) {
        return LogTail.read(logFile, maxLines);
    }

    /**
     * Returns the raw root endpoint response.
     *
     * @return root JSON response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String root() throws IOException, InterruptedException {
        return client.get("/");
    }

    /**
     * Returns typed cluster info.
     *
     * @return cluster info
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public ClusterInfoResponse info() throws IOException, InterruptedException {
        return client.info();
    }

    /**
     * Returns the cluster name.
     *
     * @return cluster name
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String clusterName() throws IOException, InterruptedException {
        return client.clusterName();
    }

    /**
     * Returns the Elasticsearch version.
     *
     * @return version number
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String version() throws IOException, InterruptedException {
        return client.version();
    }

    /**
     * Returns typed cluster health information.
     *
     * @return cluster health
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public ClusterHealthResponse clusterHealth() throws IOException, InterruptedException {
        return client.clusterHealth();
    }

    /**
     * Returns the current cluster health status string.
     *
     * @return cluster health status
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String clusterHealthStatus() throws IOException, InterruptedException {
        return client.clusterHealthStatus();
    }

    /**
     * Waits until the cluster reaches at least the requested health status.
     *
     * @param status requested health status
     * @param timeout maximum wait time
     * @return {@code true} if the status was reached
     * @throws IOException if a final request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean waitForStatus(String status, Duration timeout) throws IOException, InterruptedException {
        return client.waitForStatus(status, timeout);
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
     * Checks whether the cluster responds at the root endpoint.
     *
     * @return {@code true} when reachable
     */
    public boolean ping() {
        return client.ping();
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
        return client.get(path);
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
        return client.post(path, body);
    }

    /**
     * Performs a PUT request with a JSON body and returns the response body.
     *
     * @param path relative or absolute cluster path
     * @param body JSON request body, or {@code null}
     * @return response body
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String put(String path, String body) throws IOException, InterruptedException {
        return client.put(path, body);
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
        return client.delete(path);
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
        return client.createIndex(index);
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
        return client.createIndex(index, json);
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
        return client.indexExists(index);
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
        return client.deleteIndex(index);
    }

    /**
     * Refreshes an index.
     *
     * @param index index name
     * @return raw Elasticsearch response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String refresh(String index) throws IOException, InterruptedException {
        return client.refresh(index);
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
        return client.indexDocument(index, id, json);
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
        return client.search(index, jsonQuery);
    }

    /**
     * Returns the raw count response for an index.
     *
     * @param index index name
     * @return raw count response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String count(String index) throws IOException, InterruptedException {
        return client.count(index);
    }

    /**
     * Returns the numeric document count for an index.
     *
     * @param index index name
     * @return document count
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public long countValue(String index) throws IOException, InterruptedException {
        return client.countValue(index);
    }

    /**
     * Returns the JSON-formatted cat indices output.
     *
     * @return cat indices response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String catIndices() throws IOException, InterruptedException {
        return client.catIndices();
    }

    /**
     * Returns node info for the cluster.
     *
     * @return node info response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String nodesInfo() throws IOException, InterruptedException {
        return client.nodesInfo();
    }

    /**
     * Returns node stats for the cluster.
     *
     * @return node stats response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
    public String nodesStats() throws IOException, InterruptedException {
        return client.nodesStats();
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
        return client.putIndexTemplate(name, json);
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
        return client.getIndexTemplate(name);
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
        return client.deleteIndexTemplate(name);
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
        return client.bulk(ndjson);
    }

    /**
     * Executes a raw HTTP request against the cluster using this server's shared
     * HTTP client.
     *
     * @param method HTTP method
     * @param path relative or absolute cluster path
     * @param body JSON request body, or {@code null}
     * @return raw JDK HTTP response
     * @throws IOException if the request fails
     * @throws InterruptedException if interrupted while waiting
     */
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

    /**
     * Returns an operational snapshot of the running server.
     *
     * @return current runner state
     */
    public RunnerState state() {
        String version = config.version() != null ? config.version() : DistroVersion.fromZip(config.distroZip());
        return new RunnerState(
                serverPid,
                httpPort,
                config.clusterName(),
                version,
                startTime,
                workDir,
                baseUri.toString()
        );
    }

    /**
     * Stops the server using the configured shutdown timeout.
     */
    public void stop() {
        stopWithResult(config.shutdownTimeout());
    }

    /**
     * Stops the server using the provided shutdown timeout.
     *
     * @param timeout shutdown timeout
     */
    public void stop(Duration timeout) {
        stopWithResult(timeout);
    }

    /**
     * Stops the server and returns a structured shutdown result.
     *
     * @return shutdown outcome
     */
    public StopResult stopWithResult() {
        return stopWithResult(config.shutdownTimeout());
    }

    /**
     * Stops the server and returns a structured shutdown result.
     *
     * @param timeout shutdown timeout
     * @return shutdown outcome
     */
    public StopResult stopWithResult(Duration timeout) {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is shutting down
        }

        boolean wasRunning = isRunning();
        if (!wasRunning) {
            cleanup();
            stopLogThread(Duration.ofSeconds(1));
            return new StopResult(false, false, false, Duration.ZERO);
        }

        Instant waitStart = Instant.now();
        boolean graceful;
        boolean forced;
        try {
            ProcessTree.Termination termination = ProcessTree.terminate(process, serverPid, timeout);
            graceful = termination.graceful();
            forced = termination.forced();
        } finally {
            cleanup();
            stopLogThread(Duration.ofSeconds(2));
        }

        Duration waited = Duration.between(waitStart, Instant.now());
        return new StopResult(true, graceful, forced, waited);
    }

    /**
     * Equivalent to {@link #stop()}.
     */
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
