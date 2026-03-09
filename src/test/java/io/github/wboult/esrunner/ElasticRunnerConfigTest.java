package io.github.wboult.esrunner;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticRunnerConfigTest {

    @Test
    void defaultsIncludeSafetySettings() {
        ElasticRunnerConfig config = ElasticRunnerConfig.defaults();
        assertEquals(DistroFamily.ELASTICSEARCH, config.family());
        assertEquals("single-node", config.settings().get("discovery.type"));
        assertEquals("false", config.settings().get("xpack.security.enabled"));
        assertEquals("https://artifacts.elastic.co/downloads/elasticsearch/", config.downloadBaseUrl());
    }

    @Test
    void openSearchDefaultsUseOpenSearchSettingsAndDownloads() {
        ElasticRunnerConfig config = ElasticRunnerConfig.defaults(DistroFamily.OPENSEARCH);

        assertEquals(DistroFamily.OPENSEARCH, config.family());
        assertEquals("single-node", config.settings().get("discovery.type"));
        assertEquals("true", config.settings().get("plugins.security.disabled"));
        assertEquals("https://artifacts.opensearch.org/releases/bundle/opensearch/", config.downloadBaseUrl());
    }

    @Test
    void switchingFamilyRewritesUntouchedDefaults() {
        ElasticRunnerConfig config = ElasticRunnerConfig.defaults()
                .toBuilder()
                .family(DistroFamily.OPENSEARCH)
                .build();

        assertEquals(DistroFamily.OPENSEARCH, config.family());
        assertEquals("https://artifacts.opensearch.org/releases/bundle/opensearch/", config.downloadBaseUrl());
        assertFalse(config.settings().containsKey("xpack.security.enabled"));
        assertEquals("true", config.settings().get("plugins.security.disabled"));
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
                .family(DistroFamily.OPENSEARCH)
                .distroZip(Path.of("es.zip"))
                .version("3.5.0")
                .clusterName("builder-cluster")
                .httpPort(9250)
                .setting("node.name", "builder-node"));

        assertEquals(DistroFamily.OPENSEARCH, config.family());
        assertEquals("builder-cluster", config.clusterName());
        assertEquals(9250, config.httpPort());
        assertEquals("builder-node", config.settings().get("node.name"));
        assertEquals("3.5.0", config.version());
    }
}
