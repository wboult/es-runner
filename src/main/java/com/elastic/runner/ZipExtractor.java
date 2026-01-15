package com.elastic.runner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ZipExtractor {
    private ZipExtractor() {
    }

    static void extract(Path zipFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (InputStream inputStream = Files.newInputStream(zipFile);
             ZipInputStream zipStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                Path resolved = targetDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(targetDir)) {
                    throw new IOException("Zip entry is outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zipStream, resolved, StandardCopyOption.REPLACE_EXISTING);
                }
                zipStream.closeEntry();
            }
        }
    }
}
