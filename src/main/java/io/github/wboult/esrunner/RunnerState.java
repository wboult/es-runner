package io.github.wboult.esrunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Serializable snapshot of a running cluster instance recorded in the working
 * directory.
 */
public final class RunnerState {
    private final long pid;
    private final int httpPort;
    private final String clusterName;
    private final String version;
    private final Instant startTime;
    private final Path workDir;
    private final String baseUri;

    /**
     * Creates a state snapshot.
     *
     * @param pid Elasticsearch server PID
     * @param httpPort bound HTTP port
     * @param clusterName configured cluster name
     * @param version resolved Elasticsearch version
     * @param startTime server start time
     * @param workDir working directory containing extracted state
     * @param baseUri base HTTP URI for the cluster
     */
    public RunnerState(long pid,
                       int httpPort,
                       String clusterName,
                       String version,
                       Instant startTime,
                       Path workDir,
                       String baseUri) {
        this.pid = pid;
        this.httpPort = httpPort;
        this.clusterName = Objects.requireNonNull(clusterName, "clusterName");
        this.version = Objects.requireNonNull(version, "version");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.workDir = Objects.requireNonNull(workDir, "workDir");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
    }

    /**
     * Returns the Elasticsearch server PID.
     *
     * @return server PID
     */
    public long pid() {
        return pid;
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
     * Returns the configured cluster name.
     *
     * @return cluster name
     */
    public String clusterName() {
        return clusterName;
    }

    /**
     * Returns the resolved Elasticsearch version.
     *
     * @return version number
     */
    public String version() {
        return version;
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
     * Returns the working directory that owns the state snapshot.
     *
     * @return working directory
     */
    public Path workDir() {
        return workDir;
    }

    /**
     * Returns the cluster base URI.
     *
     * @return base URI
     */
    public String baseUri() {
        return baseUri;
    }

    /**
     * Writes the snapshot to a JSON state file used for operational inspection.
     *
     * @param stateFile target state file
     * @throws IOException if the file cannot be written
     */
    public void write(Path stateFile) throws IOException {
        Files.createDirectories(stateFile.getParent());
        String json = "{"
                + "\"pid\":" + pid + ","
                + "\"httpPort\":" + httpPort + ","
                + "\"clusterName\":\"" + JsonUtils.escape(clusterName) + "\","
                + "\"version\":\"" + JsonUtils.escape(version) + "\","
                + "\"startTime\":\"" + JsonUtils.escape(startTime.toString()) + "\","
                + "\"workDir\":\"" + JsonUtils.escape(workDir.toString()) + "\","
                + "\"baseUri\":\"" + JsonUtils.escape(baseUri) + "\""
                + "}";
        Files.writeString(stateFile, json, StandardCharsets.UTF_8);
    }

    /**
     * Reads a previously written state snapshot from disk.
     *
     * @param stateFile JSON state file
     * @return parsed runner state
     * @throws IOException if the file cannot be read
     */
    public static RunnerState read(Path stateFile) throws IOException {
        String json = Files.readString(stateFile, StandardCharsets.UTF_8);
        Map<String, String> values = JsonUtils.parseFlatJson(json);
        long pid = Long.parseLong(values.getOrDefault("pid", "0"));
        if (pid <= 0) {
            throw new IOException("Invalid PID in state file " + stateFile + ": " + pid);
        }
        int httpPort = Integer.parseInt(values.getOrDefault("httpPort", "0"));
        if (httpPort <= 0 || httpPort > 65535) {
            throw new IOException("Invalid HTTP port in state file " + stateFile + ": " + httpPort);
        }
        return new RunnerState(
                pid,
                httpPort,
                values.getOrDefault("clusterName", "unknown"),
                values.getOrDefault("version", "unknown"),
                Instant.parse(values.getOrDefault("startTime", Instant.EPOCH.toString())),
                Path.of(values.getOrDefault("workDir", ".")),
                values.getOrDefault("baseUri", "")
        );
    }
}
