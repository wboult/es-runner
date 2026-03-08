package io.github.wboult.esrunner.gradle;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.util.UUID;

/**
 * Root DSL extension used to define shared Elasticsearch clusters and bind test
 * suites to them.
 */
public abstract class ElasticTestClustersExtension {
    private final NamedDomainObjectContainer<ElasticClusterSpec> clusters;
    private final ElasticSuiteBindings suites;
    private final String buildId;

    /**
     * Creates the extension and its backing named containers.
     *
     * @param objects Gradle object factory
     * @param layout project layout for default directories
     */
    @Inject
    public ElasticTestClustersExtension(ObjectFactory objects, ProjectLayout layout) {
        this.clusters = objects.domainObjectContainer(ElasticClusterSpec.class,
                name -> objects.newInstance(ElasticClusterSpec.class, name, objects, layout));
        NamedDomainObjectContainer<ElasticSuiteBinding> bindings =
                objects.domainObjectContainer(ElasticSuiteBinding.class,
                        name -> objects.newInstance(ElasticSuiteBinding.class, name, objects));
        this.suites = new ElasticSuiteBindings(bindings);
        this.buildId = "er" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Returns the named cluster definitions for the build.
     *
     * @return cluster container
     */
    public NamedDomainObjectContainer<ElasticClusterSpec> getClusters() {
        return clusters;
    }

    /**
     * Configures cluster definitions.
     *
     * @param action cluster configuration action
     */
    public void clusters(Action<? super NamedDomainObjectContainer<ElasticClusterSpec>> action) {
        action.execute(clusters);
    }

    /**
     * Returns suite bindings that map test tasks to shared clusters.
     *
     * @return suite bindings
     */
    public ElasticSuiteBindings getSuites() {
        return suites;
    }

    /**
     * Configures suite bindings.
     *
     * @param action suite binding configuration action
     */
    public void suites(Action<? super ElasticSuiteBindings> action) {
        action.execute(suites);
    }

    /**
     * Returns the build-scoped identifier used in generated namespaces.
     *
     * @return build id
     */
    public String getBuildId() {
        return buildId;
    }
}
