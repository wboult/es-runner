package io.github.wboult.esrunner.javaclient;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.github.wboult.esrunner.ElasticServer;
import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

/**
 * Factory helpers for the official Elasticsearch Java API Client.
 */
public final class ElasticJavaClients {
    private ElasticJavaClients() {
    }

    /**
     * Creates a managed Java client for the running server.
     *
     * @param server running server
     * @return managed client handle
     */
    public static ManagedElasticsearchClient create(ElasticServer server) {
        Objects.requireNonNull(server, "server");
        return create(server.baseUri());
    }

    /**
     * Creates a managed Java client for the running server using a builder
     * callback.
     *
     * @param server running server
     * @param consumer Java client config builder callback
     * @return managed client handle
     */
    public static ManagedElasticsearchClient create(ElasticServer server, Consumer<ElasticJavaClientConfig.Builder> consumer) {
        Objects.requireNonNull(server, "server");
        return create(server.baseUri(), consumer);
    }

    /**
     * Creates a managed Java client for the provided base URI.
     *
     * @param baseUri Elasticsearch base URI
     * @return managed client handle
     */
    public static ManagedElasticsearchClient create(URI baseUri) {
        return create(baseUri, ElasticJavaClientConfig.defaults());
    }

    /**
     * Creates a managed Java client for the provided base URI using a builder
     * callback.
     *
     * @param baseUri Elasticsearch base URI
     * @param consumer Java client config builder callback
     * @return managed client handle
     */
    public static ManagedElasticsearchClient create(URI baseUri, Consumer<ElasticJavaClientConfig.Builder> consumer) {
        return create(baseUri, ElasticJavaClientConfig.from(consumer));
    }

    /**
     * Creates a managed Java client for the provided base URI.
     *
     * @param baseUri Elasticsearch base URI
     * @param config Java client configuration
     * @return managed client handle
     */
    public static ManagedElasticsearchClient create(URI baseUri, ElasticJavaClientConfig config) {
        Objects.requireNonNull(baseUri, "baseUri");
        Objects.requireNonNull(config, "config");

        RestClientBuilder builder = RestClient.builder(HttpHost.create(normalize(baseUri).toString()));
        config.restClientBuilderCustomizer().accept(builder);
        RestClient restClient = builder.build();
        RestClientTransport transport = new RestClientTransport(restClient, config.mapper());

        ElasticsearchClient client = new ElasticsearchClient(transport);
        if (config.transportOptions() != null) {
            client = client.withTransportOptions(config.transportOptions());
        }

        return new ManagedElasticsearchClient(baseUri, restClient, transport, client);
    }

    private static URI normalize(URI baseUri) {
        String uri = baseUri.toString();
        return URI.create(uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri);
    }
}
