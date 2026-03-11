package example;

import example.support.AutomationHarnessSupport;
import io.github.wboult.esrunner.ElasticClient;
import io.github.wboult.esrunner.gradle.testsupport.ElasticGradleTestEnv;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppIntegrationTest {
    @Test
    void seedsOrdersIntoItsOwnNamespace() throws Exception {
        ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
        ElasticClient client = env.client();

        String index = env.index("orders");
        AutomationHarnessSupport.seedOrders(client, index);

        assertEquals(3L, client.countValue(index));
        assertTrue(client.search(index, "{\"query\":{\"match\":{\"customer\":\"Ada\"}}}").contains("Ada Lovelace"));

        AutomationHarnessSupport.writeMetadata("app-integration.properties", env, Map.of(
                "index", index,
                "seededCount", "3",
                "scenario", "bulk-seed"
        ));
    }
}
