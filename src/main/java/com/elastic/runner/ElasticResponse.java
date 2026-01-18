package com.elastic.runner;

import java.util.List;
import java.util.Map;

public record ElasticResponse(int statusCode, String body, Map<String, List<String>> headers) {
    public boolean ok() {
        return statusCode >= 200 && statusCode < 300;
    }
}
