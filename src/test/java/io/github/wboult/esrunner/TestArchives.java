package io.github.wboult.esrunner;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class TestArchives {
    private static final int TAR_RECORD_SIZE = 512;

    private TestArchives() {
    }

    static Path zipWithEntries(Path archive, Map<String, String> entries) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return archive;
    }

    static Path tarGzWithEntries(Path archive, Map<String, String> entries) throws IOException {
        try (OutputStream fileOut = Files.newOutputStream(archive);
             GZIPOutputStream gzipOut = new GZIPOutputStream(fileOut)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                byte[] content = entry.getValue().getBytes(StandardCharsets.UTF_8);
                byte[] header = new byte[TAR_RECORD_SIZE];
                writeAscii(header, 0, 100, entry.getKey());
                writeOctal(header, 100, 8, 0644);
                writeOctal(header, 108, 8, 0);
                writeOctal(header, 116, 8, 0);
                writeOctal(header, 124, 12, content.length);
                writeOctal(header, 136, 12, System.currentTimeMillis() / 1000);
                fill(header, 148, 8, (byte) ' ');
                header[156] = '0';
                writeAscii(header, 257, 6, "ustar");
                writeAscii(header, 263, 2, "00");
                writeChecksum(header);
                gzipOut.write(header);
                gzipOut.write(content);
                int padding = (int) ((TAR_RECORD_SIZE - (content.length % TAR_RECORD_SIZE)) % TAR_RECORD_SIZE);
                if (padding > 0) {
                    gzipOut.write(new byte[padding]);
                }
            }
            gzipOut.write(new byte[TAR_RECORD_SIZE * 2]);
        }
        return archive;
    }

    private static void writeChecksum(byte[] header) {
        long sum = 0;
        for (byte value : header) {
            sum += Byte.toUnsignedInt(value);
        }
        writeOctal(header, 148, 8, sum);
    }

    private static void writeAscii(byte[] header, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, offset, Math.min(bytes.length, length));
    }

    private static void writeOctal(byte[] header, int offset, int length, long value) {
        String encoded = Long.toOctalString(value);
        int start = Math.max(0, length - encoded.length() - 1);
        for (int i = 0; i < encoded.length() && start + i < length - 1; i++) {
            header[offset + start + i] = (byte) encoded.charAt(i);
        }
        header[offset + length - 1] = 0;
    }

    private static void fill(byte[] header, int offset, int length, byte value) {
        for (int i = 0; i < length; i++) {
            header[offset + i] = value;
        }
    }
}
