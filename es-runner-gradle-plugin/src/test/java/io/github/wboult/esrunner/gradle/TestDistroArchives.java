package io.github.wboult.esrunner.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class TestDistroArchives {
    private TestDistroArchives() {
    }

    static Path localElasticsearch9Archive(Path repoRoot) throws IOException {
        Path configured = configuredArchive("ES_DISTRO_ZIP", "elasticsearch-9.3.1");
        if (configured != null) {
            return configured;
        }

        List<Path> candidates = new ArrayList<>();
        for (Path candidate = repoRoot; candidate != null; candidate = candidate.getParent()) {
            candidates.addAll(findArchives(candidate.resolve("distros"), "elasticsearch-9.3.1"));
            candidates.addAll(findArchives(candidate.resolve(".claude").resolve("worktrees"), "elasticsearch-9.3.1"));
        }

        return candidates.stream()
                .distinct()
                .sorted(Comparator.comparing(Path::toString))
                .findFirst()
                .orElse(null);
    }

    private static Path configuredArchive(String envVar, String versionToken) {
        String raw = System.getenv(envVar);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Path path = Path.of(raw.trim());
        if (!Files.isRegularFile(path)) {
            return null;
        }
        String name = path.getFileName().toString();
        if ((name.endsWith(".zip") || name.endsWith(".tar.gz")) && name.contains(versionToken)) {
            return path;
        }
        return null;
    }

    private static List<Path> findArchives(Path root, String versionToken) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }

        if (Files.isDirectory(root) && "worktrees".equals(root.getFileName().toString())) {
            List<Path> archives = new ArrayList<>();
            try (var worktrees = Files.list(root)) {
                for (Path worktree : worktrees.toList()) {
                    archives.addAll(findArchives(worktree.resolve("distros"), versionToken));
                }
            }
            return archives;
        }

        if (!Files.isDirectory(root)) {
            return List.of();
        }

        List<Path> matches = new ArrayList<>();
        try (var stream = Files.walk(root, 3)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return (name.endsWith(".zip") || name.endsWith(".tar.gz"))
                                && name.contains(versionToken);
                    })
                    .forEach(matches::add);
        }
        return matches;
    }
}
