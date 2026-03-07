package com.elastic.runner.gradle;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class ElasticSuiteBinding {
    private final String name;
    private final Property<String> cluster;
    private final Property<NamespaceMode> namespaceMode;

    @Inject
    public ElasticSuiteBinding(String name, ObjectFactory objects) {
        this.name = name;
        this.cluster = objects.property(String.class);
        this.namespaceMode = objects.property(NamespaceMode.class);
        this.namespaceMode.convention(NamespaceMode.SUITE);
    }

    public String getName() {
        return name;
    }

    public Property<String> getCluster() {
        return cluster;
    }

    public Property<NamespaceMode> getNamespaceMode() {
        return namespaceMode;
    }

    public void useCluster(String clusterName) {
        this.cluster.set(clusterName);
    }
}
