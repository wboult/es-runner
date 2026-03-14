package io.github.wboult.esrunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;

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

    @Test
    void httpDownloadRespectsConfiguredTimeout(@TempDir Path dir) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/slow.zip", exchange -> {
            try {
                Thread.sleep(300);
                byte[] body = "zip".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/slow.zip");
            DistroDownloadException ex = assertThrows(
                    DistroDownloadException.class,
                    () -> DistroDownloader.download(uri, dir.resolve("elasticsearch.zip"), Duration.ofMillis(50))
            );
            assertEquals(ElasticRunnerException.Kind.DISTRO_DOWNLOAD, ex.kind());
        } finally {
            server.stop(0);
        }
    }
}
