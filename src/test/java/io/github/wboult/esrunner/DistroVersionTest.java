package io.github.wboult.esrunner;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistroVersionTest {

    @Test
    void extractsElasticsearchVersionFromArchiveName() {
        assertEquals("9.3.1", DistroVersion.fromZip(Path.of("elasticsearch-9.3.1-windows-x86_64.zip")));
    }

    @Test
    void extractsOpenSearchVersionFromArchiveName() {
        assertEquals("3.5.0", DistroVersion.fromZip(Path.of("opensearch-3.5.0-windows-x64.zip")));
    }
}
