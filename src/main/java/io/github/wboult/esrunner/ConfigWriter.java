package io.github.wboult.esrunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.StringJoiner;

final class ConfigWriter {
    private ConfigWriter() {
    }

    static void write(Path file, Map<String, String> settings) throws IOException {
        Files.createDirectories(file.getParent());
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            joiner.add(entry.getKey() + ": " + entry.getValue());
        }
        Files.writeString(file, joiner.toString(), StandardCharsets.UTF_8);
    }
}
