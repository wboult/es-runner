package io.github.wboult.esrunner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

final class DistroDownloader {
    private DistroDownloader() {
    }

    static void download(URI uri, Path target) {
        Path temp = target.resolveSibling(target.getFileName() + ".partial");
        try {
            Files.createDirectories(target.getParent());
            switch (normalizedScheme(uri)) {
                case "http":
                case "https":
                    downloadHttp(uri, temp);
                    break;
                case "file":
                    Files.copy(Path.of(uri), temp, StandardCopyOption.REPLACE_EXISTING);
                    break;
                case "s3":
                case "gs":
                case "az":
                    throw new ElasticRunnerException(
                            "Cloud storage scheme '" + uri.getScheme() + "' is not supported. "
                            + "Use an HTTPS pre-signed or SAS URL instead (these work with the built-in "
                            + "https:// downloader). See docs/cloud-storage-extension-plan.md for the "
                            + "planned design for proper SDK-based cloud storage modules.");
                default:
                    throw new ElasticRunnerException("Unsupported download URI scheme: " + uri);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ElasticRunnerException("Failed to download distro from " + uri, e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    private static void downloadHttp(URI uri, Path target) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ElasticRunnerException("Failed to download distro: " + uri + " (HTTP " + response.statusCode() + ")");
        }
    }

    private static String normalizedScheme(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String scheme = uri.getScheme();
        if (scheme == null || scheme.isBlank()) {
            return "http";
        }
        return scheme.toLowerCase(Locale.ROOT);
    }
}
