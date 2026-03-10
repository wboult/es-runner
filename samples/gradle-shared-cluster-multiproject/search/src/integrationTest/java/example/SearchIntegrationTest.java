package example;

import io.github.wboult.esrunner.ElasticClient;
import io.github.wboult.esrunner.gradle.testsupport.ElasticGradleTestEnv;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchIntegrationTest {
    @Test
    void usesSharedClusterWithOwnNamespace() throws Exception {
        ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
        ElasticClient client = env.client();

        String index = env.index("orders");
        client.createIndex(index);
        client.indexDocument(index, "1", "{\"source\":\"search-integration\"}");
        client.refresh(index);

        assertEquals(1, client.countValue(index));
        write("search-integration.properties", env, index);
    }

    private static void write(String fileName, ElasticGradleTestEnv env, String index) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("baseUri", env.baseUri().toString());
        properties.setProperty("clusterName", env.clusterName());
        properties.setProperty("namespace", env.namespace());
        properties.setProperty("buildId", env.buildId());
        properties.setProperty("suiteId", env.suiteId());
        properties.setProperty("index", index);

        Path output = Path.of("build", "es-runner", fileName);
        Files.createDirectories(output.getParent());
        try (var stream = Files.newOutputStream(output)) {
            properties.store(stream, "search integration metadata");
        }
    }
}
