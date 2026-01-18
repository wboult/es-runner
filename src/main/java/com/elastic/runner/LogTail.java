package com.elastic.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class LogTail {
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
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - maxLines);
            return "Log tail:\n" + String.join(System.lineSeparator(), lines.subList(from, lines.size()));
        } catch (IOException e) {
            return "Failed to read log file.";
        }
    }
}
