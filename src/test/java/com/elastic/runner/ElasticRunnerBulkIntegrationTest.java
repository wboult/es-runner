package com.elastic.runner;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Duration;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticRunnerBulkIntegrationTest {

    @Test
    void indexesLargeDatasetAndCounts() throws Exception {
        String version = System.getenv().getOrDefault("ES_VERSION", "9.2.4");

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

            String index = "bulk-test";
            server.createIndex(index);

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
                String response = server.bulk(ndjson.toString());
                assertTrue(response.contains("\"errors\":false"));
            }

            server.refresh(index);
            String countJson = server.post("/" + index + "/_count", "{\"query\":{\"match_all\":{}}}");
            String count = JsonUtils.parseFlatJson(countJson).get("count");
            assertEquals("100000", count);
        }
    }

}
