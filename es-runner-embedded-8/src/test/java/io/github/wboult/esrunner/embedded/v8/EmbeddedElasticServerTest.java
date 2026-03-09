package io.github.wboult.esrunner.embedded.v8;

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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EmbeddedElasticServerTest {

    private static final String ENV_ES_HOME = "ES_EMBEDDED_HOME_8";

    private static Path esHome() {
        String home = System.getenv(ENV_ES_HOME);
        if (home == null || home.isBlank()) {
            home = System.getenv("ES_EMBEDDED_HOME");
        }
        assumeTrue(home != null && !home.isBlank(),
                ENV_ES_HOME + " not set - skipping embedded ES 8 tests. "
                        + "Set " + ENV_ES_HOME + " to the root of an extracted "
                        + "Elasticsearch 8.19.x distribution.");
        return Path.of(home);
    }

    @Test
    void nodeStartsWithConfigAndSharedHandleSurface() throws Exception {
        Path workDir = newWorkDir("config");
        try {
            EmbeddedElasticServerConfig config = EmbeddedElasticServer.defaultConfig(esHome())
                    .toBuilder()
                    .workDir(workDir)
                    .clusterName("embedded8-config-test")
                    .nodeName("embedded8-config-node")
                    .portRangeStart(9310)
                    .portRangeEnd(9320)
                    .startupTimeout(Duration.ofSeconds(90))
                    .build();

            try (EmbeddedElasticServer server = EmbeddedElasticServer.start(config)) {
                assertEquals(ElasticServerType.EMBEDDED, server.type());
                assertEquals(config, server.config());
                assertTrue(server.httpPort() >= 9310 && server.httpPort() <= 9320);
                assertTrue(server.ping());
                assertEquals("embedded8-config-test", server.info().clusterName());
                assertEquals("embedded8-config-test", server.client().clusterName());
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
            try (EmbeddedElasticServer server = EmbeddedElasticServer.start(builder -> builder
                    .esHome(esHome())
                    .workDir(workDir))) {
                assertSharedCrudSearchFlow(server);
            }
        } finally {
            cleanupWorkDir(workDir);
        }
    }

    @Test
    void repeatedStartStopWorksInOneJvm() throws Exception {
        Path esHome = esHome();
        Path root = newWorkDir("restart");
        try {
            for (int i = 0; i < 3; i++) {
                final int run = i;
                Path workDir = root.resolve("run-" + run);
                try {
                    try (EmbeddedElasticServer server = EmbeddedElasticServer.start(builder -> builder
                            .esHome(esHome)
                            .workDir(workDir)
                            .clusterName("embedded8-restart-" + run)
                            .nodeName("embedded8-node-" + run))) {
                        assertTrue(server.waitForYellow(Duration.ofSeconds(30)));
                        assertTrue(server.ping());
                        assertTrue(server.root().contains("\"cluster_name\""));
                    }
                } finally {
                    cleanupWorkDir(workDir);
                }
            }
        } finally {
            cleanupWorkDir(root);
        }
    }

    @Test
    void concurrentIndexingAndSearchRemainStable() throws Exception {
        Path workDir = newWorkDir("concurrency");
        try {
            try (EmbeddedElasticServer server = EmbeddedElasticServer.start(builder -> builder
                    .esHome(esHome())
                    .workDir(workDir)
                    .clusterName("embedded8-concurrency"))) {

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
                                    String id = workerId + "-" + doc;
                                    server.indexDocument(
                                            index,
                                            id,
                                            "{\"title\":\"hello worker " + workerId + "\",\"worker\":" + workerId + "}");
                                }
                                String search = server.search(index,
                                        "{\"query\":{\"match\":{\"title\":\"hello\"}},\"size\":1}");
                                assertTrue(search.contains("\"hits\""));
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
    void userCanTrimBundledModuleSelection() throws Exception {
        Path workDir = newWorkDir("trimmed");
        try {
            try (EmbeddedElasticServer server = EmbeddedElasticServer.start(builder -> builder
                    .esHome(esHome())
                    .workDir(workDir)
                    .useBundledProfile(EmbeddedElasticServer.profile())
                    .removeModules(Set.of(
                            "kibana",
                            "repository-s3",
                            "repository-gcs",
                            "repository-azure",
                            "repository-url")))) {

                assertTrue(Files.notExists(server.stagedHome().resolve("modules").resolve("kibana")));
                assertTrue(Files.notExists(server.stagedHome().resolve("modules").resolve("repository-s3")));
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
        return Files.createTempDirectory("es-runner-embedded8-" + name + "-");
    }

    private static void cleanupWorkDir(Path workDir) {
        if (workDir != null) {
            EmbeddedHome.deleteTreeQuietly(workDir);
        }
    }
}
