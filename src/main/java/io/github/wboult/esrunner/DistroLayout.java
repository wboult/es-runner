package io.github.wboult.esrunner;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class DistroLayout {
    private DistroLayout() {
    }

    static Path prepare(Path zip, Path workDir) throws IOException {
        Path distroDir = workDir.resolve("distro");
        Files.createDirectories(distroDir);
        Optional<Path> existing = findHomeDir(distroDir);
        if (existing.isPresent()) {
            return existing.get();
        }
        ArchiveExtractor.extract(zip, distroDir);
        return findHomeDir(distroDir)
                .orElseThrow(() -> new IOException("Failed to locate Elasticsearch home after extraction."));
    }

    private static Optional<Path> findHomeDir(Path root) throws IOException {
        if (hasBin(root)) {
            return Optional.of(root);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (Files.isDirectory(child) && hasBin(child)) {
                    return Optional.of(child);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean hasBin(Path dir) {
        Path bin = dir.resolve("bin");
        if (!Files.isDirectory(bin)) {
            return false;
        }
        Path script = bin.resolve(Os.executableName("elasticsearch"));
        return Files.exists(script);
    }
}
