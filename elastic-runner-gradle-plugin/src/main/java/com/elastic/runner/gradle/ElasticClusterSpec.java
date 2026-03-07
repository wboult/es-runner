package com.elastic.runner.gradle;

import com.elastic.runner.ElasticRunnerConfig;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.time.Duration;

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
        this.settings.convention(defaults.settings());
        this.plugins.convention(defaults.plugins());
        this.quiet.convention(defaults.quiet());
    }

    public String getName() {
        return name;
    }

    public Property<String> getDistroZip() {
        return distroZip;
    }

    public Property<String> getVersion() {
        return version;
    }

    public Property<String> getDistrosDir() {
        return distrosDir;
    }

    public Property<Boolean> getDownload() {
        return download;
    }

    public Property<String> getDownloadBaseUrl() {
        return downloadBaseUrl;
    }

    public Property<String> getWorkDir() {
        return workDir;
    }

    public Property<String> getClusterName() {
        return clusterName;
    }

    public Property<Integer> getHttpPort() {
        return httpPort;
    }

    public Property<Integer> getPortRangeStart() {
        return portRangeStart;
    }

    public Property<Integer> getPortRangeEnd() {
        return portRangeEnd;
    }

    public Property<String> getHeap() {
        return heap;
    }

    public Property<Long> getStartupTimeoutMillis() {
        return startupTimeoutMillis;
    }

    public Property<Long> getShutdownTimeoutMillis() {
        return shutdownTimeoutMillis;
    }

    public MapProperty<String, String> getSettings() {
        return settings;
    }

    public ListProperty<String> getPlugins() {
        return plugins;
    }

    public Property<Boolean> getQuiet() {
        return quiet;
    }

    public void distroZip(Object value) {
        distroZip.set(value.toString());
    }

    public void distrosDir(Object value) {
        distrosDir.set(value.toString());
    }

    public void workDir(Object value) {
        workDir.set(value.toString());
    }

    public void setting(String key, String value) {
        settings.put(key, value);
    }

    public void plugin(String pluginId) {
        plugins.add(pluginId);
    }

    public void startupTimeout(Duration duration) {
        startupTimeoutMillis.set(duration.toMillis());
    }

    public void shutdownTimeout(Duration duration) {
        shutdownTimeoutMillis.set(duration.toMillis());
    }
}
