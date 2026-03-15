package example;

import example.support.SampleSupport;
import io.github.wboult.esrunner.ElasticClient;
import io.github.wboult.esrunner.gradle.testsupport.ElasticGradleTestEnv;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AppSmokeTest {
    @Test
    void startsWithFreshNamespaceEvenWhenAnotherSuiteUsesOrders() throws Exception {
        ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
        ElasticClient client = env.client();

        String index = env.index("orders");
        assertFalse(client.indexExists(index));

        client.createIndex(index);
        client.indexDocument(index, "1", "{\"source\":\"app-smoke\"}");
        client.refresh(index);

        assertEquals(1, client.countValue(index));

        SampleSupport.writeMetadata("app-smoke.properties", env, Map.of(
                "index", index,
                "freshNamespace", "true",
                "scenario", "suite-isolation"
        ));
    }
}
