package com.elastic.runner;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticRunnerConfigTest {

    @Test
    void defaultsIncludeSafetySettings() {
        ElasticRunnerConfig config = ElasticRunnerConfig.defaults();
        assertEquals("single-node", config.settings().get("discovery.type"));
        assertEquals("false", config.settings().get("xpack.security.enabled"));
        assertEquals("https://artifacts.elastic.co/downloads/elasticsearch/", config.downloadBaseUrl());
    }

    @Test
    void withSettingIsPure() {
        ElasticRunnerConfig base = ElasticRunnerConfig.defaults();
        ElasticRunnerConfig updated = base.toBuilder().setting("cluster.name", "unit-test").build();

        assertFalse(base.settings().containsKey("cluster.name"));
        assertEquals("unit-test", updated.settings().get("cluster.name"));
    }

    @Test
    void toBuilderAppliesChanges() {
        ElasticRunnerConfig config = ElasticRunnerConfig.defaults()
                .toBuilder().distroZip(Path.of("es.zip")).clusterName("dsl").build();

        assertEquals("dsl", config.clusterName());
        assertEquals(Path.of("es.zip"), config.distroZip());
    }

    @Test
    void builderDslIsUsable() {
        ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
                .distroZip(Path.of("es.zip"))
                .version("9.2.4")
                .clusterName("builder-cluster")
                .httpPort(9250)
                .setting("node.name", "builder-node"));

        assertEquals("builder-cluster", config.clusterName());
        assertEquals(9250, config.httpPort());
        assertEquals("builder-node", config.settings().get("node.name"));
        assertEquals("9.2.4", config.version());
    }
}
