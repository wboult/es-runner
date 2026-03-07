package com.elastic.runner.gradle.testsupport;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElasticGradleTestEnvTest {

    @Test
    void buildsNamespacedResourceNamesFromSystemProperties() {
        ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties(Map.of(
                "elastic.runner.baseUri", "http://localhost:9211/",
                "elastic.runner.httpPort", "9211",
                "elastic.runner.clusterName", "integration",
                "elastic.runner.buildId", "er_build",
                "elastic.runner.suiteId", ":app:integrationTest",
                "elastic.runner.namespace", "er_build_app_integrationtest"
        ));

        assertEquals("http://localhost:9211/", env.baseUri().toString());
        assertEquals("er_build_app_integrationtest-orders", env.index("Orders"));
        assertEquals("er_build_app_integrationtest-template-1", env.template("template 1"));
    }
}
