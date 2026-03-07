package com.elastic.runner.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticSharedTestClustersPluginFunctionalTest {
    Path projectDir;

    @Test
    void sharesOneClusterAcrossProjectsAndInjectsSuiteNamespaces() throws IOException {
        projectDir = Files.createTempDirectory("elastic-runner-gradle-plugin-it");
        try {
            Path distroZip = localDistroZip();
            Assumptions.assumeTrue(distroZip != null, "Local Elasticsearch distro ZIP not found");

            write(projectDir.resolve("settings.gradle"), """
                    rootProject.name = 'fixture'
                    include('app', 'search')

                    dependencyResolutionManagement {
                        repositories {
                            mavenCentral()
                        }
                    }
                    """);
            write(projectDir.resolve("gradle.properties"), """
                    org.gradle.daemon=false
                    org.gradle.vfs.watch=false
                    """);

            String escapedZip = distroZip.toAbsolutePath().toString().replace("\\", "\\\\");
            String buildGradle = """
                    import org.gradle.api.plugins.jvm.JvmTestSuite

                    plugins {
                        id 'com.elastic.runner.shared-test-clusters'
                    }

                    elasticTestClusters {
                        clusters {
                            register("integration") {
                                distroZip('%s')
                                clusterName.set("shared-it")
                                startupTimeoutMillis.set(180_000L)
                                quiet.set(true)
                            }
                        }
                        suites {
                            matchingName("integrationTest") {
                                useCluster("integration")
                                namespaceMode.set(com.elastic.runner.gradle.NamespaceMode.SUITE)
                            }
                        }
                    }

                    subprojects {
                        apply plugin: 'java'

                        java {
                            toolchain {
                                languageVersion = JavaLanguageVersion.of(17)
                            }
                        }

                        testing {
                            suites {
                                register("integrationTest", JvmTestSuite) {
                                    useJUnitJupiter()
                                }
                            }
                        }

                        tasks.named("check") {
                            dependsOn(tasks.named("integrationTest"))
                        }
                    }
                    """.formatted(escapedZip);
            write(projectDir.resolve("build.gradle"), buildGradle);

            write(projectDir.resolve("app/src/integrationTest/java/example/SharedClusterSuiteTest.java"), testClassSource());
            write(projectDir.resolve("search/src/integrationTest/java/example/SharedClusterSuiteTest.java"), testClassSource());

            BuildResult result = GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withArguments(":app:integrationTest", ":search:integrationTest", "--parallel", "--stacktrace")
                    .withPluginClasspath()
                    .forwardOutput()
                    .build();

            assertEquals(SUCCESS, result.task(":app:integrationTest").getOutcome());
            assertEquals(SUCCESS, result.task(":search:integrationTest").getOutcome());

            List<String> appInfo = Files.readAllLines(projectDir.resolve("app/build/elastic-runner-info.txt"));
            List<String> searchInfo = Files.readAllLines(projectDir.resolve("search/build/elastic-runner-info.txt"));

            assertEquals(appInfo.get(0), searchInfo.get(0), "baseUri should be shared across projects");
            assertNotEquals(appInfo.get(1), searchInfo.get(1), "suite namespaces should differ across projects");
            assertTrue(appInfo.get(1).contains("app_integrationtest"));
            assertTrue(searchInfo.get(1).contains("search_integrationtest"));
        } finally {
            deleteTempDir(projectDir);
        }
    }

    private Path localDistroZip() throws IOException {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            Path distros = candidate.resolve("distros");
            if (!Files.isDirectory(distros)) {
                continue;
            }
            try (var stream = Files.list(distros)) {
                Path zip = stream
                        .filter(path -> path.getFileName().toString().endsWith(".zip"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .findFirst()
                        .orElse(null);
                if (zip != null) {
                    return zip;
                }
            }
        }
        return null;
    }

    private String testClassSource() {
        return """
                package example;

                import org.junit.jupiter.api.MethodOrderer;
                import org.junit.jupiter.api.Order;
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.TestMethodOrder;

                import java.net.URI;
                import java.net.http.HttpClient;
                import java.net.http.HttpRequest;
                import java.net.http.HttpResponse;
                import java.nio.charset.StandardCharsets;
                import java.nio.file.Files;
                import java.nio.file.Path;

                import static org.junit.jupiter.api.Assertions.assertEquals;
                import static org.junit.jupiter.api.Assertions.assertTrue;

                @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
                class SharedClusterSuiteTest {
                    private static final HttpClient CLIENT = HttpClient.newHttpClient();

                    @Test
                    @Order(1)
                    void createsNamespacedIndexAndDocument() throws Exception {
                        String baseUri = requiredUri("elastic.runner.baseUri");
                        String namespace = requiredProperty("elastic.runner.namespace");
                        String index = namespace + "-orders";

                        send("PUT", baseUri + index, "{\\"settings\\":{\\"index.number_of_replicas\\":0}}");
                        send("PUT", baseUri + index + "/_doc/1", "{\\"status\\":\\"new\\"}");
                        send("POST", baseUri + index + "/_refresh", null);

                        String count = send("GET", baseUri + index + "/_count", null);
                        assertTrue(count.contains("\\"count\\":1"));

                        Files.createDirectories(Path.of("build"));
                        Files.writeString(
                                Path.of("build/elastic-runner-info.txt"),
                                baseUri + System.lineSeparator() + namespace,
                                StandardCharsets.UTF_8
                        );
                    }

                    @Test
                    @Order(2)
                    void canReadSharedSuiteStateUsingSameNamespace() throws Exception {
                        String baseUri = requiredUri("elastic.runner.baseUri");
                        String namespace = requiredProperty("elastic.runner.namespace");
                        String index = namespace + "-orders";
                        String count = send("GET", baseUri + index + "/_count", null);
                        assertTrue(count.contains("\\"count\\":1"));
                    }

                    private static String requiredProperty(String key) {
                        String value = System.getProperty(key);
                        if (value == null || value.isBlank()) {
                            throw new IllegalStateException("Missing system property: " + key);
                        }
                        return value;
                    }

                    private static String requiredUri(String key) {
                        String value = requiredProperty(key);
                        return value.endsWith("/") ? value : value + "/";
                    }

                    private static String send(String method, String uri, String body) throws Exception {
                        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri));
                        if (body == null) {
                            builder.method(method, HttpRequest.BodyPublishers.noBody());
                        } else {
                            builder.header("Content-Type", "application/json");
                            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
                        }
                        HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
                                () -> method + " " + uri + " => " + response.statusCode() + " " + response.body());
                        return response.body();
                    }
                }
                """;
    }

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
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
                    public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.deleteIfExists(file);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    @Override
                    public java.nio.file.FileVisitResult postVisitDirectory(Path directory, IOException exc)
                            throws IOException {
                        Files.deleteIfExists(directory);
                        return java.nio.file.FileVisitResult.CONTINUE;
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
