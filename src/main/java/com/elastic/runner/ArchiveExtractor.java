package com.elastic.runner;

import java.io.IOException;
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
            TarGzExtractor.extract(archive, targetDir);
            return;
        }
        throw new IOException("Unsupported archive type: " + name);
    }
}
