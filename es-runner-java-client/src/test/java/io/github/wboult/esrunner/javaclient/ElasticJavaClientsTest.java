package io.github.wboult.esrunner.javaclient;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticJavaClientsTest {
    @Test
    void createsOfficialClientThatCanReadClusterInfo() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<Headers> requestHeaders = new AtomicReference<>();
        try (StubElasticHttpServer server = new StubElasticHttpServer((exchange, body) -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestHeaders.set(exchange.getRequestHeaders());
            return body(200, ROOT_RESPONSE);
        })) {
            URI baseUri = URI.create("http://localhost:" + server.port() + "/");

            try (ManagedElasticsearchClient managed = ElasticJavaClients.create(baseUri)) {
                var info = managed.client().info();

                assertEquals("stub-cluster", info.clusterName());
                assertEquals("8.19.11", info.version().number());
                assertEquals("/", requestPath.get());
                assertNotNull(requestHeaders.get().getFirst("Accept"));
                assertTrue(requestHeaders.get().getFirst("Accept").contains("compatible-with=8"));
            }
        }
    }

    @Test
    void restClientBuilderCustomizerCanAddHeaders() throws Exception {
        AtomicReference<Headers> requestHeaders = new AtomicReference<>();
        try (StubElasticHttpServer server = new StubElasticHttpServer((exchange, body) -> {
            requestHeaders.set(exchange.getRequestHeaders());
            return body(200, ROOT_RESPONSE);
        })) {
            URI baseUri = URI.create("http://localhost:" + server.port() + "/");

            try (ManagedElasticsearchClient managed = ElasticJavaClients.create(baseUri, builder -> builder
                    .customizeRestClientBuilder(restClient -> restClient.setDefaultHeaders(new Header[] {
                            new BasicHeader("Authorization", "ApiKey test-key"),
                            new BasicHeader("X-Es-Runner-Test", "true")
                    })))) {
                managed.client().info();
            }

            assertEquals("ApiKey test-key", requestHeaders.get().getFirst("Authorization"));
            assertEquals("true", requestHeaders.get().getFirst("X-Es-Runner-Test"));
        }
    }

    private static StubResponse body(int statusCode, String body) {
        return new StubResponse(statusCode, body);
    }

    private static final String ROOT_RESPONSE = """
            {
              "name": "stub-node",
              "cluster_name": "stub-cluster",
              "cluster_uuid": "stub-cluster-uuid",
              "version": {
                "number": "8.19.11",
                "build_flavor": "default",
                "build_type": "zip",
                "build_hash": "deadbeef",
                "build_date": "2026-03-08T00:00:00Z",
                "build_snapshot": false,
                "lucene_version": "9.12.2",
                "minimum_wire_compatibility_version": "8.0.0",
                "minimum_index_compatibility_version": "8.0.0"
              },
              "tagline": "You Know, for Search"
            }
            """;

    @FunctionalInterface
    private interface StubHandler {
        StubResponse handle(HttpExchange exchange, String requestBody) throws IOException;
    }

    private record StubResponse(int statusCode, String body) {
    }

    private static final class StubElasticHttpServer implements AutoCloseable {
        private final HttpServer server;

        private StubElasticHttpServer(StubHandler handler) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", new DelegatingHandler(handler));
            server.start();
        }

        private int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class DelegatingHandler implements HttpHandler {
        private final StubHandler delegate;

        private DelegatingHandler(StubHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            StubResponse response = delegate.handle(exchange, requestBody);
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("X-elastic-product", "Elasticsearch");
            exchange.sendResponseHeaders(response.statusCode(), body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
    }
}
