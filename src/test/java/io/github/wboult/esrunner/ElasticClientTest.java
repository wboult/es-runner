package io.github.wboult.esrunner;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticClientTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void waitForStatusPollsUntilExpectedStatus() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/_cluster/health", exchange -> {
            int call = calls.incrementAndGet();
            String body = call < 2 ? "{\"status\":\"red\"}" : "{\"status\":\"yellow\"}";
            respond(exchange, 200, body);
        });
        startServer();

        ElasticClient client = new ElasticClient(baseUri());

        assertTrue(client.waitForYellow(Duration.ofSeconds(2)));
        assertTrue(calls.get() >= 2);
    }

    @Test
    void waitForStatusReturnsFalseWhenTargetStatusNeverAppears() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/_cluster/health", exchange -> respond(exchange, 200, "{\"status\":\"red\"}"));
        startServer();

        ElasticClient client = new ElasticClient(baseUri());

        assertFalse(client.waitForGreen(Duration.ofMillis(200)));
    }

    @Test
    void countAndResourceQueriesUseExpectedEndpoints() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/orders/_count", exchange -> respond(exchange, 200, "{\"count\":7}"));
        server.createContext("/_cat/indices", exchange -> {
            assertEquals("format=json", exchange.getRequestURI().getQuery());
            respond(exchange, 200, "[{\"index\":\"orders\"}]");
        });
        server.createContext("/_nodes/stats", exchange -> respond(exchange, 200, "{\"nodes\":{\"n1\":{}}}"));
        startServer();

        ElasticClient client = new ElasticClient(baseUri());

        assertEquals(7L, client.countValue("orders"));
        assertTrue(client.catIndices().contains("\"orders\""));
        assertTrue(client.nodesStats().contains("\"nodes\""));
    }

    @Test
    void bulkUsesNdjsonContentTypeAndBody() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/_bulk", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(readBody(exchange));
            respond(exchange, 200, "{\"errors\":false}");
        });
        startServer();

        ElasticClient client = new ElasticClient(baseUri());
        String ndjson = "{\"index\":{}}\n{\"value\":1}\n";

        assertTrue(client.bulk(ndjson).contains("\"errors\":false"));
        assertEquals("application/x-ndjson", contentType.get());
        assertEquals(ndjson, body.get());
    }

    @Test
    void indexDocumentWithoutIdUsesPostDocEndpoint() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/orders/_doc", exchange -> {
            method.set(exchange.getRequestMethod());
            body.set(readBody(exchange));
            respond(exchange, 201, "{\"result\":\"created\"}");
        });
        startServer();

        ElasticClient client = new ElasticClient(baseUri());

        assertTrue(client.indexDocument("orders", "{\"value\":1}").contains("\"created\""));
        assertEquals("POST", method.get());
        assertEquals("{\"value\":1}", body.get());
    }

    @Test
    void bulkIndexDocumentsBuildsNdjsonForOneIndex() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/_bulk", exchange -> {
            body.set(readBody(exchange));
            respond(exchange, 200, "{\"errors\":false}");
        });
        startServer();

        ElasticClient client = new ElasticClient(baseUri());

        assertTrue(client.bulkIndexDocuments("orders", List.of("{\"value\":1}", "{\"value\":2}"))
                .contains("\"errors\":false"));
        assertEquals("""
                {"index":{"_index":"orders"}}
                {"value":1}
                {"index":{"_index":"orders"}}
                {"value":2}
                """, body.get());
    }

    @Test
    void bulkIndexDocumentsRejectsEmptyLists() {
        ElasticClient client = new ElasticClient(URI.create("http://localhost:9200/"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> client.bulkIndexDocuments("orders", List.of()));
        assertTrue(ex.getMessage().contains("must not be empty"));
    }

    @Test
    void timeoutOverridesReturnConfiguredCopies() {
        ElasticClient base = new ElasticClient(URI.create("http://localhost:9200/"));

        ElasticClient updated = base.withRequestTimeout(Duration.ofSeconds(90))
                .withBulkTimeout(Duration.ofMinutes(9));

        assertEquals(Duration.ofSeconds(30), base.requestTimeout());
        assertEquals(Duration.ofMinutes(5), base.bulkTimeout());
        assertEquals(Duration.ofSeconds(90), updated.requestTimeout());
        assertEquals(Duration.ofMinutes(9), updated.bulkTimeout());
    }

    private void startServer() {
        server.start();
    }

    private URI baseUri() {
        return URI.create("http://localhost:" + server.getAddress().getPort() + "/");
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
