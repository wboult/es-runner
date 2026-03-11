package io.github.wboult.esrunner;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("plugin-install")
class ElasticRunnerPluginInstallIntegrationTest {

    @Test
    void installsAnalysisIcuAndUsesItForRealSearches() throws Exception {
        String version = System.getenv().getOrDefault("ES_VERSION", "9.3.1");
        Path workDir = Files.createTempDirectory("es-runner-plugin-it-");
        ElasticRunnerConfig config = IntegrationTestSupport.config(
                version,
                workDir,
                "plugin-it",
                "512m",
                Duration.ofSeconds(240),
                true
        ).toBuilder()
                .plugin("analysis-icu")
                .build();

        try (ElasticServer server = ElasticRunner.start(config)) {
            ElasticClient client = server.client()
                    .withRequestTimeout(Duration.ofSeconds(120))
                    .withBulkTimeout(Duration.ofMinutes(5));
            assertTrue(server.waitForYellow(Duration.ofSeconds(120)));

            client.put("/_cluster/settings", """
                    {
                      "transient": {
                        "cluster.routing.allocation.disk.threshold_enabled": false
                      }
                    }
                    """);

            assertTrue(client.get("/_nodes/plugins").contains("analysis-icu"));

            client.createIndex("icu-docs", """
                    {
                      "settings": {
                        "index": {
                          "number_of_replicas": 0
                        },
                        "analysis": {
                          "analyzer": {
                            "folding": {
                              "tokenizer": "standard",
                              "filter": ["lowercase", "icu_folding"]
                            }
                          }
                        }
                      },
                      "mappings": {
                        "properties": {
                          "title": {
                            "type": "text",
                            "analyzer": "folding"
                          }
                        }
                      }
                    }
                    """);
            String indexHealth = client.get("/_cluster/health/icu-docs?wait_for_status=yellow&timeout=120s");
            assertTrue(indexHealth.contains("\"timed_out\":false"), indexHealth);

            client.indexDocument("icu-docs", "1", """
                    {
                      "title": "Caf\u00e9 Cr\u00e8me"
                    }
                    """);
            client.refresh("icu-docs");

            String searchResponse = client.search("icu-docs", """
                    {
                      "query": {
                        "match": {
                          "title": "cafe creme"
                        }
                      }
                    }
                    """);
            SearchWorkflowAssertions.assertSearchTotal(searchResponse, 1);
        }
    }
}
