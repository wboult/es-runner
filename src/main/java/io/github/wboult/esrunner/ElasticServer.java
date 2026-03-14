package io.github.wboult.esrunner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Handle for one running Elasticsearch process started by {@link ElasticRunner}.
 */
public final class ElasticServer implements ElasticServerHandle {
    private final ElasticProcessRuntime runtime;
    private final int httpPort;
    private final URI baseUri;
    private final HttpClient httpClient;
    private final ElasticClient client;

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
        this(
                new ElasticProcessRuntime(
                        config,
                        process,
                        homeDir,
                        workDir,
                        logFile,
                        pidFile,
                        stateFile,
                        serverPid,
                        resolveVersion(config),
                        startTime,
                        logThread
                ),
                httpPort,
                httpClient
        );
    }

    ElasticServer(ElasticProcessRuntime runtime, int httpPort, HttpClient httpClient) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.httpPort = httpPort;
        this.baseUri = URI.create("http://localhost:" + httpPort + "/");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.client = new ElasticClient(
                baseUri,
                runtime.config().requestTimeout(),
                runtime.config().bulkTimeout(),
                httpClient
        );
    }

    /**
     * Returns the immutable configuration used to start the server.
     *
     * @return runner configuration
     */
    public ElasticRunnerConfig config() {
        return runtime.config();
    }

    @Override
    public ElasticServerType type() {
        return ElasticServerType.PROCESS;
    }

    /**
     * Returns the extracted Elasticsearch home directory.
     *
     * @return Elasticsearch home directory
     */
    public Path homeDir() {
        return runtime.homeDir();
    }

    /**
     * Returns the version-specific working directory that stores state, logs,
     * and extracted content.
     *
     * @return working directory
     */
    public Path workDir() {
        return runtime.workDir();
    }

    /**
     * Returns the log file populated by the process gobbler.
     *
     * @return log file path
     */
    public Path logFile() {
        return runtime.logFile();
    }

    /**
     * Returns the PID file written by the Elasticsearch launcher.
     *
     * @return PID file path
     */
    public Path pidFile() {
        return runtime.pidFile();
    }

    /**
     * Returns the JSON state file written by ES Runner.
     *
     * @return state file path
     */
    public Path stateFile() {
        return runtime.stateFile();
    }

    /**
     * Returns the bound HTTP port.
     *
     * @return HTTP port
     */
    @Override
    public int httpPort() {
        return httpPort;
    }

    /**
     * Returns the cluster base URI.
     *
     * @return base URI
     */
    @Override
    public URI baseUri() {
        return baseUri;
    }

    /**
     * Returns a reusable client bound to this server's base URI.
     *
     * @return cluster client
     */
    @Override
    public ElasticClient client() {
        return client;
    }

    /**
     * Returns whether the underlying process tree still appears alive.
     *
     * @return {@code true} when the server is still running
     */
    public boolean isRunning() {
        return runtime.isRunning();
    }

    /**
     * Returns the Elasticsearch server PID.
     *
     * @return server PID
     */
    public long pid() {
        return runtime.pid();
    }

    /**
     * Returns the recorded start time.
     *
     * @return start time
     */
    public Instant startTime() {
        return runtime.startTime();
    }

    /**
     * Returns the last 20 lines of the server log.
     *
     * @return log tail
     */
    public String logTail() {
        return runtime.logTail();
    }

    /**
     * Returns the last {@code maxLines} of the server log.
     *
     * @param maxLines maximum lines to return
     * @return log tail
     */
    public String logTail(int maxLines) {
        return runtime.logTail(maxLines);
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
                .timeout(runtime.config().requestTimeout());
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Returns an operational snapshot of the running server.
     *
     * @return current runner state
     */
    public RunnerState state() {
        return runtime.state(httpPort, baseUri);
    }

    /**
     * Stops the server using the configured shutdown timeout.
     */
    public void stop() {
        runtime.stopWithResult(config().shutdownTimeout());
    }

    /**
     * Stops the server using the provided shutdown timeout.
     *
     * @param timeout shutdown timeout
     */
    public void stop(Duration timeout) {
        runtime.stopWithResult(timeout);
    }

    /**
     * Stops the server and returns a structured shutdown result.
     *
     * @return shutdown outcome
     */
    public StopResult stopWithResult() {
        return runtime.stopWithResult(config().shutdownTimeout());
    }

    /**
     * Stops the server and returns a structured shutdown result.
     *
     * @param timeout shutdown timeout
     * @return shutdown outcome
     */
    public StopResult stopWithResult(Duration timeout) {
        return runtime.stopWithResult(timeout);
    }

    /**
     * Equivalent to {@link #stop()}.
     */
    @Override
    public void close() {
        runtime.close();
    }

    private static String resolveVersion(ElasticRunnerConfig config) {
        if (config.version() != null && !config.version().isBlank()) {
            return config.version();
        }
        if (config.distroZip() != null) {
            return DistroVersion.fromZip(config.distroZip());
        }
        throw new IllegalArgumentException("ElasticServer requires config.version or config.distroZip");
    }
}
