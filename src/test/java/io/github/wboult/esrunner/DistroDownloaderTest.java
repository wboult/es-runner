package io.github.wboult.esrunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistroDownloaderTest {

    @Test
    void rejectsS3Scheme(@TempDir Path dir) {
        URI uri = URI.create("s3://elastic-mirror/releases/elasticsearch.zip");
        DistroDownloadException ex = assertThrows(DistroDownloadException.class,
                () -> DistroDownloader.download(uri, dir.resolve("elasticsearch.zip")));
        assertEquals(ElasticRunnerException.Kind.DISTRO_DOWNLOAD, ex.kind());
        assertTrue(ex.getMessage().contains("s3"), "error should name the offending scheme");
    }

    @Test
    void rejectsGcsScheme(@TempDir Path dir) {
        URI uri = URI.create("gs://elastic-mirror/releases/elasticsearch.zip");
        DistroDownloadException ex = assertThrows(DistroDownloadException.class,
                () -> DistroDownloader.download(uri, dir.resolve("elasticsearch.zip")));
        assertEquals(ElasticRunnerException.Kind.DISTRO_DOWNLOAD, ex.kind());
        assertTrue(ex.getMessage().contains("gs"), "error should name the offending scheme");
    }

    @Test
    void rejectsAzureScheme(@TempDir Path dir) {
        URI uri = URI.create("az://acct123/releases/elasticsearch.zip");
        DistroDownloadException ex = assertThrows(DistroDownloadException.class,
                () -> DistroDownloader.download(uri, dir.resolve("elasticsearch.zip")));
        assertEquals(ElasticRunnerException.Kind.DISTRO_DOWNLOAD, ex.kind());
        assertTrue(ex.getMessage().contains("az"), "error should name the offending scheme");
    }

    @Test
    void rejectsUnknownScheme(@TempDir Path dir) {
        URI uri = URI.create("ftp://example.com/elasticsearch.zip");
        DistroDownloadException ex = assertThrows(DistroDownloadException.class,
                () -> DistroDownloader.download(uri, dir.resolve("elasticsearch.zip")));
        assertEquals(ElasticRunnerException.Kind.DISTRO_DOWNLOAD, ex.kind());
    }
}
