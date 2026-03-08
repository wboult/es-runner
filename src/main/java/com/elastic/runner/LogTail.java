package com.elastic.runner;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class LogTail {
    /** Files smaller than this threshold are read entirely into memory. */
    private static final long SMALL_FILE_THRESHOLD = 1_048_576L; // 1 MiB

    /**
     * Bytes to read from the end of large files. Sized to comfortably fit
     * {@code maxLines} lines without loading the whole file.
     */
    private static final long LARGE_FILE_CHUNK = 262_144L; // 256 KiB

    private LogTail() {
    }

    static String read(Path logFile, int maxLines) {
        if (maxLines <= 0) {
            return "";
        }
        if (!Files.exists(logFile)) {
            return "Log file not found.";
        }
        try {
            List<String> lines = readLastLines(logFile, maxLines);
            return "Log tail:\n" + String.join(System.lineSeparator(), lines);
        } catch (IOException e) {
            return "Failed to read log file: " + e.getMessage();
        }
    }

    private static List<String> readLastLines(Path logFile, int maxLines) throws IOException {
        long fileSize = Files.size(logFile);

        if (fileSize <= SMALL_FILE_THRESHOLD) {
            // Small file: read entirely and slice the tail.
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - maxLines);
            return new ArrayList<>(lines.subList(from, lines.size()));
        }

        // Large file: read a chunk from the end to avoid loading the whole file.
        long chunkSize = Math.min(fileSize, LARGE_FILE_CHUNK);
        byte[] buffer = new byte[(int) chunkSize];
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            raf.seek(fileSize - chunkSize);
            raf.readFully(buffer);
        }

        String chunk = new String(buffer, StandardCharsets.UTF_8);

        // Discard the first (potentially partial) line caused by seeking mid-file.
        int firstNewline = chunk.indexOf('\n');
        if (firstNewline >= 0 && firstNewline + 1 < chunk.length()) {
            chunk = chunk.substring(firstNewline + 1);
        }

        String[] split = chunk.split("\n", -1);
        int count = split.length;
        // Trim trailing empty element when the file ends with a newline.
        if (count > 0 && split[count - 1].isEmpty()) {
            count--;
        }

        int from = Math.max(0, count - maxLines);
        List<String> result = new ArrayList<>(count - from);
        for (int i = from; i < count; i++) {
            // Normalise Windows line endings (\r\n → \n).
            String line = split[i];
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            result.add(line);
        }
        return result;
    }
}
