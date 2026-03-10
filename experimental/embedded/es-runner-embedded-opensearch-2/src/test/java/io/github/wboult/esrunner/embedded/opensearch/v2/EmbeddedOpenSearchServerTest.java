package io.github.wboult.esrunner.embedded.opensearch.v2;

import io.github.wboult.esrunner.ElasticServerHandle;
import io.github.wboult.esrunner.ElasticServerType;
import io.github.wboult.esrunner.embedded.EmbeddedElasticServerConfig;
import io.github.wboult.esrunner.embedded.EmbeddedHome;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EmbeddedOpenSearchServerTest {

    private static final String ENV_OS_HOME = "OPENSEARCH_EMBEDDED_HOME_2";

    private static Path openSearchHome() {
        String home = System.getenv(ENV_OS_HOME);
        assumeTrue(home != null && !home.isBlank(),
                ENV_OS_HOME + " not set - skipping embedded OpenSearch 2 tests. "
                        + "Set " + ENV_OS_HOME + " to the root of an extracted "
                        + "OpenSearch 2.19.x distribution.");
        return Path.of(home);
    }

    @Test
    void nodeStartsWithConfigAndSharedHandleSurface() throws Exception {
        Path workDir = newWorkDir("config");
        try {
            EmbeddedElasticServerConfig config = EmbeddedOpenSearchServer.defaultConfig(openSearchHome())
                    .toBuilder()
                    .workDir(workDir)
                    .clusterName("embedded-os2-config-test")
                    .nodeName("embedded-os2-config-node")
                    .portRangeStart(9510)
                    .portRangeEnd(9520)
                    .startupTimeout(Duration.ofSeconds(90))
                    .build();

            try (EmbeddedOpenSearchServer server = EmbeddedOpenSearchServer.start(config)) {
                assertEquals(ElasticServerType.EMBEDDED, server.type());
                assertEquals(config, server.config());
                assertTrue(server.httpPort() >= 9510 && server.httpPort() <= 9520);
                assertTrue(server.ping());
                assertEquals("embedded-os2-config-test", server.info().clusterName());
                assertNotNull(server.client());
                assertTrue(Files.isDirectory(server.workDir()));
                assertTrue(Files.isDirectory(server.dataDir()));
                assertTrue(Files.isDirectory(server.logsDir()));
                assertTrue(Files.isDirectory(server.stagedHome()));
            }
        } finally {
            cleanupWorkDir(workDir);
        }
    }

    @Test
    void basicIndexAndSearchWorksThroughSharedServerHandle() throws Exception {
        Path workDir = newWorkDir("search");
        try {
            try (EmbeddedOpenSearchServer server = EmbeddedOpenSearchServer.start(builder -> builder
                    .esHome(openSearchHome())
                    .workDir(workDir))) {
                assertSharedCrudSearchFlow(server);
            }
        } finally {
            cleanupWorkDir(workDir);
        }
    }

    @Test
    void concurrentIndexingAndSearchRemainStable() throws Exception {
        Path workDir = newWorkDir("concurrency");
        try {
            try (EmbeddedOpenSearchServer server = EmbeddedOpenSearchServer.start(builder -> builder
                    .esHome(openSearchHome())
                    .workDir(workDir)
                    .clusterName("embedded-os2-concurrency"))) {

                String index = "concurrency-index";
                server.createIndex(index, "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0}}");

                ExecutorService executor = Executors.newFixedThreadPool(4);
                try {
                    List<Future<?>> futures = new ArrayList<>();
                    for (int worker = 0; worker < 4; worker++) {
                        final int workerId = worker;
                        futures.add(executor.submit(() -> {
                            try {
                                for (int doc = 0; doc < 5; doc++) {
                                    server.indexDocument(
                                            index,
                                            workerId + "-" + doc,
                                            "{\"title\":\"hello worker " + workerId + "\",\"worker\":" + workerId + "}");
                                }
                                assertTrue(server.search(index,
                                        "{\"query\":{\"match\":{\"title\":\"hello\"}},\"size\":1}")
                                        .contains("\"hits\""));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }));
                    }
                    for (Future<?> future : futures) {
                        future.get();
                    }
                } finally {
                    executor.shutdownNow();
                }

                server.refresh(index);
                assertEquals(20L, server.countValue(index));
                assertTrue(server.search(index, "{\"query\":{\"match\":{\"title\":\"worker 2\"}}}")
                        .contains("\"worker\":2"));
            }
        } finally {
            cleanupWorkDir(workDir);
        }
    }

    @Test
    void userCanTrimClasspathPluginSelection() throws Exception {
        Path workDir = newWorkDir("trimmed-plugins");
        try {
            EmbeddedElasticServerConfig config = EmbeddedOpenSearchServer.defaultConfig(openSearchHome())
                    .toBuilder()
                    .workDir(workDir)
                    .removeClasspathPlugin(EmbeddedOpenSearchServer.ANALYSIS_COMMON_PLUGIN)
                    .build();

            try (EmbeddedOpenSearchServer server = EmbeddedOpenSearchServer.start(config)) {
                assertTrue(server.config().classpathPlugins()
                        .contains(EmbeddedOpenSearchServer.ANALYSIS_COMMON_PLUGIN) == false);
                assertSharedCrudSearchFlow(server);
            }
        } finally {
            cleanupWorkDir(workDir);
        }
    }

    private static void assertSharedCrudSearchFlow(ElasticServerHandle server) throws Exception {
        String index = "test-index";
        server.createIndex(index, "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0}}");
        server.indexDocument(index, "1", "{\"title\":\"hello world\",\"value\":42}");
        server.refresh(index);

        assertTrue(server.indexExists(index));
        assertEquals(1L, server.countValue(index));
        assertTrue(server.search(index, "{\"query\":{\"match\":{\"title\":\"hello\"}}}").contains("hello world"));
    }

    private static Path newWorkDir(String name) throws Exception {
        return Files.createTempDirectory("es-runner-opensearch2-" + name + "-");
    }

    private static void cleanupWorkDir(Path workDir) {
        if (workDir != null) {
            EmbeddedHome.deleteTreeQuietly(workDir);
        }
    }
}
