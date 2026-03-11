package example.support;

import io.github.wboult.esrunner.ElasticClient;
import io.github.wboult.esrunner.ElasticResponse;
import io.github.wboult.esrunner.gradle.testsupport.ElasticGradleTestEnv;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class AutomationHarnessSupport {
    private AutomationHarnessSupport() {
    }

    public static void seedOrders(ElasticClient client, String index) throws IOException, InterruptedException {
        String payload = loadResource("orders-bulk.ndjson").replace("${index}", index);
        String response = client.bulk(payload);
        if (!response.contains("\"errors\":false")) {
            throw new IllegalStateException("Bulk seed failed: " + response);
        }
        client.refresh(index);
    }

    public static String ordersTemplateJson(String indexPattern) {
        return """
                {
                  "index_patterns": ["%s"],
                  "template": {
                    "mappings": {
                      "properties": {
                        "customer": { "type": "text" },
                        "status": { "type": "keyword" },
                        "priority": { "type": "keyword" },
                        "channel": { "type": "keyword" },
                        "total": { "type": "double" }
                      }
                    }
                  }
                }
                """.formatted(indexPattern);
    }

    public static void addAlias(ElasticClient client, String index, String alias) throws IOException, InterruptedException {
        ElasticResponse response = client.request("POST", "/_aliases",
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

    public static void writeMetadata(String fileName,
                                     ElasticGradleTestEnv env,
                                     Map<String, String> extraProperties) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("baseUri", env.baseUri().toString());
        properties.setProperty("clusterName", env.clusterName());
        properties.setProperty("namespace", env.namespace());
        properties.setProperty("buildId", env.buildId());
        properties.setProperty("suiteId", env.suiteId());
        extraProperties.forEach((key, value) -> properties.setProperty(key, Objects.requireNonNull(value, key)));

        Path output = Path.of("build", "es-runner", fileName);
        Files.createDirectories(output.getParent());
        try (var stream = Files.newOutputStream(output)) {
            properties.store(stream, "automation harness metadata");
        }
    }

    private static String loadResource(String name) throws IOException {
        try (InputStream input = AutomationHarnessSupport.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
