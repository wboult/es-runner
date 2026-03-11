package example;

import example.support.AutomationHarnessSupport;
import io.github.wboult.esrunner.ElasticClient;
import io.github.wboult.esrunner.gradle.testsupport.ElasticGradleTestEnv;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchIntegrationTest {
    @Test
    void queriesSeededOrdersThroughNamespacedAlias() throws Exception {
        ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
        ElasticClient client = env.client();

        String index = env.index("orders-2026");
        String alias = env.alias("orders-read");
        String template = env.template("orders-template");

        client.putIndexTemplate(template, AutomationHarnessSupport.ordersTemplateJson(env.index("orders-*")));
        AutomationHarnessSupport.seedOrders(client, index);
        AutomationHarnessSupport.addAlias(client, index, alias);

        assertEquals(3L, client.countValue(index));
        assertTrue(client.search(alias, "{\"query\":{\"match\":{\"customer\":\"Ada\"}}}").contains("Ada Lovelace"));

        AutomationHarnessSupport.writeMetadata("search-integration.properties", env, Map.of(
                "index", index,
                "alias", alias,
                "template", template,
                "scenario", "template-alias-query"
        ));
    }
}
