package com.elastic.runner;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticServerStopTest {

    @Test
    void stopWithResultGracefullyStopsAndCleansFiles() throws IOException {
        Path tempDir = Files.createTempDirectory("elastic-server-stop-graceful");
        StubProcess process = StubProcess.graceful();
        ElasticServer server = serverFor(process, tempDir);

        StopResult result = server.stopWithResult(Duration.ofMillis(10));

        assertTrue(result.wasRunning());
        assertTrue(result.graceful());
        assertFalse(result.forced());
        assertTrue(process.destroyCalled);
        assertFalse(process.destroyForciblyCalled);
        assertFalse(Files.exists(tempDir.resolve("es.pid")));
        assertFalse(Files.exists(tempDir.resolve("state.json")));
    }

    @Test
    void stopWithResultForciblyStopsWhenGracefulShutdownTimesOut() throws IOException {
        Path tempDir = Files.createTempDirectory("elastic-server-stop-forced");
        StubProcess process = StubProcess.forceOnly();
        ElasticServer server = serverFor(process, tempDir);

        StopResult result = server.stopWithResult(Duration.ofMillis(10));

        assertTrue(result.wasRunning());
        assertFalse(result.graceful());
        assertTrue(result.forced());
        assertTrue(process.destroyCalled);
        assertTrue(process.destroyForciblyCalled);
        assertFalse(Files.exists(tempDir.resolve("es.pid")));
        assertFalse(Files.exists(tempDir.resolve("state.json")));
    }

    @Test
    void stopWithResultCleansFilesWhenProcessAlreadyExited() throws IOException {
        Path tempDir = Files.createTempDirectory("elastic-server-stop-dead");
        StubProcess process = StubProcess.alreadyExited();
        ElasticServer server = serverFor(process, tempDir);

        StopResult result = server.stopWithResult(Duration.ofMillis(10));

        assertFalse(result.wasRunning());
        assertFalse(result.graceful());
        assertFalse(result.forced());
        assertFalse(process.destroyCalled);
        assertFalse(Files.exists(tempDir.resolve("es.pid")));
        assertFalse(Files.exists(tempDir.resolve("state.json")));
    }

    private static ElasticServer serverFor(StubProcess process, Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("runner.log");
        Path pidFile = tempDir.resolve("es.pid");
        Path stateFile = tempDir.resolve("state.json");
        Files.writeString(pidFile, "123");
        Files.writeString(stateFile, "{}");

        ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
                .version("9.2.4")
                .workDir(tempDir)
                .shutdownTimeout(Duration.ofSeconds(1)));

        return new ElasticServer(
                config,
                process,
                tempDir.resolve("home"),
                tempDir,
                logFile,
                pidFile,
                stateFile,
                123L,
                9200,
                HttpClient.newHttpClient(),
                Instant.now(),
                new Thread(() -> {
                })
        );
    }

    private static final class StubProcess extends Process {
        private boolean alive;
        private final boolean gracefulOnWait;
        private boolean destroyCalled;
        private boolean destroyForciblyCalled;

        private StubProcess(boolean alive, boolean gracefulOnWait) {
            this.alive = alive;
            this.gracefulOnWait = gracefulOnWait;
        }

        static StubProcess graceful() {
            return new StubProcess(true, true);
        }

        static StubProcess forceOnly() {
            return new StubProcess(true, false);
        }

        static StubProcess alreadyExited() {
            return new StubProcess(false, false);
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            alive = false;
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            if (destroyForciblyCalled) {
                alive = false;
                return true;
            }
            if (destroyCalled && gracefulOnWait) {
                alive = false;
                return true;
            }
            return false;
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException("still running");
            }
            return 0;
        }

        @Override
        public void destroy() {
            destroyCalled = true;
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalled = true;
            alive = false;
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
