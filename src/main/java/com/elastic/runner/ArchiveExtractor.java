package com.elastic.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class ArchiveExtractor {
    private ArchiveExtractor() {
    }

    static void extract(Path archive, Path targetDir) throws IOException {
        String name = archive.getFileName().toString().toLowerCase();
        if (name.endsWith(".zip")) {
            ZipExtractor.extract(archive, targetDir);
            return;
        }
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            if (!Os.isWindows()) {
                try {
                    Files.createDirectories(targetDir);
                    ProcessBuilder builder = new ProcessBuilder(
                            "tar", "-xzf", archive.toString(), "-C", targetDir.toString());
                    Process process = builder.start();
                    if (process.waitFor() == 0) {
                        return;
                    }
                } catch (IOException | InterruptedException ignored) {
                    if (ignored instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            TarGzExtractor.extract(archive, targetDir);
            return;
        }
        throw new IOException("Unsupported archive type: " + name);
    }
}
