package io.github.wboult.esrunner.javaclient;

import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.TransportOptions;
import java.util.Objects;
import java.util.function.Consumer;
import org.elasticsearch.client.RestClientBuilder;

/**
 * Configuration for constructing the official Elasticsearch Java API Client.
 */
public record ElasticJavaClientConfig(
        JsonpMapper mapper,
        TransportOptions transportOptions,
        Consumer<RestClientBuilder> restClientBuilderCustomizer
) {
    /**
     * Normalizes optional configuration values.
     *
     * @param mapper JSON-P mapper used by the transport
     * @param transportOptions optional transport options applied to the client
     * @param restClientBuilderCustomizer customization hook for the underlying
     *                                    low-level REST client builder
     */
    public ElasticJavaClientConfig {
        mapper = mapper == null ? new JacksonJsonpMapper() : mapper;
        restClientBuilderCustomizer = restClientBuilderCustomizer == null
                ? builder -> { }
                : restClientBuilderCustomizer;
    }

    /**
     * Returns the default Java client configuration.
     *
     * @return default configuration
     */
    public static ElasticJavaClientConfig defaults() {
        return new ElasticJavaClientConfig(new JacksonJsonpMapper(), null, builder -> { });
    }

    /**
     * Returns a builder seeded from the defaults.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder(defaults());
    }

    /**
     * Builds a configuration by applying the consumer to the defaults.
     *
     * @param consumer builder callback
     * @return built configuration
     */
    public static ElasticJavaClientConfig from(Consumer<Builder> consumer) {
        Builder builder = builder();
        consumer.accept(builder);
        return builder.build();
    }

    /**
     * Returns a builder seeded from this configuration.
     *
     * @return builder copy
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Mutable builder for {@link ElasticJavaClientConfig}.
     */
    public static final class Builder {
        private JsonpMapper mapper;
        private TransportOptions transportOptions;
        private Consumer<RestClientBuilder> restClientBuilderCustomizer;

        private Builder(ElasticJavaClientConfig base) {
            this.mapper = base.mapper();
            this.transportOptions = base.transportOptions();
            this.restClientBuilderCustomizer = base.restClientBuilderCustomizer();
        }

        /**
         * Sets the mapper used by the transport.
         *
         * @param mapper JSON-P mapper
         * @return this builder
         */
        public Builder mapper(JsonpMapper mapper) {
            this.mapper = Objects.requireNonNull(mapper, "mapper");
            return this;
        }

        /**
         * Sets transport options applied to the official client.
         *
         * @param transportOptions transport options
         * @return this builder
         */
        public Builder transportOptions(TransportOptions transportOptions) {
            this.transportOptions = transportOptions;
            return this;
        }

        /**
         * Replaces the low-level REST client builder customizer.
         *
         * @param customizer builder customizer
         * @return this builder
         */
        public Builder restClientBuilderCustomizer(Consumer<RestClientBuilder> customizer) {
            this.restClientBuilderCustomizer = Objects.requireNonNull(customizer, "customizer");
            return this;
        }

        /**
         * Appends a low-level REST client builder customizer.
         *
         * @param customizer builder customizer to append
         * @return this builder
         */
        public Builder customizeRestClientBuilder(Consumer<RestClientBuilder> customizer) {
            Objects.requireNonNull(customizer, "customizer");
            this.restClientBuilderCustomizer = this.restClientBuilderCustomizer.andThen(customizer);
            return this;
        }

        /**
         * Builds the immutable configuration.
         *
         * @return built configuration
         */
        public ElasticJavaClientConfig build() {
            return new ElasticJavaClientConfig(mapper, transportOptions, restClientBuilderCustomizer);
        }
    }
}
