package io.github.wboult.esrunner.embedded;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Prepares a filtered Elasticsearch home for embedded use.
 *
 * <p>The raw extracted distribution contains many built-in modules. For embedded
 * mode we stage a smaller home using the bundled module profile shipped with the
 * selected embedded runner.
 */
public final class EmbeddedHome {

    private EmbeddedHome() {
    }

    /**
     * Builds a staged embedded home using the supplied bundled module profile.
     *
     * @param extractedHome extracted Elasticsearch distribution root
     * @param targetHome target staged home directory
     * @param includedModules exact set of built-in modules to copy
     * @return staged embedded home directory
     * @throws IOException if staging fails
     */
    public static Path prepareBundledHome(Path extractedHome, Path targetHome, Set<String> includedModules) throws IOException {
        requireDirectory(extractedHome, "extracted Elasticsearch home");
        requireDirectory(extractedHome.resolve("config"), "config directory");
        requireDirectory(extractedHome.resolve("lib"), "lib directory");
        requireDirectory(extractedHome.resolve("modules"), "modules directory");

        Files.createDirectories(targetHome);
        copyTree(extractedHome.resolve("config"), targetHome.resolve("config"));
        rewriteEmbeddedLoggingConfig(targetHome.resolve("config").resolve("log4j2.properties"));
        copyTree(extractedHome.resolve("lib"), targetHome.resolve("lib"));
        copyModules(extractedHome.resolve("modules"), targetHome.resolve("modules"), includedModules);
        Files.createDirectories(targetHome.resolve("plugins"));
        return targetHome;
    }

    /**
     * Builds a staged embedded home for runtimes that load optional plugins directly from
     * the caller classpath instead of from the extracted distribution filesystem.
     *
     * @param extractedHome extracted distribution root
     * @param targetHome target staged home directory
     * @return staged embedded home directory
     * @throws IOException if staging fails
     */
    public static Path prepareClasspathPluginHome(Path extractedHome, Path targetHome) throws IOException {
        requireDirectory(extractedHome, "extracted distribution home");
        requireDirectory(extractedHome.resolve("config"), "config directory");
        requireDirectory(extractedHome.resolve("lib"), "lib directory");

        Files.createDirectories(targetHome);
        copyTree(extractedHome.resolve("config"), targetHome.resolve("config"));
        rewriteEmbeddedLoggingConfig(targetHome.resolve("config").resolve("log4j2.properties"));
        copyTree(extractedHome.resolve("lib"), targetHome.resolve("lib"));
        Files.createDirectories(targetHome.resolve("modules"));
        Files.createDirectories(targetHome.resolve("plugins"));
        return targetHome;
    }

    /**
     * Best-effort recursive delete with small retries for Windows file locks.
     *
     * @param root directory to delete
     */
    public static void deleteTreeQuietly(Path root) {
        if (root == null || Files.exists(root) == false) {
            return;
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.deleteIfExists(file);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    @Override
                    public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.deleteIfExists(dir);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
                return;
            } catch (IOException ignored) {
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void copyModules(Path sourceModules, Path targetModules, Set<String> includedModules) throws IOException {
        Files.createDirectories(targetModules);
        try (Stream<Path> modules = Files.list(sourceModules)) {
            for (Path moduleDir : modules.filter(Files::isDirectory).toList()) {
                if (includedModules.isEmpty() == false
                        && includedModules.contains(moduleDir.getFileName().toString()) == false) {
                    continue;
                }
                Path targetModule = targetModules.resolve(moduleDir.getFileName());
                copyTree(moduleDir, targetModule);
                rewritePluginDescriptor(targetModule.resolve("plugin-descriptor.properties"));
            }
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                Path destination = relative.toString().isEmpty() ? target : target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void rewriteEmbeddedLoggingConfig(Path log4jConfig) throws IOException {
        if (Files.exists(log4jConfig) == false) {
            return;
        }
        String content = Files.readString(log4jConfig);
        String rewritten = content.replace("%node_name", "${sys:es.logs.node_name:-embedded-node}");
        Files.writeString(log4jConfig, rewritten);
    }

    private static void rewritePluginDescriptor(Path descriptor) throws IOException {
        if (Files.exists(descriptor) == false) {
            return;
        }
        List<String> rewritten = Files.readAllLines(descriptor).stream()
                .filter(line -> line.startsWith("modulename=") == false)
                .toList();
        Files.write(descriptor, rewritten);
    }

    private static void requireDirectory(Path path, String description) {
        if (Files.isDirectory(path) == false) {
            throw new IllegalArgumentException(description + " not found: " + path.toAbsolutePath());
        }
    }
}
