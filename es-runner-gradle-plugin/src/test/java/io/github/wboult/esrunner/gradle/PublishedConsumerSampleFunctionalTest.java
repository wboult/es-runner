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
import java.util.List;
import java.util.Properties;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishedConsumerSampleFunctionalTest {
    private static final String SAMPLE_ROOT = "samples/gradle-shared-cluster-automation-harness";

    @Test
    void publishedArtifactSampleBuildSharesOneClusterAcrossProjectsAndSuites() throws IOException {
        Path sampleDir = Files.createTempDirectory("es-runner-automation-harness");
        Path sampleRepo = Files.createTempDirectory("es-runner-sample-repo");
        boolean success = false;
        try {
            Path repoRoot = repoRoot();
            Path distroArchive = TestDistroArchives.localElasticsearch9Archive(repoRoot);
            Assumptions.assumeTrue(distroArchive != null, "Local Elasticsearch 9 distro archive not found");

            copyDirectory(repoRoot.resolve(SAMPLE_ROOT), sampleDir);
            publishArtifacts(repoRoot, sampleRepo);

            BuildResult result = GradleRunner.create()
                    .withProjectDir(sampleDir.toFile())
                    .withArguments(
                            ":app:check",
                            ":search:check",
                            "-PesRunnerVersion=" + rootVersion(repoRoot),
                            "-PesRunnerRepositoryUrl=" + sampleRepo.toUri(),
                            "-PesDistroZip=" + distroArchive.toAbsolutePath(),
                            "--stacktrace"
                    )
                    .forwardOutput()
                    .build();

            assertEquals(SUCCESS, result.task(":app:integrationTest").getOutcome());
            assertEquals(SUCCESS, result.task(":app:smokeTest").getOutcome());
            assertEquals(SUCCESS, result.task(":search:integrationTest").getOutcome());
            assertEquals(SUCCESS, result.task(":search:smokeTest").getOutcome());

            List<Properties> metadata = List.of(
                    loadProperties(sampleDir.resolve("app/build/es-runner/app-integration.properties")),
                    loadProperties(sampleDir.resolve("app/build/es-runner/app-smoke.properties")),
                    loadProperties(sampleDir.resolve("search/build/es-runner/search-integration.properties")),
                    loadProperties(sampleDir.resolve("search/build/es-runner/search-smoke.properties"))
            );

            String sharedBaseUri = metadata.get(0).getProperty("baseUri");
            metadata.forEach(properties -> {
                assertEquals("sample-shared-es9", properties.getProperty("clusterName"));
                assertEquals(sharedBaseUri, properties.getProperty("baseUri"));
            });

            assertEquals(4, metadata.stream().map(properties -> properties.getProperty("namespace")).distinct().count());
            assertNotEquals(metadata.get(0).getProperty("namespace"), metadata.get(1).getProperty("namespace"));
            assertNotEquals(metadata.get(2).getProperty("namespace"), metadata.get(3).getProperty("namespace"));
            assertTrue(metadata.get(0).getProperty("namespace").contains("app_integrationtest"));
            assertTrue(metadata.get(3).getProperty("namespace").contains("search_smoketest"));
            assertEquals("3", metadata.get(0).getProperty("seededCount"));
            assertEquals("true", metadata.get(1).getProperty("freshNamespace"));
            assertTrue(metadata.get(2).getProperty("alias").contains("orders-read"));
            assertEquals("404", metadata.get(3).getProperty("rawLogicalIndexStatus"));

            success = true;
        } finally {
            if (success) {
                deleteTempDir(sampleDir);
                deleteTempDir(sampleRepo);
            } else {
                System.err.println("Preserving failed sample fixture at " + sampleDir);
                System.err.println("Preserving failed sample repo at " + sampleRepo);
            }
        }
    }

    private void publishArtifacts(Path repoRoot, Path sampleRepo) {
        BuildResult publishResult = GradleRunner.create()
                .withProjectDir(repoRoot.toFile())
                .withArguments(
                        "publishMavenJavaPublicationToSampleRepoRepository",
                        ":es-runner-gradle-test-support:publishMavenJavaPublicationToSampleRepoRepository",
                        ":es-runner-gradle-plugin:publishPluginMavenPublicationToSampleRepoRepository",
                        ":es-runner-gradle-plugin:publishElasticRunnerSharedTestClustersPluginMarkerMavenPublicationToSampleRepoRepository",
                        "-PsampleRepoUrl=" + sampleRepo.toUri(),
                        "--stacktrace"
                )
                .forwardOutput()
                .build();
        assertEquals(SUCCESS, publishResult.task(":es-runner-gradle-plugin:publishPluginMavenPublicationToSampleRepoRepository").getOutcome());
    }

    private Path repoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))
                    && Files.isDirectory(candidate.resolve("es-runner-gradle-plugin"))
                    && Files.isDirectory(candidate.resolve("samples"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to locate repo root from " + current);
    }

    private String rootVersion(Path repoRoot) throws IOException {
        String buildScript = Files.readString(repoRoot.resolve("build.gradle"));
        java.util.regex.Matcher literalMatcher = java.util.regex.Pattern
                .compile("version\\s*=\\s*'([^']+)'")
                .matcher(buildScript);
        if (literalMatcher.find()) {
            return literalMatcher.group(1);
        }

        java.util.regex.Matcher propertyMatcher = java.util.regex.Pattern
                .compile("version\\s*=\\s*providers\\.gradleProperty\\(\"releaseVersion\"\\)\\.orElse\\(\"([^\"]+)\"\\)\\.get\\(\\)")
                .matcher(buildScript);
        if (propertyMatcher.find()) {
            return propertyMatcher.group(1);
        }

        throw new IllegalStateException("Unable to determine root version from build.gradle");
    }

    private void copyDirectory(Path sourceRoot, Path targetRoot) throws IOException {
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(targetRoot.resolve(sourceRoot.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path target = targetRoot.resolve(sourceRoot.relativize(file).toString());
                String content = Files.readString(file, StandardCharsets.UTF_8);
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
