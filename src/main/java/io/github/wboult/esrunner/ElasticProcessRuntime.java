package io.github.wboult.esrunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

final class ElasticProcessRuntime implements AutoCloseable {
    private final ElasticRunnerConfig config;
    private final Process process;
    private final Path homeDir;
    private final Path workDir;
    private final Path logFile;
    private final Path pidFile;
    private final Path stateFile;
    private final long serverPid;
    private final String version;
    private final Instant startTime;
    private final Thread logThread;
    private final Thread shutdownHook;

    ElasticProcessRuntime(ElasticRunnerConfig config,
                          Process process,
                          Path homeDir,
                          Path workDir,
                          Path logFile,
                          Path pidFile,
                          Path stateFile,
                          long serverPid,
                          String version,
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
        this.version = Objects.requireNonNull(version, "version");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.logThread = Objects.requireNonNull(logThread, "logThread");

        this.shutdownHook = new Thread(this::runShutdownHook, "es-runner-shutdown");
        Runtime.getRuntime().addShutdownHook(this.shutdownHook);
    }

    ElasticRunnerConfig config() {
        return config;
    }

    Path homeDir() {
        return homeDir;
    }

    Path workDir() {
        return workDir;
    }

    Path logFile() {
        return logFile;
    }

    Path pidFile() {
        return pidFile;
    }

    Path stateFile() {
        return stateFile;
    }

    boolean isRunning() {
        return ProcessTree.isAlive(process, serverPid);
    }

    long pid() {
        return serverPid;
    }

    Instant startTime() {
        return startTime;
    }

    String logTail() {
        return logTail(20);
    }

    String logTail(int maxLines) {
        return LogTail.read(logFile, maxLines);
    }

    RunnerState state(int httpPort, java.net.URI baseUri) {
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

    StopResult stopWithResult(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        removeShutdownHook();

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

        return new StopResult(true, graceful, forced, Duration.between(waitStart, Instant.now()));
    }

    @Override
    public void close() {
        stopWithResult(config.shutdownTimeout());
    }

    static void cleanupFailedStart(Process process,
                                   Path pidFile,
                                   Path stateFile,
                                   Thread logThread) {
        try {
            if (process != null) {
                long serverPid = readServerPid(pidFile).orElse(process.pid());
                terminateFailedStartProcess(process, serverPid, Duration.ofSeconds(10));
            }
            if (logThread != null && logThread.isAlive()) {
                logThread.join(TimeUnit.SECONDS.toMillis(2));
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            deleteIfExists(pidFile);
            deleteIfExists(stateFile);
        }
    }

    static OptionalLong readServerPid(Path pidFile) {
        if (!Files.exists(pidFile)) {
            return OptionalLong.empty();
        }
        try {
            String value = Files.readString(pidFile, StandardCharsets.UTF_8).trim();
            if (value.isEmpty()) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Long.parseLong(value));
        } catch (IOException | NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    private void runShutdownHook() {
        if (!ProcessTree.isAlive(process, serverPid)) {
            cleanup();
            stopLogThread(Duration.ofSeconds(1));
            return;
        }
        try {
            ProcessTree.terminate(process, serverPid, Duration.ofSeconds(10));
        } catch (RuntimeException ignored) {
            // Best effort during JVM shutdown.
        } finally {
            cleanup();
            stopLogThread(Duration.ofSeconds(1));
        }
    }

    private void removeShutdownHook() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is shutting down
        }
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

    private static void terminateFailedStartProcess(Process process, long serverPid, Duration timeout) {
        try {
            ProcessTree.terminate(process, serverPid, timeout);
        } catch (LinkageError ignored) {
            terminateDirect(process, timeout);
        }
    }

    private static void terminateDirect(Process process, Duration timeout) {
        if (!process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
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
}
