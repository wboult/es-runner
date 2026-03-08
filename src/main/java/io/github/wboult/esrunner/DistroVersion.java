package io.github.wboult.esrunner;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DistroVersion {
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("elasticsearch-([0-9]+(?:\\.[0-9]+)*)", Pattern.CASE_INSENSITIVE);

    private DistroVersion() {
    }

    static String fromZip(Path zip) {
        String name = zip.getFileName().toString();
        Matcher matcher = VERSION_PATTERN.matcher(name);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "unknown";
    }
}
