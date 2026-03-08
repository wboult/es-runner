package io.github.wboult.esrunner.javaclient;

import io.github.wboult.esrunner.ElasticRunnerConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;

final class JavaClientIntegrationTestSupport {
    private JavaClientIntegrationTestSupport() {
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

        ElasticRunnerConfig base = ElasticRunnerConfig.defaults().toBuilder()
                .workDir(workDir)
                .clusterName(clusterName)
                .heap(heap)
                .startupTimeout(startupTimeout)
                .quiet(quiet)
                .build();

        if (zipPath != null && !zipPath.isBlank()) {
            Path zip = Path.of(zipPath);
            Assumptions.assumeTrue(Files.exists(zip), "Elasticsearch distro ZIP not found");
            return base.toBuilder().distroZip(zip).build();
        }

        Path localZip = findLocalZipForVersion(distrosDir, version);
        if (Files.exists(localZip)) {
            return base.toBuilder().distroZip(localZip).build();
        }

        if (download) {
            return base.toBuilder()
                    .version(version)
                    .distrosDir(distrosDir)
                    .download(true)
                    .build();
        }

        Assumptions.assumeTrue(false, "Elasticsearch distro ZIP not found and downloads disabled");
        return base;
    }

    private static Path findLocalZipForVersion(Path distrosDir, String version) {
        if (!Files.isDirectory(distrosDir)) {
            return distrosDir.resolve("missing-" + version + ".zip");
        }
        try (Stream<Path> files = Files.list(distrosDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains(version))
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .findFirst()
                    .orElse(distrosDir.resolve("missing-" + version + ".zip"));
        } catch (IOException e) {
            return distrosDir.resolve("missing-" + version + ".zip");
        }
    }
}
