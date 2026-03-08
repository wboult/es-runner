package io.github.wboult.esrunner;

import java.util.List;
import java.util.Map;

/**
 * Simple HTTP response wrapper returned by low-level client request methods.
 *
 * @param statusCode HTTP status code
 * @param body response body
 * @param headers response headers grouped by name
 */
public record ElasticResponse(int statusCode, String body, Map<String, List<String>> headers) {
    /**
     * Returns whether the response uses a successful 2xx status code.
     *
     * @return {@code true} for HTTP 2xx responses
     */
    public boolean ok() {
        return statusCode >= 200 && statusCode < 300;
    }
}
