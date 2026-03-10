package example;

import io.github.wboult.esrunner.ElasticClient;
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
class AppIntegrationTest {
    private static final ElasticGradleTestEnv ENV = ElasticGradleTestEnv.fromSystemProperties();
    private static final ElasticClient CLIENT = ENV.client().withRequestTimeout(Duration.ofMinutes(2));
    private static final String INDEX = ENV.index("orders");

    @Test
    @Order(1)
    void createsNamespacedIndexAndWritesMetadata() throws Exception {
        CLIENT.createIndex(INDEX, "{\"settings\":{\"index.number_of_replicas\":0}}");
        CLIENT.indexDocument(INDEX, "1", "{\"suite\":\"app-integration\",\"status\":\"new\"}");
        CLIENT.refresh(INDEX);

        assertEquals(1L, CLIENT.countValue(INDEX));
        assertTrue(ENV.namespace().contains("app_integrationtest"));
        writeInfo("app-integration");
    }

    @Test
    @Order(2)
    void reusesStateInsideTheSameSuite() throws Exception {
        assertEquals(1L, CLIENT.countValue(INDEX));
    }

    private static void writeInfo(String fileName) throws IOException {
        Files.createDirectories(Path.of("build", "es-runner"));
        Properties properties = new Properties();
        properties.setProperty("baseUri", ENV.baseUri().toString());
        properties.setProperty("buildId", ENV.buildId());
        properties.setProperty("namespace", ENV.namespace());
        properties.setProperty("suiteId", ENV.suiteId());
        properties.setProperty("clusterName", ENV.clusterName());
        try (var output = Files.newOutputStream(Path.of("build", "es-runner", fileName + ".properties"))) {
            properties.store(output, null);
        }
    }
}
