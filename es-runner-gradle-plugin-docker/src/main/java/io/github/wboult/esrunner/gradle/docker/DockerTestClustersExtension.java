package io.github.wboult.esrunner.gradle.docker;

import io.github.wboult.esrunner.gradle.ElasticSuiteBinding;
import io.github.wboult.esrunner.gradle.ElasticSuiteBindings;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.util.UUID;

/**
 * Root DSL extension used to define shared Docker-backed Elasticsearch clusters
 * and bind test suites to them.
 */
public abstract class DockerTestClustersExtension {
    private final NamedDomainObjectContainer<DockerClusterSpec> clusters;
    private final ElasticSuiteBindings suites;
    private final String buildId;

    /**
     * Creates the extension and its backing named containers.
     *
     * @param objects Gradle object factory
     */
    @Inject
    public DockerTestClustersExtension(ObjectFactory objects) {
        this.clusters = objects.domainObjectContainer(DockerClusterSpec.class,
                name -> objects.newInstance(DockerClusterSpec.class, name, objects));
        NamedDomainObjectContainer<ElasticSuiteBinding> bindings =
                objects.domainObjectContainer(ElasticSuiteBinding.class,
                        name -> objects.newInstance(ElasticSuiteBinding.class, name, objects));
        this.suites = new ElasticSuiteBindings(bindings);
        this.buildId = "er" + UUID.randomUUID().toString().replace("-", "");
    }

    /** @return named cluster definitions */
    public NamedDomainObjectContainer<DockerClusterSpec> getClusters() {
        return clusters;
    }

    /**
     * Configures cluster definitions.
     *
     * @param action cluster configuration action
     */
    public void clusters(Action<? super NamedDomainObjectContainer<DockerClusterSpec>> action) {
        action.execute(clusters);
    }

    /** @return suite bindings */
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

    /** @return build-scoped identifier used in generated namespaces */
    public String getBuildId() {
        return buildId;
    }
}
