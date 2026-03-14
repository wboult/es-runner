package io.github.wboult.esrunner;

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

/**
 * Immutable configuration for resolving a distro archive and starting one
 * local search node.
 *
 * <p>{@link #defaults()} starts from these values for Elasticsearch:
 * family {@code ELASTICSEARCH}, no explicit ZIP or version, {@code distros/}
 * as the cache directory, {@code .es/} as the work directory, download
 * disabled, cluster name {@code local-es}, dynamic HTTP port selection from
 * {@code 9200-9300}, heap {@code 256m}, startup timeout {@code 60s}, shutdown
 * timeout {@code 20s}, request timeout {@code 30s}, bulk timeout {@code 5m},
 * download timeout {@code 5m}, quiet logging disabled, and the family's
 * default settings map.</p>
 *
 * <p>{@link #defaults(DistroFamily)} keeps those operational defaults but swaps
 * the family-specific download base URL and default settings for the requested
 * family.</p>
 *
 * @param family distro family to resolve and launch
 * @param distroZip local ZIP archive to use instead of version-based lookup
 * @param version distro version used for download or cache resolution
 * @param distrosDir local distro cache directory
 * @param download whether the distro should be downloaded even if cached
 * @param downloadBaseUrl base URL or mirror prefix used for downloads
 * @param workDir working directory used for extracted files, logs, and state
 * @param clusterName cluster name written into the family config file
 * @param httpPort fixed HTTP port, or {@code 0} to use the configured range
 * @param portRangeStart start of the HTTP port range used when {@code httpPort}
 *                       is zero
 * @param portRangeEnd end of the HTTP port range used when {@code httpPort} is
 *                     zero
 * @param heap heap size passed to the launched process for both Xms and Xmx
 * @param startupTimeout startup timeout for process readiness
 * @param shutdownTimeout shutdown timeout before forced termination
 * @param requestTimeout timeout for standard HTTP requests made through the
 *                       attached client
 * @param bulkTimeout timeout for bulk NDJSON requests made through the
 *                    attached client
 * @param downloadTimeout timeout for HTTP distro downloads
 * @param settings additional server settings
 * @param plugins plugins to install before startup
 * @param quiet whether process output should stay quiet on stdout/stderr
 */
