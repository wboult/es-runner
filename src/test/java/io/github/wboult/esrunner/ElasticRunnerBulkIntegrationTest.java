package io.github.wboult.esrunner;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticRunnerBulkIntegrationTest {

    @Tag("stress")
    @Test
    void indexesLargeDatasetAndCounts() throws Exception {
        String version = System.getenv().getOrDefault("ES_VERSION", "9.3.1");

        Path workDir = Files.createTempDirectory("es-runner-bulk-it-");
        ElasticRunnerConfig config = IntegrationTestSupport.config(
                version,
                workDir,
                "bulk-it",
                "512m",
                Duration.ofSeconds(180),
                true
        );

        try (ElasticServer server = ElasticRunner.start(config)) {
            assertTrue(server.ping());
            server.waitForYellow(Duration.ofSeconds(120));
            Thread.sleep(Duration.ofSeconds(5).toMillis());

            String index = "bulk-test";
            server.createIndex(index, """
                    {
                      "settings": {
                        "index": {
                          "number_of_replicas": 0,
                          "refresh_interval": "-1"
                        }
                      }
                    }
                    """);

            int total = 100_000;
            int batchSize = 250;
            Random random = new Random(42);

            for (int offset = 0; offset < total; offset += batchSize) {
                int limit = Math.min(total, offset + batchSize);
                StringBuilder ndjson = new StringBuilder((limit - offset) * 64);
                for (int i = offset; i < limit; i++) {
                    ndjson.append("{\"index\":{\"_index\":\"")
                            .append(index)
                            .append("\",\"_id\":\"")
                            .append(i)
                            .append("\"}}\n");
                    ndjson.append("{\"value\":")
                            .append(random.nextInt(1_000_000))
                            .append(",\"name\":\"n")
                            .append(random.nextInt(10_000))
                            .append("\"}\n");
                }
                String response = bulkOrFail(server, ndjson.toString());
                assertTrue(response.contains("\"errors\":false"));
            }

            server.refresh(index);
            String countJson = server.post("/" + index + "/_count", "{\"query\":{\"match_all\":{}}}");
            String count = JsonUtils.parseFlatJson(countJson).get("count");
            assertEquals("100000", count);
        }
    }

    private static String bulkOrFail(ElasticServer server, String ndjson) throws IOException, InterruptedException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                return server.bulk(ndjson);
            } catch (IOException e) {
                lastFailure = e;
                if (attempt == 5) {
                    break;
                }
                server.waitForYellow(Duration.ofSeconds(15));
                Thread.sleep(Duration.ofSeconds(attempt * 2L).toMillis());
            }
        }
        throw new IOException("Bulk indexing failed. Elasticsearch log tail:\n" + server.logTail(80), lastFailure);
    }

}
