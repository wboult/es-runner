package io.github.wboult.esrunner.javaclient;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import org.elasticsearch.client.RestClient;

/**
 * Managed handle for the official Elasticsearch Java API Client and its
 * underlying transport resources.
 */
public final class ManagedElasticsearchClient implements AutoCloseable {
    private final URI baseUri;
    private final RestClient restClient;
    private final RestClientTransport transport;
    private final ElasticsearchClient client;

    ManagedElasticsearchClient(URI baseUri,
                               RestClient restClient,
                               RestClientTransport transport,
                               ElasticsearchClient client) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.client = Objects.requireNonNull(client, "client");
    }

    /**
     * Returns the Elasticsearch base URI.
     *
     * @return base URI
     */
    public URI baseUri() {
        return baseUri;
    }

    /**
     * Returns the official Elasticsearch Java API Client.
     *
     * @return typed Elasticsearch client
     */
    public ElasticsearchClient client() {
        return client;
    }

    /**
     * Returns the low-level REST client used by the transport.
     *
     * @return low-level REST client
     */
    public RestClient restClient() {
        return restClient;
    }

    /**
     * Returns the Java API Client transport.
     *
     * @return transport
     */
    public RestClientTransport transport() {
        return transport;
    }

    /**
     * Closes the transport and underlying low-level REST client.
     *
     * @throws IOException if close fails
     */
    @Override
    public void close() throws IOException {
        transport.close();
    }
}
