package io.github.wboult.esrunner;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessTreeTest {

    @Test
    void terminatesWindowsChildProcessTree() throws IOException {
        Assumptions.assumeTrue(Os.isWindows(), "Windows-specific process-tree coverage");

        Process process = new ProcessBuilder("cmd.exe", "/c", "ping -n 300 127.0.0.1 > nul")
                .redirectErrorStream(true)
                .start();

        try {
            ProcessHandle child = awaitChild(process);

            ProcessTree.Termination termination = ProcessTree.terminate(process, child.pid(), Duration.ofSeconds(3));

            assertTrue(termination.hadAliveProcesses());
            assertFalse(process.isAlive());
            assertFalse(child.isAlive());
        } finally {
            ProcessTree.terminate(process, process.pid(), Duration.ofSeconds(1));
        }
    }

    private static ProcessHandle awaitChild(Process process) {
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)) {
            Optional<ProcessHandle> child = process.toHandle().descendants()
                    .filter(ProcessHandle::isAlive)
                    .findFirst();
            if (child.isPresent()) {
                return child.get();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("Expected a child process for " + process.pid());
    }
}
