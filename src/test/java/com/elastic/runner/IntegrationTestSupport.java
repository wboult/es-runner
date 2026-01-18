package com.elastic.runner;

import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class IntegrationTestSupport {
    private static final Map<String, Boolean> DOWNLOAD_AVAILABILITY = new ConcurrentHashMap<>();

    private IntegrationTestSupport() {
    }

    static ElasticRunnerConfig config(String version,
                                      Path workDir,
                                      String clusterName,
                                      String heap,
                                      Duration startupTimeout,
                                      boolean quiet) {
        String zipPath = System.getenv("ES_DISTRO_ZIP");
        boolean download = Boolean.parseBoolean(System.getenv().getOrDefault("ES_DISTRO_DOWNLOAD", "false"));
        Path distrosDir = Path.of(System.getenv().getOrDefault("ES_DISTROS_DIR", "distros"));
        String baseUrl = System.getenv("ES_DISTRO_BASE_URL");

        ElasticRunnerConfig base = ElasticRunnerConfig.defaults()
                .withWorkDir(workDir)
                .withClusterName(clusterName)
                .withHeap(heap)
                .withStartupTimeout(startupTimeout)
                .withQuiet(quiet);

        if (zipPath != null && !zipPath.isBlank()) {
            Path zip = Path.of(zipPath);
            Assumptions.assumeTrue(Files.exists(zip), "Elasticsearch distro ZIP not found");
            return base.withDistroZip(zip);
        }

        DistroDescriptor descriptor = DistroDescriptor.forVersion(version);
        Path localZip = distrosDir.resolve(descriptor.fileName());
        if (Files.exists(localZip)) {
            return base.withDistroZip(localZip);
        }

        if (download) {
            assumeDownloadAvailable(version, baseUrl);
            ElasticRunnerConfig config = base.withVersion(version)
                    .withDistrosDir(distrosDir)
                    .withDownload(true);
            if (baseUrl != null && !baseUrl.isBlank()) {
                config = config.withDownloadBaseUrl(baseUrl);
            }
            return config;
        }

        Assumptions.assumeTrue(false, "Elasticsearch distro ZIP not found and downloads disabled");
        return base;
    }

    static ElasticRunnerConfig configFromExample(String version,
                                                 Path workDir,
                                                 String clusterName,
                                                 Duration startupTimeout,
                                                 boolean quiet,
                                                 Consumer<ElasticRunnerConfig.Builder> customize) {
        String zipPath = System.getenv("ES_DISTRO_ZIP");
        boolean download = Boolean.parseBoolean(System.getenv().getOrDefault("ES_DISTRO_DOWNLOAD", "false"));
        Path distrosDir = Path.of(System.getenv().getOrDefault("ES_DISTROS_DIR", "distros"));
        String baseUrl = System.getenv("ES_DISTRO_BASE_URL");

        Path localZip = null;
        if (zipPath == null || zipPath.isBlank()) {
            DistroDescriptor descriptor = DistroDescriptor.forVersion(version);
            localZip = distrosDir.resolve(descriptor.fileName());
            if (!download && !Files.exists(localZip)) {
                Assumptions.assumeTrue(false, "Elasticsearch distro ZIP not found and downloads disabled");
            }
        }

        Path distroZip = null;
        if (zipPath != null && !zipPath.isBlank()) {
            distroZip = Path.of(zipPath);
            Assumptions.assumeTrue(Files.exists(distroZip), "Elasticsearch distro ZIP not found");
        } else if (localZip != null && Files.exists(localZip)) {
            distroZip = localZip;
        }

        Path finalDistroZip = distroZip;
        Path finalLocalZip = localZip;
        return ElasticRunnerConfig.from(builder -> {
            builder.workDir(workDir)
                    .clusterName(clusterName)
                    .startupTimeout(startupTimeout)
                    .quiet(quiet);
            if (finalDistroZip != null) {
                builder.distroZip(finalDistroZip);
            } else {
                assumeDownloadAvailable(version, baseUrl);
                builder.version(version)
                        .distrosDir(distrosDir)
                        .download(download);
                if (baseUrl != null && !baseUrl.isBlank()) {
                    builder.downloadBaseUrl(baseUrl);
                }
            }
            if (customize != null) {
                customize.accept(builder);
            }
        });
    }

    private static void assumeDownloadAvailable(String version, String baseUrl) {
        String effectiveBaseUrl = (baseUrl == null || baseUrl.isBlank())
                ? ElasticRunnerConfig.defaults().downloadBaseUrl()
                : baseUrl;
        String key = version + "|" + effectiveBaseUrl;
        Boolean available = DOWNLOAD_AVAILABILITY.get(key);
        if (available == null) {
            available = checkDownloadAvailable(version, effectiveBaseUrl);
            DOWNLOAD_AVAILABILITY.put(key, available);
        }
        Assumptions.assumeTrue(available, "Elasticsearch distro not found at " + effectiveBaseUrl);
    }

    private static boolean checkDownloadAvailable(String version, String baseUrl) {
        try {
            DistroDescriptor descriptor = DistroDescriptor.forVersion(version);
            URI uri = descriptor.downloadUri(baseUrl);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<Void> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() != 404;
        } catch (Exception ignored) {
            return true;
        }
    }
}
