package io.github.wboult.esrunner.embedded;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for {@link EmbeddedElasticServer}.
 *
 * <p><strong>Pre-requisite:</strong> set {@code ES_EMBEDDED_HOME} to the root of an
 * <em>extracted</em> Elasticsearch 8.1.x distribution before running.  The directory
 * must contain {@code bin/}, {@code lib/}, and {@code modules/}.
 *
 * <pre>
 *   # Linux / macOS
 *   export ES_EMBEDDED_HOME=/path/to/elasticsearch-8.1.1
 *   ./gradlew :es-runner-embedded:test
 *
 *   # Windows PowerShell
 *   $env:ES_EMBEDDED_HOME = "C:\path\to\elasticsearch-8.1.1"
 *   .\gradlew.bat :es-runner-embedded:test
 * </pre>
 *
 * <p>Tests are skipped (not failed) when {@code ES_EMBEDDED_HOME} is not set.
 */
class EmbeddedElasticServerTest {

    private static final String ENV_ES_HOME = "ES_EMBEDDED_HOME";

    /** Resolves the extracted ES home from the environment, skipping if absent. */
    private static Path esHome() {
        String home = System.getenv(ENV_ES_HOME);
        assumeTrue(home != null && !home.isBlank(),
                ENV_ES_HOME + " not set — skipping embedded ES tests. "
                        + "Set " + ENV_ES_HOME + " to the root of an extracted "
                        + "Elasticsearch 8.1.x distribution.");
        return Path.of(home);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void nodeStartsAndRespondsToHttp(@TempDir Path tempDir) throws Exception {
        Path esHome = esHome();

        try (EmbeddedElasticServer server = EmbeddedElasticServer.start(
                esHome,
                tempDir.resolve("data"),
                tempDir.resolve("logs"))) {

            URI base = server.baseUri();
            System.out.println("Embedded ES started at " + base);

            HttpResponse<String> resp = get(base);
            assertEquals(200, resp.statusCode(),
                    "Root endpoint should return 200; body: " + resp.body());

            String body = resp.body();
            assertTrue(body.contains("\"cluster_name\""),
                    "Response should include cluster_name; got: " + body);
            System.out.println("Root response: " + body);
        }
    }

    @Test
    void clusterHealthIsReachable(@TempDir Path tempDir) throws Exception {
        Path esHome = esHome();

        try (EmbeddedElasticServer server = EmbeddedElasticServer.start(
                esHome,
                tempDir.resolve("data"),
                tempDir.resolve("logs"),
                Map.of("cluster.name", "health-test"))) {

            HttpResponse<String> resp = get(URI.create(server.baseUri() + "/_cluster/health"));
            assertEquals(200, resp.statusCode());

            String body = resp.body();
            assertTrue(body.contains("\"status\""),
                    "Health response should contain status; got: " + body);
            System.out.println("Cluster health: " + body);
        }
    }

    @Test
    void basicIndexAndSearch(@TempDir Path tempDir) throws Exception {
        Path esHome = esHome();

        try (EmbeddedElasticServer server = EmbeddedElasticServer.start(
                esHome,
                tempDir.resolve("data"),
                tempDir.resolve("logs"))) {

            URI base = server.baseUri();
            HttpClient http = HttpClient.newHttpClient();

            // Create an index
            String createIdx = put(http, URI.create(base + "/test-index"),
                    "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0}}");
            System.out.println("Create index: " + createIdx);

            // Index a document
            String indexed = put(http, URI.create(base + "/test-index/_doc/1"),
                    "{\"title\":\"hello world\",\"value\":42}");
            System.out.println("Index doc: " + indexed);

            // Refresh so it's searchable
            post(http, URI.create(base + "/test-index/_refresh"), "");

            // Count
            HttpResponse<String> count = http.send(
                    HttpRequest.newBuilder(URI.create(base + "/test-index/_count")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            System.out.println("Count: " + count.body());
            assertEquals(200, count.statusCode());
            assertTrue(count.body().contains("\"count\":1"),
                    "Expected 1 document; got: " + count.body());

            // Full-text search using the standard analyser (requires analysis-common module)
            HttpResponse<String> search = http.send(
                    HttpRequest.newBuilder(URI.create(base + "/test-index/_search"))
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "{\"query\":{\"match\":{\"title\":\"hello\"}}}"))
                            .header("Content-Type", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            System.out.println("Search: " + search.body());
            assertEquals(200, search.statusCode(),
                    "Search should succeed; body: " + search.body());
            assertTrue(search.body().contains("hello world"),
                    "Search result should contain indexed document; got: " + search.body());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static HttpResponse<String> get(URI uri) throws Exception {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .send(HttpRequest.newBuilder(uri).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    private static String put(HttpClient http, URI uri, String body) throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(uri)
                        .PUT(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(resp.statusCode() < 300,
                "PUT " + uri + " failed (" + resp.statusCode() + "): " + resp.body());
        return resp.body();
    }

    private static String post(HttpClient http, URI uri, String body) throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(uri)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(resp.statusCode() < 300,
                "POST " + uri + " failed (" + resp.statusCode() + "): " + resp.body());
        return resp.body();
    }
}
