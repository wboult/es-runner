package com.elastic.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class RunnerState {
    private final long pid;
    private final int httpPort;
    private final String clusterName;
    private final String version;
    private final Instant startTime;
    private final Path workDir;
    private final String baseUri;

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

    public long pid() {
        return pid;
    }

    public int httpPort() {
        return httpPort;
    }

    public String clusterName() {
        return clusterName;
    }

    public String version() {
        return version;
    }

    public Instant startTime() {
        return startTime;
    }

    public Path workDir() {
        return workDir;
    }

    public String baseUri() {
        return baseUri;
    }

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

    public static RunnerState read(Path stateFile) throws IOException {
        String json = Files.readString(stateFile, StandardCharsets.UTF_8);
        Map<String, String> values = JsonUtils.parseFlatJson(json);
        return new RunnerState(
                Long.parseLong(values.getOrDefault("pid", "0")),
                Integer.parseInt(values.getOrDefault("httpPort", "0")),
                values.getOrDefault("clusterName", "unknown"),
                values.getOrDefault("version", "unknown"),
                Instant.parse(values.getOrDefault("startTime", Instant.EPOCH.toString())),
                Path.of(values.getOrDefault("workDir", ".")),
                values.getOrDefault("baseUri", "")
        );
    }
}
