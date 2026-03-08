package io.github.wboult.esrunner;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigWriterTest {

    @Test
    void writesSettingsFile() throws Exception {
        Path tempDir = Files.createTempDirectory("es-config-test");
        Path configFile = tempDir.resolve("elasticsearch.yml");

        ConfigWriter.write(configFile, Map.of(
                "cluster.name", "unit-test",
                "http.port", "9200"
        ));

        String content = Files.readString(configFile);
        assertTrue(content.contains("cluster.name: unit-test"));
        assertTrue(content.contains("http.port: 9200"));
    }
}
