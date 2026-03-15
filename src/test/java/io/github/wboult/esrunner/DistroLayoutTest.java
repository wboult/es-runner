package io.github.wboult.esrunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistroLayoutTest {
    @Test
    void prepareIsSafeUnderConcurrentExtraction(@TempDir Path tempDir) throws Exception {
        Path archive = TestArchives.zipWithEntries(
                tempDir.resolve("elasticsearch.zip"),
                Map.of(
                        "elasticsearch-9.3.1/bin/" + Os.executableName("elasticsearch"), "echo start",
                        "elasticsearch-9.3.1/config/elasticsearch.yml", "cluster.name: concurrent"
                )
        );
        Path workDir = tempDir.resolve("work");
        int callers = 6;
        CyclicBarrier barrier = new CyclicBarrier(callers);

        Callable<Path> task = () -> {
            barrier.await();
            return DistroLayout.prepare(archive, workDir, DistroFamily.ELASTICSEARCH);
        };

        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Path>> futures = pool.invokeAll(java.util.Collections.nCopies(callers, task));
            Set<Path> homes = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toSet());

            assertEquals(1, homes.size());
            Path home = homes.iterator().next();
            assertTrue(Files.exists(home.resolve("bin").resolve(Os.executableName("elasticsearch"))));
            try (var children = Files.list(workDir.resolve("distro"))) {
                assertEquals(
                        List.of("elasticsearch-9.3.1"),
                        children
                                .filter(Files::isDirectory)
                                .map(path -> path.getFileName().toString())
                                .sorted()
                                .toList()
                );
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
