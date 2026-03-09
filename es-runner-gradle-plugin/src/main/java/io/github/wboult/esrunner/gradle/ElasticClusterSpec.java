package io.github.wboult.esrunner.gradle;

import io.github.wboult.esrunner.ElasticRunnerConfig;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DSL model for one shared Elasticsearch cluster definition.
 */
public abstract class ElasticClusterSpec {
    private final String name;
    private final Property<String> distroZip;
    private final Property<String> version;
    private final Property<String> distrosDir;
    private final Property<Boolean> download;
    private final Property<String> downloadBaseUrl;
    private final Property<String> workDir;
    private final Property<String> clusterName;
    private final Property<Integer> httpPort;
    private final Property<Integer> portRangeStart;
    private final Property<Integer> portRangeEnd;
    private final Property<String> heap;
    private final Property<Long> startupTimeoutMillis;
    private final Property<Long> shutdownTimeoutMillis;
    private final MapProperty<String, String> settings;
    private final ListProperty<String> plugins;
    private final Property<Boolean> quiet;

    /**
     * Creates a cluster specification with defaults derived from
     * {@link ElasticRunnerConfig#defaults()}.
     *
     * @param name cluster name in the Gradle container
     * @param objects Gradle object factory
     * @param layout project layout used for default directories
     */
    @Inject
    public ElasticClusterSpec(String name, ObjectFactory objects, ProjectLayout layout) {
        this.name = name;
        this.distroZip = objects.property(String.class);
        this.version = objects.property(String.class);
        this.distrosDir = objects.property(String.class);
        this.download = objects.property(Boolean.class);
        this.downloadBaseUrl = objects.property(String.class);
        this.workDir = objects.property(String.class);
        this.clusterName = objects.property(String.class);
        this.httpPort = objects.property(Integer.class);
        this.portRangeStart = objects.property(Integer.class);
        this.portRangeEnd = objects.property(Integer.class);
        this.heap = objects.property(String.class);
        this.startupTimeoutMillis = objects.property(Long.class);
        this.shutdownTimeoutMillis = objects.property(Long.class);
        this.settings = objects.mapProperty(String.class, String.class);
        this.plugins = objects.listProperty(String.class);
        this.quiet = objects.property(Boolean.class);

        ElasticRunnerConfig defaults = ElasticRunnerConfig.defaults();
        Map<String, String> defaultSettings = new LinkedHashMap<>(defaults.settings());
        defaultSettings.put("cluster.routing.allocation.disk.threshold_enabled", "false");
        this.distrosDir.convention(layout.getProjectDirectory()
                .dir(".gradle/elasticsearch/distros")
                .getAsFile()
                .getAbsolutePath());
        this.download.convention(defaults.download());
        this.downloadBaseUrl.convention(defaults.downloadBaseUrl());
        this.workDir.convention(layout.getBuildDirectory()
                .dir("elastic-test-clusters/" + name)
                .map(dir -> dir.getAsFile().getAbsolutePath()));
        this.clusterName.convention(name);
        this.httpPort.convention(defaults.httpPort());
        this.portRangeStart.convention(defaults.portRangeStart());
        this.portRangeEnd.convention(defaults.portRangeEnd());
        this.heap.convention(defaults.heap());
        this.startupTimeoutMillis.convention(defaults.startupTimeout().toMillis());
        this.shutdownTimeoutMillis.convention(defaults.shutdownTimeout().toMillis());
        this.settings.convention(defaultSettings);
        this.plugins.convention(defaults.plugins());
        this.quiet.convention(defaults.quiet());
    }

    /**
     * Returns the logical cluster name inside the Gradle DSL container.
     *
     * @return cluster definition name
     */
    public String getName() {
        return name;
    }

    /** @return optional local ZIP path */
    public Property<String> getDistroZip() {
        return distroZip;
    }

    /** @return optional Elasticsearch version */
    public Property<String> getVersion() {
        return version;
    }

    /** @return distro cache directory */
    public Property<String> getDistrosDir() {
        return distrosDir;
    }

    /** @return whether downloads should refresh the cache */
    public Property<Boolean> getDownload() {
        return download;
    }

    /** @return download base URL or mirror prefix */
    public Property<String> getDownloadBaseUrl() {
        return downloadBaseUrl;
    }

    /** @return cluster working directory */
    public Property<String> getWorkDir() {
        return workDir;
    }

    /** @return configured cluster name */
    public Property<String> getClusterName() {
        return clusterName;
    }

    /** @return fixed HTTP port, or zero for ranged allocation */
    public Property<Integer> getHttpPort() {
        return httpPort;
    }

    /** @return start of the HTTP port range */
    public Property<Integer> getPortRangeStart() {
        return portRangeStart;
    }

    /** @return end of the HTTP port range */
    public Property<Integer> getPortRangeEnd() {
        return portRangeEnd;
    }

    /** @return heap setting used for Xms and Xmx */
    public Property<String> getHeap() {
        return heap;
    }

    /** @return startup timeout in milliseconds */
    public Property<Long> getStartupTimeoutMillis() {
        return startupTimeoutMillis;
    }

    /** @return shutdown timeout in milliseconds */
    public Property<Long> getShutdownTimeoutMillis() {
        return shutdownTimeoutMillis;
    }

    /** @return extra Elasticsearch settings */
    public MapProperty<String, String> getSettings() {
        return settings;
    }

    /** @return plugin install list */
    public ListProperty<String> getPlugins() {
        return plugins;
    }

    /** @return quiet output flag */
    public Property<Boolean> getQuiet() {
        return quiet;
    }

    /**
     * Sets the local ZIP path as an arbitrary object convertible to a string.
     *
     * @param value distro ZIP path
     */
    public void distroZip(Object value) {
        distroZip.set(value.toString());
    }

    /**
     * Sets the shared distro cache directory as an arbitrary object convertible
     * to a string.
     *
     * @param value distro cache directory
     */
    public void distrosDir(Object value) {
        distrosDir.set(value.toString());
    }

    /**
     * Sets the cluster working directory as an arbitrary object convertible to
     * a string.
     *
     * @param value working directory
     */
    public void workDir(Object value) {
        workDir.set(value.toString());
    }

    /**
     * Adds or overrides one Elasticsearch setting.
     *
     * @param key Elasticsearch setting key
     * @param value setting value
     */
    public void setting(String key, String value) {
        settings.put(key, value);
    }

    /**
     * Adds one plugin to install before startup.
     *
     * @param pluginId plugin identifier or install source accepted by
     *                 {@code elasticsearch-plugin install}
     */
    public void plugin(String pluginId) {
        plugins.add(pluginId);
    }

    /**
     * Sets the startup timeout using a {@link Duration}.
     *
     * @param duration startup timeout
     */
    public void startupTimeout(Duration duration) {
        startupTimeoutMillis.set(duration.toMillis());
    }

    /**
     * Sets the shutdown timeout using a {@link Duration}.
     *
     * @param duration shutdown timeout
     */
    public void shutdownTimeout(Duration duration) {
        shutdownTimeoutMillis.set(duration.toMillis());
    }
}
