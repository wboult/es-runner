package com.elastic.runner;

import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

final class IntegrationTestSupport {
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
        if (Files.exists(localZip) && !download) {
            return base.withDistroZip(localZip);
        }

        if (download) {
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
}
