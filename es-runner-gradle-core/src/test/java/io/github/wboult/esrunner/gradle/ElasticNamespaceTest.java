package io.github.wboult.esrunner.gradle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElasticNamespaceTest {
    @Test
    void suiteNamespaceUsesBuildProjectAndTask() {
        String namespace = ElasticNamespace.namespace("er123", ":search-api", "integrationTest", NamespaceMode.SUITE);

        assertEquals("er123_search_api_integrationtest", namespace);
    }

    @Test
    void projectNamespaceOmitsTask() {
        String namespace = ElasticNamespace.namespace("er123", ":catalog", "smokeTest", NamespaceMode.PROJECT);

        assertEquals("er123_catalog", namespace);
    }

    @Test
    void sanitizesNonAlphaNumericContentAndFallsBackToRoot() {
        assertEquals("root", ElasticNamespace.sanitize("::"));
        assertEquals("order_projection", ElasticNamespace.sanitize("Order Projection"));
    }
}
