package io.github.wboult.esrunner.gradle.docker;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
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
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerSharedTestClustersPluginFunctionalTest {
    private static final String FIXTURE_ROOT = "fixtures/docker-shared-cluster-multiproject";

    @Test
    void doesNotStartClusterWhenBuildRunsNoBoundTestTask() throws IOException {
        Assumptions.assumeTrue(linuxDockerAvailable(),
                "Docker plugin functional tests require Linux with a working Docker daemon");
        Path projectDir = Files.createTempDirectory("es-runner-docker-plugin-it");
        boolean success = false;
        try {
            Path repoRoot = repoRoot();
            copyFixture(projectDir, Map.of(
                    "@REPO_ROOT@", repoRoot.toString().replace("\\", "/"),
                    "@TEST_SUPPORT_VERSION@", rootVersion(repoRoot)
            ));

            BuildResult result = runBuild(projectDir, ":app:classes");

            TaskOutcome outcome = result.task(":app:classes").getOutcome();
            assertTrue(outcome == SUCCESS || outcome == TaskOutcome.UP_TO_DATE);
            assertFalse(result.getOutput().contains("Shared Docker cluster 'sharedEsDocker' started"),
                    "docker cluster should stay cold when no bound test task runs");
            success = true;
        } finally {
            if (success) {
                deleteTempDir(projectDir);
            } else {
                System.err.println("Preserving failed fixture at " + projectDir);
            }
        }
    }

    @Test
    void sharesOneDockerEsClusterAcrossProjectsAndSuites() throws IOException {
        Assumptions.assumeTrue(linuxDockerAvailable(),
                "Docker plugin functional tests require Linux with a working Docker daemon");
        Path projectDir = Files.createTempDirectory("es-runner-docker-plugin-it");
        boolean success = false;
        try {
            Path repoRoot = repoRoot();
            copyFixture(projectDir, Map.of(
                    "@REPO_ROOT@", repoRoot.toString().replace("\\", "/"),
                    "@TEST_SUPPORT_VERSION@", rootVersion(repoRoot)
            ));

            BuildResult result = runBuild(projectDir,
                    ":app:integrationTest",
                    ":app:smokeTest",
                    ":search:integrationTest",
                    ":search:smokeTest");

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
                assertEquals("shared-es-docker", properties.getProperty("clusterName"));
                assertEquals(sharedBaseUri, properties.getProperty("baseUri"));
            });

            List<String> namespaces = metadata.stream()
                    .map(properties -> properties.getProperty("namespace"))
                    .toList();
            assertEquals(4, namespaces.stream().distinct().count());
            assertNotEquals(metadata.get(0).getProperty("namespace"), metadata.get(1).getProperty("namespace"));
            assertNotEquals(metadata.get(2).getProperty("namespace"), metadata.get(3).getProperty("namespace"));
            assertTrue(metadata.get(0).getProperty("namespace").contains("app_integrationtest"));
            assertTrue(metadata.get(3).getProperty("namespace").contains("search_smoketest"));

            success = true;
        } finally {
            if (success) {
                deleteTempDir(projectDir);
            } else {
                System.err.println("Preserving failed fixture at " + projectDir);
            }
        }
    }

    private boolean linuxDockerAvailable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("docker", "info")
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, String> gradleEnvironment() {
        Map<String, String> environment = new LinkedHashMap<>(System.getenv());
        environment.remove("DOCKER_HOST");
        environment.remove("TESTCONTAINERS_DOCKER_CLIENT_STRATEGY");
        environment.remove("TESTCONTAINERS_HOST_OVERRIDE");
        environment.remove("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE");
        environment.put("TESTCONTAINERS_REUSE_ENABLE", "false");
        return environment;
    }

    private List<String> gradleArguments(String... tasks) {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(
                        "-Dorg.gradle.java.installations.paths=" + currentJavaHome()
                ),
                java.util.Arrays.stream(tasks)
        ).collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toList(),
                arguments -> {
                    arguments.add("--stacktrace");
                    return arguments;
                }
        ));
    }

    private String currentJavaHome() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            return javaHome;
        }
        return System.getProperty("java.home");
    }

    private Path repoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))
                    && Files.isDirectory(candidate.resolve("es-runner-gradle-plugin-docker"))
                    && Files.isDirectory(candidate.resolve("es-runner-gradle-test-support"))) {
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

    private BuildResult runBuild(Path projectDir, String... tasks) {
        try {
            return GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withArguments(gradleArguments(tasks))
                    .withEnvironment(gradleEnvironment())
                    .withPluginClasspath()
                    .forwardOutput()
                    .build();
        } catch (UnexpectedBuildFailure failure) {
            System.err.println("Nested fixture build failed for " + projectDir + ":");
            System.err.println(failure.getBuildResult().getOutput());
            throw failure;
        }
    }

    private void copyFixture(Path targetRoot, Map<String, String> replacements) throws IOException {
        Path fixtureRoot = repoRoot()
                .resolve("es-runner-gradle-plugin-docker")
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
