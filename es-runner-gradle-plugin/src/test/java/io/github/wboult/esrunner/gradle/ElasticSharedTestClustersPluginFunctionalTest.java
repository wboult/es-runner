package io.github.wboult.esrunner.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticSharedTestClustersPluginFunctionalTest {
    private static final String FIXTURE_ROOT = "fixtures/shared-cluster-multiproject";

    Path projectDir;

    @Test
    void sharesOneEs9ClusterAcrossProjectsAndSuites() throws IOException {
        projectDir = Files.createTempDirectory("es-runner-gradle-plugin-it");
        boolean success = false;
        try {
            Path repoRoot = repoRoot();
            Path distroArchive = localElasticsearch9Archive(repoRoot);
            Assumptions.assumeTrue(distroArchive != null, "Local Elasticsearch 9 distro archive not found");

            copyFixture(projectDir, Map.of(
                    "@REPO_ROOT@", repoRoot.toString().replace("\\", "/"),
                    "@DISTRO_ZIP@", distroArchive.toAbsolutePath().toString().replace("\\", "\\\\"),
                    "@TEST_SUPPORT_VERSION@", rootVersion(repoRoot)
            ));

            BuildResult result = GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withArguments(
                            ":app:integrationTest",
                            ":app:smokeTest",
                            ":search:integrationTest",
                            ":search:smokeTest",
                            "--stacktrace"
                    )
                    .withPluginClasspath()
                    .forwardOutput()
                    .build();

            assertEquals(SUCCESS, result.task(":app:integrationTest").getOutcome());
            assertEquals(SUCCESS, result.task(":app:smokeTest").getOutcome());
            assertEquals(SUCCESS, result.task(":search:integrationTest").getOutcome());
            assertEquals(SUCCESS, result.task(":search:smokeTest").getOutcome());

            List<Properties> metadata = List.of(
                    loadProperties(projectDir.resolve("app/build/es-runner/app-integration.properties")),
                    loadProperties(projectDir.resolve("app/build/es-runner/app-smoke.properties")),
                    loadProperties(projectDir.resolve("search/build/es-runner/search-integration.properties")),
                    loadProperties(projectDir.resolve("search/build/es-runner/search-smoke.properties"))
            );

            String sharedBaseUri = metadata.get(0).getProperty("baseUri");
            metadata.forEach(properties -> {
                assertEquals("shared-es9", properties.getProperty("clusterName"));
                assertEquals(sharedBaseUri, properties.getProperty("baseUri"));
            });

            List<String> namespaces = metadata.stream()
                    .map(properties -> properties.getProperty("namespace"))
                    .toList();
            assertEquals(4, namespaces.stream().distinct().count(), "suite namespaces should all differ");

            assertTrue(metadata.get(0).getProperty("namespace").contains("app_integrationtest"));
            assertTrue(metadata.get(1).getProperty("namespace").contains("app_smoketest"));
            assertTrue(metadata.get(2).getProperty("namespace").contains("search_integrationtest"));
            assertTrue(metadata.get(3).getProperty("namespace").contains("search_smoketest"));

            assertNotEquals(
                    metadata.get(0).getProperty("namespace"),
                    metadata.get(1).getProperty("namespace"),
                    "app integration and smoke suites should not share a namespace"
            );
            assertNotEquals(
                    metadata.get(2).getProperty("namespace"),
                    metadata.get(3).getProperty("namespace"),
                    "search integration and smoke suites should not share a namespace"
            );

            success = true;
        } finally {
            if (success) {
                deleteTempDir(projectDir);
            } else {
                System.err.println("Preserving failed fixture at " + projectDir);
            }
        }
    }

    private Path repoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))
                    && Files.isDirectory(candidate.resolve("es-runner-gradle-plugin"))
                    && Files.isDirectory(candidate.resolve("es-runner-gradle-test-support"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to locate repo root from " + current);
    }

    private Path localElasticsearch9Archive(Path repoRoot) throws IOException {
        List<Path> candidates = new ArrayList<>();
        for (Path candidate = repoRoot; candidate != null; candidate = candidate.getParent()) {
            Path distros = candidate.resolve("distros");
            if (!Files.isDirectory(distros)) {
                continue;
            }
            try (var stream = Files.walk(distros, 2)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return (name.endsWith(".zip") || name.endsWith(".tar.gz"))
                                    && name.contains("elasticsearch-9.3.1");
                        })
                        .forEach(candidates::add);
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .findFirst()
                .orElse(null);
    }

    private String rootVersion(Path repoRoot) throws IOException {
        String buildScript = Files.readString(repoRoot.resolve("build.gradle"));
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("version\\s*=\\s*'([^']+)'")
                .matcher(buildScript);
        if (!matcher.find()) {
            throw new IllegalStateException("Unable to determine root version from build.gradle");
        }
        return matcher.group(1);
    }

    private void copyFixture(Path targetRoot, Map<String, String> replacements) throws IOException {
        Path fixtureRoot = repoRoot()
                .resolve("es-runner-gradle-plugin")
                .resolve("src/test/resources")
                .resolve(FIXTURE_ROOT);
        Files.walkFileTree(fixtureRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(targetRoot.resolve(fixtureRoot.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path target = targetRoot.resolve(fixtureRoot.relativize(file).toString());
                String content = Files.readString(file);
                for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                    content = content.replace(replacement.getKey(), replacement.getValue());
                }
                Files.writeString(target, content, StandardCharsets.UTF_8);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private void deleteTempDir(Path dir) {
        if (dir == null) {
            return;
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            if (!Files.exists(dir)) {
                return;
            }
            try {
                Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException exc)
                            throws IOException {
                        Files.deleteIfExists(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
                return;
            } catch (IOException ignored) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
