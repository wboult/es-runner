package com.elastic.runner;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
