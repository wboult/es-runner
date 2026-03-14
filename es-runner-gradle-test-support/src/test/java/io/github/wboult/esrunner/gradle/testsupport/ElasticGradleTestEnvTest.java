package io.github.wboult.esrunner.gradle.testsupport;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertEquals("er_build_app_integrationtest-orders-*", env.indexPattern("Orders"));
        assertEquals("er_build_app_integrationtest-template-1", env.template("template 1"));
    }

    @Test
    void rejectsWildcardLikeInputForConcreteNames() {
        ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties(Map.of(
                "elastic.runner.baseUri", "http://localhost:9211/",
                "elastic.runner.httpPort", "9211",
                "elastic.runner.clusterName", "integration",
                "elastic.runner.buildId", "er_build",
                "elastic.runner.suiteId", ":app:integrationTest",
                "elastic.runner.namespace", "er_build_app_integrationtest"
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> env.index("orders-*"));
        assertEquals(
                "index logical names must be concrete. Use indexPattern(...) for wildcard-based index patterns instead: orders-*",
                ex.getMessage()
        );
    }
}
