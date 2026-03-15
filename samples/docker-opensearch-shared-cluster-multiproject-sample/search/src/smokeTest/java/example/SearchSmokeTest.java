package example;

import example.support.SampleSupport;
import io.github.wboult.esrunner.ElasticClient;
import io.github.wboult.esrunner.ElasticResponse;
import io.github.wboult.esrunner.gradle.testsupport.ElasticGradleTestEnv;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SearchSmokeTest {
    @Test
    void rawLogicalIndexNameFailsSoSuitesMustUseNamespacedResources() throws Exception {
        ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
        ElasticClient client = env.client();

        String index = env.index("orders");
        ElasticResponse rawLogical = client.request("GET", "/orders/_count", null);

        assertEquals(404, rawLogical.statusCode());
        assertFalse(client.indexExists(index));

        SampleSupport.writeMetadata("search-smoke.properties", env, Map.of(
                "index", index,
                "rawLogicalIndexStatus", Integer.toString(rawLogical.statusCode()),
                "scenario", "negative-path"
        ));
    }
}
