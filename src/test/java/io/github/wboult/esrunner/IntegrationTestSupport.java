package io.github.wboult.esrunner;

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
        return config(DistroFamily.ELASTICSEARCH, version, workDir, clusterName, heap, startupTimeout, quiet);
    }

    static ElasticRunnerConfig config(DistroFamily family,
                                      String version,
                                      Path workDir,
                                      String clusterName,
                                      String heap,
                                      Duration startupTimeout,
                                      boolean quiet) {
        String prefix = envPrefix(family);
        String zipPath = trimToNull(System.getenv(prefix + "_DISTRO_ZIP"));
        boolean download = Boolean.parseBoolean(System.getenv().getOrDefault(prefix + "_DISTRO_DOWNLOAD", "false"));
        Path distrosDir = Path.of(System.getenv().getOrDefault(prefix + "_DISTROS_DIR", "distros").trim());
        String baseUrl = trimToNull(System.getenv(prefix + "_DISTRO_BASE_URL"));

        ElasticRunnerConfig base = ElasticRunnerConfig.defaults(family).toBuilder()
                .workDir(workDir)
                .clusterName(clusterName)
                .heap(heap)
                .startupTimeout(startupTimeout)
                .quiet(quiet)
                .build();

        if (zipPath != null) {
            Path zip = Path.of(zipPath);
            Assumptions.assumeTrue(Files.exists(zip), family.displayName() + " distro ZIP not found");
            return base.toBuilder().distroZip(zip).build();
        }

        DistroDescriptor descriptor = DistroDescriptor.forVersion(family, version);
        Path localZip = distrosDir.resolve(descriptor.fileName());
        if (Files.exists(localZip)) {
            return base.toBuilder().distroZip(localZip).build();
        }

        if (download) {
            assumeDownloadAvailable(family, version, baseUrl);
            ElasticRunnerConfig config = base.toBuilder().version(version)
                    .distrosDir(distrosDir)
                    .download(true).build();
            if (baseUrl != null) {
                config = config.toBuilder().downloadBaseUrl(baseUrl).build();
            }
            return config;
        }

        Assumptions.assumeTrue(false, family.displayName() + " distro ZIP not found and downloads disabled");
        return base;
    }

    static ElasticRunnerConfig configFromExample(String version,
                                                 Path workDir,
                                                 String clusterName,
                                                 Duration startupTimeout,
                                                 boolean quiet,
                                                 Consumer<ElasticRunnerConfig.Builder> customize) {
        return configFromExample(
                DistroFamily.ELASTICSEARCH,
                version,
                workDir,
                clusterName,
                startupTimeout,
                quiet,
                customize
        );
    }

    static ElasticRunnerConfig configFromExample(DistroFamily family,
                                                 String version,
                                                 Path workDir,
                                                 String clusterName,
                                                 Duration startupTimeout,
                                                 boolean quiet,
                                                 Consumer<ElasticRunnerConfig.Builder> customize) {
        String prefix = envPrefix(family);
        String zipPath = trimToNull(System.getenv(prefix + "_DISTRO_ZIP"));
        boolean download = Boolean.parseBoolean(System.getenv().getOrDefault(prefix + "_DISTRO_DOWNLOAD", "false"));
        Path distrosDir = Path.of(System.getenv().getOrDefault(prefix + "_DISTROS_DIR", "distros").trim());
        String baseUrl = trimToNull(System.getenv(prefix + "_DISTRO_BASE_URL"));

        Path localZip = null;
        if (zipPath == null) {
            DistroDescriptor descriptor = DistroDescriptor.forVersion(family, version);
            localZip = distrosDir.resolve(descriptor.fileName());
            if (!download && !Files.exists(localZip)) {
                Assumptions.assumeTrue(false, family.displayName() + " distro ZIP not found and downloads disabled");
            }
        }

        Path distroZip = null;
        if (zipPath != null) {
            distroZip = Path.of(zipPath);
            Assumptions.assumeTrue(Files.exists(distroZip), family.displayName() + " distro ZIP not found");
        } else if (localZip != null && Files.exists(localZip)) {
            distroZip = localZip;
        }

        Path finalDistroZip = distroZip;
        Path finalLocalZip = localZip;
        return ElasticRunnerConfig.from(builder -> {
            builder.family(family)
                    .workDir(workDir)
                    .clusterName(clusterName)
                    .startupTimeout(startupTimeout)
                    .quiet(quiet);
            if (finalDistroZip != null) {
                builder.distroZip(finalDistroZip);
            } else {
                assumeDownloadAvailable(family, version, baseUrl);
                builder.version(version)
                        .distrosDir(distrosDir)
                        .download(download);
                if (baseUrl != null) {
                    builder.downloadBaseUrl(baseUrl);
                }
            }
            if (customize != null) {
                customize.accept(builder);
            }
        });
    }

    private static void assumeDownloadAvailable(String version, String baseUrl) {
        assumeDownloadAvailable(DistroFamily.ELASTICSEARCH, version, baseUrl);
    }

    private static void assumeDownloadAvailable(DistroFamily family, String version, String baseUrl) {
        String effectiveBaseUrl = (baseUrl == null || baseUrl.isBlank())
                ? ElasticRunnerConfig.defaults(family).downloadBaseUrl()
                : baseUrl;
        String key = family.name() + "|" + version + "|" + effectiveBaseUrl;
        Boolean available = DOWNLOAD_AVAILABILITY.get(key);
        if (available == null) {
            available = checkDownloadAvailable(family, version, effectiveBaseUrl);
            DOWNLOAD_AVAILABILITY.put(key, available);
        }
        Assumptions.assumeTrue(available, family.displayName() + " distro not found at " + effectiveBaseUrl);
    }

    private static boolean checkDownloadAvailable(DistroFamily family, String version, String baseUrl) {
        try {
            DistroDescriptor descriptor = DistroDescriptor.forVersion(family, version);
            URI uri = descriptor.downloadUri(baseUrl);
            String scheme = uri.getScheme();
            if ("file".equalsIgnoreCase(scheme)) {
                return Files.exists(Path.of(uri));
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return true;
            }
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

    private static String envPrefix(DistroFamily family) {
        return family == DistroFamily.OPENSEARCH ? "OPENSEARCH" : "ES";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
