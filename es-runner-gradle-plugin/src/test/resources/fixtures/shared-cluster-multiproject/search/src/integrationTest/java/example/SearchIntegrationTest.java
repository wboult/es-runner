package example;

import io.github.wboult.esrunner.ElasticClient;
import io.github.wboult.esrunner.ElasticResponse;
import io.github.wboult.esrunner.gradle.testsupport.ElasticGradleTestEnv;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SearchIntegrationTest {
    private static final ElasticGradleTestEnv ENV = ElasticGradleTestEnv.fromSystemProperties();
    private static final ElasticClient CLIENT = ENV.client().withRequestTimeout(Duration.ofMinutes(2));
    private static final String INDEX = ENV.index("orders-2026");
    private static final String INDEX_PATTERN = ENV.indexPattern("orders");
    private static final String ALIAS = ENV.alias("orders-read");
    private static final String TEMPLATE = ENV.template("orders-template");

    @Test
    @Order(1)
    void createsNamespacedTemplateAliasAndSearchData() throws Exception {
        CLIENT.putIndexTemplate(TEMPLATE, """
                {
                  "index_patterns": ["%s"],
                  "template": {
                    "settings": {
                      "index": {
                        "number_of_replicas": 0
                      }
                    },
                    "mappings": {
                      "properties": {
                        "customer": { "type": "text" },
                        "status": { "type": "keyword" }
                      }
                    }
                  }
                }
                """.formatted(INDEX_PATTERN));
        CLIENT.createIndex(INDEX);
        CLIENT.bulkIndexDocuments(INDEX, java.util.List.of(
                "{\"customer\":\"Ada Lovelace\",\"status\":\"new\"}",
                "{\"customer\":\"Grace Hopper\",\"status\":\"pending\"}"
        ));
        addAlias(INDEX, ALIAS);
        CLIENT.refresh(INDEX);

        assertEquals(2L, CLIENT.countValue(INDEX));
        assertTrue(CLIENT.search(ALIAS, """
                {
                  "query": {
                    "match": {
                      "customer": "Ada"
                    }
                  }
                }
                """).contains("Ada Lovelace"));
        assertTrue(ENV.namespace().contains("search_integrationtest"));
        writeInfo("search-integration");
    }

    @Test
    @Order(2)
    void reusesSearchSuiteState() throws Exception {
        assertEquals(2L, CLIENT.countValue(INDEX));
        assertTrue(CLIENT.search(ALIAS, """
                {
                  "query": {
                    "match": {
                      "customer": "Grace"
                    }
                  }
                }
                """).contains("Grace Hopper"));
    }

    private static void writeInfo(String fileName) throws IOException {
        Files.createDirectories(Path.of("build", "es-runner"));
        Properties properties = new Properties();
        properties.setProperty("baseUri", ENV.baseUri().toString());
        properties.setProperty("buildId", ENV.buildId());
        properties.setProperty("namespace", ENV.namespace());
        properties.setProperty("suiteId", ENV.suiteId());
        properties.setProperty("clusterName", ENV.clusterName());
        properties.setProperty("index", INDEX);
        properties.setProperty("indexPattern", INDEX_PATTERN);
        properties.setProperty("alias", ALIAS);
        properties.setProperty("template", TEMPLATE);
        properties.setProperty("scenario", "template-alias-query");
        try (var output = Files.newOutputStream(Path.of("build", "es-runner", fileName + ".properties"))) {
            properties.store(output, null);
        }
    }

    private static void addAlias(String index, String alias) throws IOException, InterruptedException {
        ElasticResponse response = CLIENT.request("POST", "/_aliases",
                """
                {
                  "actions": [
                    { "add": { "index": "%s", "alias": "%s" } }
                  ]
                }
                """.formatted(index, alias));
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("Alias update failed: " + response.statusCode() + " " + response.body());
        }
    }
}
