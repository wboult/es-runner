package io.github.wboult.esrunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;

final class TarGzExtractor {
    private static final int RECORD_SIZE = 512;

    private TarGzExtractor() {
    }

    static void extract(Path archive, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (InputStream fileIn = Files.newInputStream(archive);
             InputStream gzipIn = new GZIPInputStream(fileIn)) {
            byte[] header = new byte[RECORD_SIZE];
            while (true) {
                int read = readFully(gzipIn, header);
                if (read == 0 || isAllZero(header)) {
                    break;
                }
                String name = parseName(header);
                if (name.isEmpty()) {
                    break;
                }
                long size = parseOctal(header, 124, 12);
                char type = (char) header[156];

                Path resolved = targetDir.resolve(name).normalize();
                if (!resolved.startsWith(targetDir)) {
                    throw new IOException("Tar entry is outside target dir: " + name);
                }

                if (type == '5' || name.endsWith("/")) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    try (InputStream entryStream = new LimitedInputStream(gzipIn, size)) {
                        Files.copy(entryStream, resolved, StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                long remaining = RECORD_SIZE - (size % RECORD_SIZE);
                if (remaining != RECORD_SIZE) {
                    skipFully(gzipIn, remaining);
                }
            }
        }
    }

    private static String parseName(byte[] header) {
        String name = parseString(header, 0, 100);
        String prefix = parseString(header, 345, 155);
        if (!prefix.isEmpty()) {
            return prefix + "/" + name;
        }
        return name;
    }

    private static String parseString(byte[] buffer, int offset, int length) {
        int end = offset;
        int max = offset + length;
        while (end < max && buffer[end] != 0) {
            end++;
        }
        return new String(buffer, offset, end - offset).trim();
    }

    private static long parseOctal(byte[] buffer, int offset, int length) {
        long value = 0;
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            byte b = buffer[i];
            if (b == 0 || b == ' ') {
                continue;
            }
            if (b < '0' || b > '7') {
                break;
            }
            value = (value << 3) + (b - '0');
        }
        return value;
    }

    private static boolean isAllZero(byte[] buffer) {
        for (byte b : buffer) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static int readFully(InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = in.read(buffer, total, buffer.length - total);
            if (read == -1) {
                break;
            }
            total += read;
        }
        return total;
    }

    private static void skipFully(InputStream in, long count) throws IOException {
        long skipped = 0;
        while (skipped < count) {
            long step = in.skip(count - skipped);
            if (step <= 0) {
                break;
            }
            skipped += step;
        }
    }

    private static final class LimitedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        private LimitedInputStream(InputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int read = delegate.read();
            if (read != -1) {
                remaining--;
            }
            return read;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int read = delegate.read(b, off, toRead);
            if (read != -1) {
                remaining -= read;
            }
            return read;
        }
    }
}
