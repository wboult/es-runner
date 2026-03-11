package io.github.wboult.esrunner;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SearchWorkflowAssertions {
    private static final Pattern SEARCH_TOTAL_PATTERN =
            Pattern.compile("\"total\"\\s*:\\s*\\{\\s*\"value\"\\s*:\\s*(\\d+)");
    private static final Pattern BULK_ERRORS_FALSE_PATTERN =
            Pattern.compile("\"errors\"\\s*:\\s*false");
    private static final Pattern TIMED_OUT_FALSE_PATTERN =
            Pattern.compile("\"timed_out\"\\s*:\\s*false");

    private SearchWorkflowAssertions() {
    }

    static void assertRealisticOrderWorkflow(ElasticServer server, String prefix) throws Exception {
        assertTrue(server.waitForYellow(Duration.ofSeconds(120)));
        ElasticClient client = server.client()
                .withRequestTimeout(Duration.ofSeconds(120))
                .withBulkTimeout(Duration.ofMinutes(5));
        client.put("/_cluster/settings", """
                {
                  "transient": {
                    "cluster.routing.allocation.disk.threshold_enabled": false
                  }
                }
                """);

        String templateName = prefix + "-orders-template";
        String index = prefix + "-orders-2026-03";
        String alias = prefix + "-orders-read";

        String templateResponse = client.putIndexTemplate(templateName, """
                {
                  "index_patterns": ["%s-orders-*"],
                  "template": {
                    "settings": {
                      "index": {
                        "number_of_replicas": 0
                      }
                    },
                    "mappings": {
                      "properties": {
                        "customer": { "type": "keyword" },
                        "region": { "type": "keyword" },
                        "status": { "type": "keyword" },
                        "description": { "type": "text" },
                        "total": { "type": "double" },
                        "ordered_at": { "type": "date" }
                      }
                    },
                    "aliases": {
                      "%s-orders-read": {}
                    }
                  }
                }
                """.formatted(prefix, prefix));
        assertTrue(templateResponse.contains("\"acknowledged\":true"));
        assertTrue(client.getIndexTemplate(templateName).contains(templateName));

        client.createIndex(index);
        assertTrue(client.indexExists(index));
        String indexHealth = client.get("/_cluster/health/" + index + "?wait_for_status=yellow&timeout=120s");
        assertTrue(TIMED_OUT_FALSE_PATTERN.matcher(indexHealth).find(), indexHealth);

        String bulkResponse = client.bulk("""
                {"index":{"_index":"%s","_id":"o-100"}}
                {"customer":"acme","region":"eu","status":"shipped","description":"overnight bike delivery","total":120.5,"ordered_at":"2026-03-10T10:15:00Z"}
                {"index":{"_index":"%s","_id":"o-101"}}
                {"customer":"acme","region":"eu","status":"pending","description":"standard helmet delivery","total":45.0,"ordered_at":"2026-03-10T11:00:00Z"}
                {"index":{"_index":"%s","_id":"o-102"}}
                {"customer":"globex","region":"us","status":"shipped","description":"overnight gloves delivery","total":75.25,"ordered_at":"2026-03-10T12:30:00Z"}
                """.formatted(index, index, index));
        assertTrue(BULK_ERRORS_FALSE_PATTERN.matcher(bulkResponse).find(), bulkResponse);

        client.refresh(index);
        assertEquals(3L, client.countValue(index));
        assertTrue(client.get("/_alias/" + alias).contains(index));

        String matchResponse = client.search(alias, """
                {
                  "query": {
                    "match": {
                      "description": "overnight"
                    }
                  }
                }
                """);
        assertSearchTotal(matchResponse, 2);

        String termResponse = client.search(alias, """
                {
                  "query": {
                    "term": {
                      "status": "shipped"
                    }
                  }
                }
                """);
        assertSearchTotal(termResponse, 2);

        String aggregationResponse = client.search(alias, """
                {
                  "size": 0,
                  "aggs": {
                    "orders_by_region": {
                      "terms": {
                        "field": "region"
                      }
                    }
                  }
                }
                """);
        assertTrue(aggregationResponse.contains("\"orders_by_region\""));
        assertTrue(aggregationResponse.contains("\"key\":\"eu\""));
        assertTrue(aggregationResponse.contains("\"key\":\"us\""));
    }

    private static void assertSearchTotal(String json, long expected) {
        Matcher matcher = SEARCH_TOTAL_PATTERN.matcher(json);
        assertTrue(matcher.find(), "Expected search total in response: " + json);
        assertEquals(expected, Long.parseLong(matcher.group(1)));
    }
}
