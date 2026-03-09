package io.github.wboult.esrunner;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Search server distro family supported by the process-backed runner.
 */
public enum DistroFamily {
    ELASTICSEARCH(
            "Elasticsearch",
            "elasticsearch",
            "https://artifacts.elastic.co/downloads/elasticsearch/",
            "elasticsearch.yml",
            "elasticsearch",
            "elasticsearch-plugin",
            "ES_PATH_CONF",
            "ES_JAVA_OPTS"
    ),
    OPENSEARCH(
            "OpenSearch",
            "opensearch",
            "https://artifacts.opensearch.org/releases/bundle/opensearch/",
            "opensearch.yml",
            "opensearch",
            "opensearch-plugin",
            "OPENSEARCH_PATH_CONF",
            "OPENSEARCH_JAVA_OPTS"
    );

    private final String displayName;
    private final String archivePrefix;
    private final String defaultDownloadBaseUrl;
    private final String configFileName;
    private final String launcherBaseName;
    private final String pluginScriptBaseName;
    private final String pathConfEnvVar;
    private final String javaOptsEnvVar;

    DistroFamily(String displayName,
                 String archivePrefix,
                 String defaultDownloadBaseUrl,
                 String configFileName,
                 String launcherBaseName,
                 String pluginScriptBaseName,
                 String pathConfEnvVar,
                 String javaOptsEnvVar) {
        this.displayName = displayName;
        this.archivePrefix = archivePrefix;
        this.defaultDownloadBaseUrl = defaultDownloadBaseUrl;
        this.configFileName = configFileName;
        this.launcherBaseName = launcherBaseName;
        this.pluginScriptBaseName = pluginScriptBaseName;
        this.pathConfEnvVar = pathConfEnvVar;
        this.javaOptsEnvVar = javaOptsEnvVar;
    }

    String displayName() {
        return displayName;
    }

    String archivePrefix() {
        return archivePrefix;
    }

    String defaultDownloadBaseUrl() {
        return defaultDownloadBaseUrl;
    }

    String configFileName() {
        return configFileName;
    }

    String launcherBaseName() {
        return launcherBaseName;
    }

    String pluginScriptBaseName() {
        return pluginScriptBaseName;
    }

    String pathConfEnvVar() {
        return pathConfEnvVar;
    }

    String javaOptsEnvVar() {
        return javaOptsEnvVar;
    }

    String classifier() {
        if (this == ELASTICSEARCH) {
            return Os.classifier();
        }
        String osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ROOT);
        String platform;
        if (osName.contains("win")) {
            platform = "windows";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            platform = "macos";
        } else if (osName.contains("nux") || osName.contains("linux")) {
            platform = "linux";
        } else {
            platform = "unknown";
        }

        String osArch = System.getProperty("os.arch", "generic").toLowerCase(Locale.ROOT);
        String arch;
        if ("amd64".equals(osArch) || "x86_64".equals(osArch)) {
            arch = "x64";
        } else if ("aarch64".equals(osArch) || "arm64".equals(osArch)) {
            arch = "arm64";
        } else {
            arch = osArch;
        }
        return platform + "-" + arch;
    }

    Map<String, String> defaultSettings() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("discovery.type", "single-node");
        if (this == ELASTICSEARCH) {
            defaults.put("xpack.security.enabled", "false");
        } else {
            defaults.put("plugins.security.disabled", "true");
        }
        return defaults;
    }

    static Optional<DistroFamily> infer(Path archive) {
        return infer(archive.getFileName().toString());
    }

    static Optional<DistroFamily> infer(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("elasticsearch-")) {
            return Optional.of(ELASTICSEARCH);
        }
        if (lower.startsWith("opensearch-")) {
            return Optional.of(OPENSEARCH);
        }
        return Optional.empty();
    }
}
