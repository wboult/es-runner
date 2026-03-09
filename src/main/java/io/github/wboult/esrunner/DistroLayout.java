package io.github.wboult.esrunner;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class DistroLayout {
    private DistroLayout() {
    }

    static Path prepare(Path zip, Path workDir, DistroFamily family) throws IOException {
        Path distroDir = workDir.resolve("distro");
        Files.createDirectories(distroDir);
        Optional<Path> existing = findHomeDir(distroDir, family);
        if (existing.isPresent()) {
            return existing.get();
        }
        ArchiveExtractor.extract(zip, distroDir);
        return findHomeDir(distroDir, family)
                .orElseThrow(() -> new IOException("Failed to locate " + family.displayName() + " home after extraction."));
    }

    private static Optional<Path> findHomeDir(Path root, DistroFamily family) throws IOException {
        if (hasBin(root, family)) {
            return Optional.of(root);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (Files.isDirectory(child) && hasBin(child, family)) {
                    return Optional.of(child);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean hasBin(Path dir, DistroFamily family) {
        Path bin = dir.resolve("bin");
        if (!Files.isDirectory(bin)) {
            return false;
        }
        Path script = bin.resolve(Os.executableName(family.launcherBaseName()));
        return Files.exists(script);
    }
}
