package io.github.wboult.esrunner.gradle;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Binds one Gradle test suite or task name to a shared cluster definition.
 */
public abstract class ElasticSuiteBinding {
    private final String name;
    private final Property<String> cluster;
    private final Property<NamespaceMode> namespaceMode;

    /**
     * Creates a suite binding.
     *
     * @param name suite or task name
     * @param objects Gradle object factory
     */
    @Inject
    public ElasticSuiteBinding(String name, ObjectFactory objects) {
        this.name = name;
        this.cluster = objects.property(String.class);
        this.namespaceMode = objects.property(NamespaceMode.class);
        this.namespaceMode.convention(NamespaceMode.SUITE);
    }

    /**
     * Returns the suite name used to match Gradle test tasks.
     *
     * @return suite name
     */
    public String getName() {
        return name;
    }

    /** @return bound cluster definition name */
    public Property<String> getCluster() {
        return cluster;
    }

    /** @return namespace derivation strategy */
    public Property<NamespaceMode> getNamespaceMode() {
        return namespaceMode;
    }

    /**
     * Associates this suite with the named shared cluster definition.
     *
     * @param clusterName cluster definition name
     */
    public void useCluster(String clusterName) {
        this.cluster.set(clusterName);
    }
}
