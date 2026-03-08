package io.github.wboult.esrunner;

import java.nio.file.Path;
import java.util.List;

final class Os {
    private static final String OS_NAME = System.getProperty("os.name", "generic").toLowerCase();
    private static final String OS_ARCH = System.getProperty("os.arch", "generic").toLowerCase();

    private Os() {
    }

    static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    static String classifier() {
        String arch = switch (OS_ARCH) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> OS_ARCH;
        };
        if (OS_NAME.contains("win")) {
            return "windows-" + arch;
        }
        if (OS_NAME.contains("mac") || OS_NAME.contains("darwin")) {
            return "darwin-" + arch;
        }
        if (OS_NAME.contains("nux") || OS_NAME.contains("linux")) {
            return "linux-" + arch;
        }
        return "unknown-" + arch;
    }

    static String archiveExtension() {
        return isWindows() ? "zip" : "tar.gz";
    }

    static String executableName(String base) {
        return isWindows() ? base + ".bat" : base;
    }

    static List<String> commandFor(Path script) {
        if (isWindows()) {
            return List.of("cmd.exe", "/c", script.toString());
        }
        return List.of(script.toString());
    }
}
