package io.github.wboult.esrunner;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunnerStateTest {

    @Test
    void writesAndReadsState() throws Exception {
        Path tempDir = Files.createTempDirectory("es-state-test");
        Path stateFile = tempDir.resolve("state.json");

        RunnerState state = new RunnerState(
                1234L,
                9201,
                "unit-cluster",
                "9.2.4",
                Instant.parse("2026-01-01T00:00:00Z"),
                tempDir,
                "http://localhost:9201/"
        );
        state.write(stateFile);

        RunnerState read = RunnerState.read(stateFile);
        assertEquals(state.pid(), read.pid());
        assertEquals(state.httpPort(), read.httpPort());
        assertEquals(state.clusterName(), read.clusterName());
        assertEquals(state.version(), read.version());
        assertEquals(state.startTime(), read.startTime());
        assertEquals(state.workDir(), read.workDir());
        assertEquals(state.baseUri(), read.baseUri());
    }

    @Test
    void wrapsInvalidPortValuesAsIoExceptions() throws Exception {
        Path tempDir = Files.createTempDirectory("es-state-test");
        Path stateFile = tempDir.resolve("state.json");
        Files.writeString(stateFile, """
                {"pid":1234,"httpPort":"nope","clusterName":"test","version":"9.3.1","startTime":"2026-01-01T00:00:00Z","workDir":".","baseUri":"http://localhost:9200/"}
                """);

        assertThrows(java.io.IOException.class, () -> RunnerState.read(stateFile));
    }
}
