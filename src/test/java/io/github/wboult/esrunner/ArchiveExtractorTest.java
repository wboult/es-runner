package io.github.wboult.esrunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchiveExtractorTest {
    @Test
    void extractsZipArchives(@TempDir Path tempDir) throws IOException {
        Path archive = TestArchives.zipWithEntries(
                tempDir.resolve("elasticsearch.zip"),
                Map.of("elasticsearch-9.3.1/config/elasticsearch.yml", "cluster.name: zip-test")
        );
        Path target = tempDir.resolve("zip");

        ArchiveExtractor.extract(archive, target);

        assertEquals(
                "cluster.name: zip-test",
                Files.readString(target.resolve("elasticsearch-9.3.1/config/elasticsearch.yml"))
        );
    }

    @Test
    void extractsTarGzArchives(@TempDir Path tempDir) throws IOException {
        Path archive = TestArchives.tarGzWithEntries(
                tempDir.resolve("elasticsearch.tar.gz"),
                Map.of("elasticsearch-9.3.1/config/elasticsearch.yml", "cluster.name: tar-test")
        );
        Path target = tempDir.resolve("tar");

        ArchiveExtractor.extract(archive, target);

        assertEquals(
                "cluster.name: tar-test",
                Files.readString(target.resolve("elasticsearch-9.3.1/config/elasticsearch.yml"))
        );
    }

    @Test
    void rejectsZipSlipEntries(@TempDir Path tempDir) throws IOException {
        Path archive = TestArchives.zipWithEntries(
                tempDir.resolve("escape.zip"),
                Map.of("../escape.txt", "nope")
        );

        IOException ex = assertThrows(IOException.class,
                () -> ArchiveExtractor.extract(archive, tempDir.resolve("target")));

        assertEquals("Zip entry is outside target dir: ../escape.txt", ex.getMessage());
    }

    @Test
    void rejectsTarEntriesOutsideTargetDir(@TempDir Path tempDir) throws IOException {
        Path archive = TestArchives.tarGzWithEntries(
                tempDir.resolve("escape.tar.gz"),
                Map.of("../escape.txt", "nope")
        );

        IOException ex = assertThrows(IOException.class,
                () -> ArchiveExtractor.extract(archive, tempDir.resolve("target")));

        assertEquals("Tar entry is outside target dir: ../escape.txt", ex.getMessage());
    }
}
