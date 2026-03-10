package io.github.wboult.esrunner.embedded;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Immutable configuration for one embedded Elasticsearch node.
 */
public record EmbeddedElasticServerConfig(
        Path esHome,
        Path workDir,
        String clusterName,
        String nodeName,
        int httpPort,
        int portRangeStart,
        int portRangeEnd,
        Duration startupTimeout,
        Duration shutdownTimeout,
        Map<String, String> settings,
        Set<String> includedModules,
        Set<String> classpathPlugins
) {
    /**
     * Validates and normalizes configuration.
     *
     * @param esHome extracted Elasticsearch home directory
     * @param workDir working directory for staged home, data, and logs
     * @param clusterName cluster name
     * @param nodeName node name
     * @param httpPort fixed HTTP port, or {@code 0} to use the configured range
     * @param portRangeStart start of the HTTP port range used when {@code httpPort} is zero
     * @param portRangeEnd end of the HTTP port range used when {@code httpPort} is zero
     * @param startupTimeout startup timeout for HTTP readiness
     * @param shutdownTimeout shutdown timeout for embedded node close
     * @param settings additional Elasticsearch settings
     * @param includedModules built-in modules copied into the staged embedded home
     * @param classpathPlugins plugin class names loaded directly from the caller's classpath
     */
    public EmbeddedElasticServerConfig {
        Objects.requireNonNull(esHome, "esHome");
        Objects.requireNonNull(workDir, "workDir");
        Objects.requireNonNull(clusterName, "clusterName");
        Objects.requireNonNull(nodeName, "nodeName");
        Objects.requireNonNull(startupTimeout, "startupTimeout");
        Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        includedModules = includedModules == null ? Set.of() : Set.copyOf(includedModules);
        classpathPlugins = classpathPlugins == null ? Set.of() : Set.copyOf(classpathPlugins);
        if (portRangeStart <= 0 || portRangeEnd < portRangeStart) {
            throw new IllegalArgumentException("Invalid port range: " + portRangeStart + "-" + portRangeEnd);
        }
    }

    /**
     * Returns the default embedded configuration used by examples and tests.
     *
     * @return default configuration
     */
    public static EmbeddedElasticServerConfig defaults() {
        return new EmbeddedElasticServerConfig(
                Paths.get("."),
                Paths.get(".es-embedded"),
                "local-es",
                "embedded-node",
                0,
                9200,
                9300,
                Duration.ofSeconds(60),
                Duration.ofSeconds(20),
                defaultSettings(),
                Set.of(),
                Set.of()
        );
    }

    /**
     * Returns a builder seeded from {@link #defaults()}.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder(defaults());
    }

    /**
     * Builds a configuration by applying a builder callback to the defaults.
     *
     * @param consumer builder callback
     * @return built configuration
     */
    public static EmbeddedElasticServerConfig from(Consumer<Builder> consumer) {
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
     * Returns the data directory under the configured work directory.
     *
     * @return data directory
     */
    public Path dataDir() {
        return workDir.resolve("data");
    }

    /**
     * Returns the logs directory under the configured work directory.
     *
     * @return logs directory
     */
    public Path logsDir() {
        return workDir.resolve("logs");
    }

    /**
     * Returns the staged embedded home directory under the configured work directory.
     *
     * @return staged home directory
     */
    public Path stagedHomeDir() {
        return workDir.resolve("home");
    }

    private static Map<String, String> defaultSettings() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("discovery.type", "single-node");
        defaults.put("xpack.security.enabled", "false");
        defaults.put("bootstrap.memory_lock", "false");
        defaults.put("ingest.geoip.downloader.enabled", "false");
        return defaults;
    }

    /**
     * Mutable builder for {@link EmbeddedElasticServerConfig}.
     */
    public static final class Builder {
        private Path esHome;
        private Path workDir;
        private String clusterName;
        private String nodeName;
        private int httpPort;
        private int portRangeStart;
        private int portRangeEnd;
        private Duration startupTimeout;
        private Duration shutdownTimeout;
        private final Map<String, String> settings;
        private final Set<String> includedModules;
        private final Set<String> classpathPlugins;

        private Builder(EmbeddedElasticServerConfig base) {
            this.esHome = base.esHome();
            this.workDir = base.workDir();
            this.clusterName = base.clusterName();
            this.nodeName = base.nodeName();
            this.httpPort = base.httpPort();
            this.portRangeStart = base.portRangeStart();
            this.portRangeEnd = base.portRangeEnd();
            this.startupTimeout = base.startupTimeout();
            this.shutdownTimeout = base.shutdownTimeout();
            this.settings = new LinkedHashMap<>(base.settings());
            this.includedModules = new LinkedHashSet<>(base.includedModules());
            this.classpathPlugins = new LinkedHashSet<>(base.classpathPlugins());
        }

        public Builder esHome(Path esHome) {
            this.esHome = esHome;
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

        public Builder nodeName(String nodeName) {
            this.nodeName = nodeName;
            return this;
        }

        public Builder httpPort(int httpPort) {
            this.httpPort = httpPort;
            return this;
        }

        public Builder portRangeStart(int portRangeStart) {
            this.portRangeStart = portRangeStart;
            return this;
        }

        public Builder portRangeEnd(int portRangeEnd) {
            this.portRangeEnd = portRangeEnd;
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

        public Builder setting(String key, String value) {
            this.settings.put(key, value);
            return this;
        }

        public Builder settings(Map<String, String> settings) {
            this.settings.clear();
            if (settings != null) {
                this.settings.putAll(settings);
            }
            return this;
        }

        public Builder includeModule(String moduleName) {
            this.includedModules.add(moduleName);
            return this;
        }

        public Builder includeModules(Collection<String> moduleNames) {
            if (moduleNames != null) {
                this.includedModules.addAll(moduleNames);
            }
            return this;
        }

        public Builder removeModule(String moduleName) {
            this.includedModules.remove(moduleName);
            return this;
        }

        public Builder removeModules(Collection<String> moduleNames) {
            if (moduleNames != null) {
                this.includedModules.removeAll(moduleNames);
            }
            return this;
        }

        public Builder clearIncludedModules() {
            this.includedModules.clear();
            return this;
        }

        public Builder includedModules(Set<String> includedModules) {
            this.includedModules.clear();
            if (includedModules != null) {
                this.includedModules.addAll(includedModules);
            }
            return this;
        }

        public Builder includeClasspathPlugin(String pluginClassName) {
            this.classpathPlugins.add(pluginClassName);
            return this;
        }

        public Builder includeClasspathPlugins(Collection<String> pluginClassNames) {
            if (pluginClassNames != null) {
                this.classpathPlugins.addAll(pluginClassNames);
            }
            return this;
        }

        public Builder removeClasspathPlugin(String pluginClassName) {
            this.classpathPlugins.remove(pluginClassName);
            return this;
        }

        public Builder removeClasspathPlugins(Collection<String> pluginClassNames) {
            if (pluginClassNames != null) {
                this.classpathPlugins.removeAll(pluginClassNames);
            }
            return this;
        }

        public Builder clearClasspathPlugins() {
            this.classpathPlugins.clear();
            return this;
        }

        public Builder classpathPlugins(Set<String> pluginClassNames) {
            this.classpathPlugins.clear();
            if (pluginClassNames != null) {
                this.classpathPlugins.addAll(pluginClassNames);
            }
            return this;
        }

        public Builder useBundledProfile(EmbeddedModuleProfile profile) {
            Objects.requireNonNull(profile, "profile");
            return includedModules(profile.includedModules());
        }

        public EmbeddedElasticServerConfig build() {
            return new EmbeddedElasticServerConfig(
                    esHome,
                    workDir,
                    clusterName,
                    nodeName,
                    httpPort,
                    portRangeStart,
                    portRangeEnd,
                    startupTimeout,
                    shutdownTimeout,
                    settings,
                    includedModules,
                    classpathPlugins
            );
        }
    }
}
