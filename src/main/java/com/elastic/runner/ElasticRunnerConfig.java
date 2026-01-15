package com.elastic.runner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public record ElasticRunnerConfig(
        Path distroZip,
        String version,
        Path distrosDir,
        boolean download,
        String downloadBaseUrl,
        Path workDir,
        String clusterName,
        int httpPort,
        int portRangeStart,
        int portRangeEnd,
        String heap,
        Duration startupTimeout,
        Duration shutdownTimeout,
        Map<String, String> settings,
        List<String> plugins,
        boolean quiet
) {
    public ElasticRunnerConfig {
        Objects.requireNonNull(workDir, "workDir");
        Objects.requireNonNull(clusterName, "clusterName");
        Objects.requireNonNull(heap, "heap");
        Objects.requireNonNull(startupTimeout, "startupTimeout");
        Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        Objects.requireNonNull(distrosDir, "distrosDir");
        Objects.requireNonNull(downloadBaseUrl, "downloadBaseUrl");
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
        if (portRangeStart <= 0 || portRangeEnd < portRangeStart) {
            throw new IllegalArgumentException("Invalid port range: " + portRangeStart + "-" + portRangeEnd);
        }
    }

    public static ElasticRunnerConfig defaults() {
        return new ElasticRunnerConfig(
                null,
                null,
                Paths.get("distros"),
                false,
                "https://artifacts.elastic.co/downloads/elasticsearch/",
                Paths.get(".es"),
                "local-es",
                0,
                9200,
                9300,
                "256m",
                Duration.ofSeconds(60),
                Duration.ofSeconds(20),
                defaultSettings(),
                List.of(),
                false
        );
    }

    public static Builder builder() {
        return new Builder(defaults());
    }

    public static ElasticRunnerConfig from(Consumer<Builder> consumer) {
        Builder builder = builder();
        consumer.accept(builder);
        return builder.build();
    }

    public ElasticRunnerConfig configure(UnaryOperator<ElasticRunnerConfig> fn) {
        return fn.apply(this);
    }

    public ElasticRunnerConfig withDistroZip(Path distroZip) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withWorkDir(Path workDir) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withClusterName(String clusterName) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withHttpPort(int httpPort) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withPortRange(int start, int end) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                start,
                end,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withHeap(String heap) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withStartupTimeout(Duration startupTimeout) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withShutdownTimeout(Duration shutdownTimeout) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withSettings(Map<String, String> settings) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withSetting(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Map<String, String> updated = new LinkedHashMap<>(settings);
        updated.put(key, value);
        return withSettings(updated);
    }

    public ElasticRunnerConfig withPlugins(List<String> plugins) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withPlugin(String plugin) {
        Objects.requireNonNull(plugin, "plugin");
        List<String> updated = new java.util.ArrayList<>(plugins);
        updated.add(plugin);
        return withPlugins(updated);
    }

    public ElasticRunnerConfig withQuiet(boolean quiet) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withVersion(String version) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withDistrosDir(Path distrosDir) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withDownload(boolean download) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    public ElasticRunnerConfig withDownloadBaseUrl(String downloadBaseUrl) {
        return new ElasticRunnerConfig(
                distroZip,
                version,
                distrosDir,
                download,
                downloadBaseUrl,
                workDir,
                clusterName,
                httpPort,
                portRangeStart,
                portRangeEnd,
                heap,
                startupTimeout,
                shutdownTimeout,
                settings,
                plugins,
                quiet
        );
    }

    private static Map<String, String> defaultSettings() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("discovery.type", "single-node");
        defaults.put("xpack.security.enabled", "false");
        return defaults;
    }

    public static final class Builder {
        private Path distroZip;
        private String version;
        private Path distrosDir;
        private boolean download;
        private String downloadBaseUrl;
        private Path workDir;
        private String clusterName;
        private int httpPort;
        private int portRangeStart;
        private int portRangeEnd;
        private String heap;
        private Duration startupTimeout;
        private Duration shutdownTimeout;
        private final Map<String, String> settings;
        private final List<String> plugins;
        private boolean quiet;

        private Builder(ElasticRunnerConfig base) {
            this.distroZip = base.distroZip();
            this.version = base.version();
            this.distrosDir = base.distrosDir();
            this.download = base.download();
            this.downloadBaseUrl = base.downloadBaseUrl();
            this.workDir = base.workDir();
            this.clusterName = base.clusterName();
            this.httpPort = base.httpPort();
            this.portRangeStart = base.portRangeStart();
            this.portRangeEnd = base.portRangeEnd();
            this.heap = base.heap();
            this.startupTimeout = base.startupTimeout();
            this.shutdownTimeout = base.shutdownTimeout();
            this.settings = new LinkedHashMap<>(base.settings());
            this.plugins = new ArrayList<>(base.plugins());
            this.quiet = base.quiet();
        }

        public Builder distroZip(Path distroZip) {
            this.distroZip = distroZip;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder distrosDir(Path distrosDir) {
            this.distrosDir = distrosDir;
            return this;
        }

        public Builder download(boolean download) {
            this.download = download;
            return this;
        }

        public Builder downloadBaseUrl(String downloadBaseUrl) {
            this.downloadBaseUrl = downloadBaseUrl;
            return this;
        }

        public Builder workDir(Path workDir) {
            this.workDir = workDir;
            return this;
        }

        public Builder clusterName(String clusterName) {
            this.clusterName = clusterName;
            return this;
        }

        public Builder httpPort(int httpPort) {
            this.httpPort = httpPort;
            return this;
        }

        public Builder portRange(int start, int end) {
            this.portRangeStart = start;
            this.portRangeEnd = end;
            return this;
        }

        public Builder heap(String heap) {
            this.heap = heap;
            return this;
        }

        public Builder startupTimeout(Duration startupTimeout) {
            this.startupTimeout = startupTimeout;
            return this;
        }

        public Builder shutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
            return this;
        }

        public Builder settings(Map<String, String> settings) {
            this.settings.clear();
            this.settings.putAll(settings);
            return this;
        }

        public Builder setting(String key, String value) {
            this.settings.put(key, value);
            return this;
        }

        public Builder plugins(List<String> plugins) {
            this.plugins.clear();
            this.plugins.addAll(plugins);
            return this;
        }

        public Builder plugin(String plugin) {
            this.plugins.add(plugin);
            return this;
        }

        public Builder quiet(boolean quiet) {
            this.quiet = quiet;
            return this;
        }

        public ElasticRunnerConfig build() {
            return new ElasticRunnerConfig(
                    distroZip,
                    version,
                    distrosDir,
                    download,
                    downloadBaseUrl,
                    workDir,
                    clusterName,
                    httpPort,
                    portRangeStart,
                    portRangeEnd,
                    heap,
                    startupTimeout,
                    shutdownTimeout,
                    settings,
                    plugins,
                    quiet
            );
        }
    }
}