public record ElasticRunnerConfig(
        DistroFamily family,
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
        Duration requestTimeout,
        Duration bulkTimeout,
        Duration downloadTimeout,
        Map<String, String> settings,
        List<String> plugins,
        boolean quiet
) {
    /**
     * Validates and normalizes configuration values.
     *
     * @param family distro family
     * @param distroZip local ZIP archive
     * @param version distro version
     * @param distrosDir distro cache directory
     * @param download download flag
     * @param downloadBaseUrl download base URL or mirror prefix
     * @param workDir working directory
     * @param clusterName cluster name
     * @param httpPort fixed HTTP port
     * @param portRangeStart port range start
     * @param portRangeEnd port range end
     * @param heap heap size
     * @param startupTimeout startup timeout
     * @param shutdownTimeout shutdown timeout
     * @param requestTimeout request timeout
     * @param bulkTimeout bulk timeout
     * @param downloadTimeout download timeout
     * @param settings server settings
     * @param plugins plugins to install
     * @param quiet quiet flag
     */
    public ElasticRunnerConfig {
        family = family == null ? DistroFamily.ELASTICSEARCH : family;
        Objects.requireNonNull(workDir, "workDir");
        Objects.requireNonNull(clusterName, "clusterName");
        Objects.requireNonNull(heap, "heap");
        Objects.requireNonNull(startupTimeout, "startupTimeout");
        Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        Objects.requireNonNull(bulkTimeout, "bulkTimeout");
        Objects.requireNonNull(downloadTimeout, "downloadTimeout");
        Objects.requireNonNull(distrosDir, "distrosDir");
        Objects.requireNonNull(downloadBaseUrl, "downloadBaseUrl");
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
        if (portRangeStart <= 0 || portRangeEnd < portRangeStart) {
            throw new IllegalArgumentException("Invalid port range: " + portRangeStart + "-" + portRangeEnd);
        }
    }

    /**
     * Returns the default single-node configuration used as the starting point
     * for most examples.
     *
     * @return default configuration
     */
    public static ElasticRunnerConfig defaults() {
        return defaults(DistroFamily.ELASTICSEARCH);
    }

    /**
     * Returns the default single-node configuration used as the starting point
     * for one distro family.
     *
     * @param family distro family
     * @return default configuration
     */
    public static ElasticRunnerConfig defaults(DistroFamily family) {
        return new ElasticRunnerConfig(
                family,
                null,
                null,
                Paths.get("distros"),
                false,
                family.defaultDownloadBaseUrl(),
                Paths.get(".es"),
                "local-es",
                0,
                9200,
                9300,
                "256m",
                Duration.ofSeconds(60),
                Duration.ofSeconds(20),
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5),
                family.defaultSettings(),
                List.of(),
                false
        );
    }

    /**
     * Returns a mutable builder seeded from {@link #defaults()}.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder(defaults());
    }

    /**
     * Returns a mutable builder seeded from the defaults for one distro family.
     *
     * @param family distro family
     * @return new builder
     */
    public static Builder builder(DistroFamily family) {
        return new Builder(defaults(family));
    }

    /**
     * Builds a configuration by applying a builder callback to the defaults.
     *
     * @param consumer builder callback
     * @return built configuration
     */
    public static ElasticRunnerConfig from(Consumer<Builder> consumer) {
        Builder builder = builder();
        consumer.accept(builder);
        return builder.build();
    }

    /**
     * Returns a builder seeded from this configuration.
     *
     * @return builder copy
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Mutable builder for {@link ElasticRunnerConfig}.
     */
    public static final class Builder {
        private DistroFamily family;
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
        private Duration requestTimeout;
        private Duration bulkTimeout;
        private Duration downloadTimeout;
        private final Map<String, String> settings;
        private final List<String> plugins;
        private boolean quiet;

        private Builder(ElasticRunnerConfig base) {
            this.family = base.family();
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
            this.requestTimeout = base.requestTimeout();
            this.bulkTimeout = base.bulkTimeout();
            this.downloadTimeout = base.downloadTimeout();
            this.settings = new LinkedHashMap<>(base.settings());
            this.plugins = new ArrayList<>(base.plugins());
            this.quiet = base.quiet();
        }

        /**
         * Sets the distro family. If the download URL or settings still match
         * the previous defaults, they are swapped to the new family's defaults
         * too so a simple family change stays ergonomic.
         *
         * @param family distro family
         * @return this builder
         */
        public Builder family(DistroFamily family) {
            Objects.requireNonNull(family, "family");
            DistroFamily previous = this.family;
            if (previous == family) {
                return this;
            }
            if (previous != null && Objects.equals(downloadBaseUrl, previous.defaultDownloadBaseUrl())) {
                this.downloadBaseUrl = family.defaultDownloadBaseUrl();
            }
            if (previous != null && settings.equals(previous.defaultSettings())) {
                this.settings.clear();
                this.settings.putAll(family.defaultSettings());
            }
            this.family = family;
            return this;
        }

        /**
         * Sets the local ZIP archive to use.
         *
         * @param distroZip distro ZIP archive
         * @return this builder
         */
        public Builder distroZip(Path distroZip) {
            this.distroZip = distroZip;
            return this;
        }

        /**
         * Sets the distro version used for download or cache lookup.
         *
         * @param version Elasticsearch version
         * @return this builder
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the local distro cache directory.
         *
         * @param distrosDir distro cache directory
         * @return this builder
         */
        public Builder distrosDir(Path distrosDir) {
            this.distrosDir = distrosDir;
            return this;
        }

        /**
         * Controls whether the distro should be downloaded even when already
         * present in the cache directory.
         *
         * @param download download flag
         * @return this builder
         */
        public Builder download(boolean download) {
            this.download = download;
            return this;
        }

        /**
         * Sets the base URL or mirror prefix used for distro downloads.
         *
         * @param downloadBaseUrl download base URL
         * @return this builder
         */
        public Builder downloadBaseUrl(String downloadBaseUrl) {
            this.downloadBaseUrl = downloadBaseUrl;
            return this;
        }

        /**
         * Sets the working directory used for extracted files, logs, and state.
         *
         * @param workDir working directory
         * @return this builder
         */
        public Builder workDir(Path workDir) {
            this.workDir = workDir;
            return this;
        }

        /**
         * Sets the local cluster name.
         *
         * @param clusterName cluster name
         * @return this builder
         */
        public Builder clusterName(String clusterName) {
            this.clusterName = clusterName;
            return this;
        }

        /**
         * Sets a fixed HTTP port. Use {@code 0} to let the configured port range
         * decide.
         *
         * @param httpPort HTTP port
         * @return this builder
         */
        public Builder httpPort(int httpPort) {
            this.httpPort = httpPort;
            return this;
        }

        /**
         * Sets the HTTP port range used when {@link #httpPort(int)} is zero.
         *
         * @param start inclusive range start
         * @param end inclusive range end
         * @return this builder
         */
        public Builder portRange(int start, int end) {
            this.portRangeStart = start;
            this.portRangeEnd = end;
            return this;
        }

        /**
         * Sets the server heap size for both Xms and Xmx.
         *
         * @param heap heap size such as {@code 512m}
         * @return this builder
         */
        public Builder heap(String heap) {
            this.heap = heap;
            return this;
        }

        /**
         * Sets the startup timeout.
         *
         * @param startupTimeout startup timeout
         * @return this builder
         */
        public Builder startupTimeout(Duration startupTimeout) {
            this.startupTimeout = startupTimeout;
            return this;
        }

        /**
         * Sets the shutdown timeout used before forced termination.
         *
         * @param shutdownTimeout shutdown timeout
         * @return this builder
         */
        public Builder shutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
            return this;
        }

        /**
         * Sets the timeout for standard HTTP requests made through the attached
         * {@link ElasticClient}.
         *
         * @param requestTimeout standard request timeout
         * @return this builder
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * Sets the timeout for bulk NDJSON requests made through the attached
         * {@link ElasticClient}.
         *
         * @param bulkTimeout bulk request timeout
         * @return this builder
         */
        public Builder bulkTimeout(Duration bulkTimeout) {
            this.bulkTimeout = bulkTimeout;
            return this;
        }

        /**
         * Sets the timeout for HTTP distro downloads.
         *
         * @param downloadTimeout distro download timeout
         * @return this builder
         */
        public Builder downloadTimeout(Duration downloadTimeout) {
            this.downloadTimeout = downloadTimeout;
            return this;
        }

        /**
         * Replaces the entire server settings map, including all family
         * defaults.
         *
         * <p>This is a full-replacement operation. Defaults like
         * {@code discovery.type=single-node} are removed unless they are
         * present in the supplied map. Prefer {@link #setting(String, String)}
         * for additive changes.</p>
         *
         * <p>Prefer {@link #replaceSettings(Map)} in new code. This method is
         * kept as a legacy alias.</p>
         *
         * @param settings settings map
         * @return this builder
         */
        @Deprecated(forRemoval = false)
        public Builder settings(Map<String, String> settings) {
            return replaceSettings(settings);
        }

        /**
         * Replaces the entire server settings map, including all family
         * defaults.
         *
         * <p>Use this when you want complete control over the written config
         * file. Prefer {@link #setting(String, String)} when you only need to
         * add or override a few settings while keeping the defaults.</p>
         *
         * @param settings settings map
         * @return this builder
         */
        public Builder replaceSettings(Map<String, String> settings) {
            this.settings.clear();
            this.settings.putAll(settings);
            return this;
        }

        /**
         * Adds or overrides one server setting.
         *
         * @param key setting key
         * @param value setting value
         * @return this builder
         */
        public Builder setting(String key, String value) {
            this.settings.put(key, value);
            return this;
        }

        /**
         * Replaces the plugin install list.
         *
         * @param plugins plugin list
         * @return this builder
         */
        public Builder plugins(List<String> plugins) {
            this.plugins.clear();
            this.plugins.addAll(plugins);
            return this;
        }

        /**
         * Adds one plugin to install before startup.
         *
         * @param plugin plugin id or install source
         * @return this builder
         */
        public Builder plugin(String plugin) {
            this.plugins.add(plugin);
            return this;
        }

        /**
         * Controls whether process output should stay quiet on stdout/stderr.
         *
         * @param quiet quiet flag
         * @return this builder
         */
        public Builder quiet(boolean quiet) {
            this.quiet = quiet;
            return this;
        }

        /**
         * Builds the immutable configuration.
         *
         * @return built configuration
         */
        public ElasticRunnerConfig build() {
            return new ElasticRunnerConfig(
                    family,
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
                    requestTimeout,
                    bulkTimeout,
                    downloadTimeout,
                    settings,
                    plugins,
                    quiet
            );
        }
    }
}
