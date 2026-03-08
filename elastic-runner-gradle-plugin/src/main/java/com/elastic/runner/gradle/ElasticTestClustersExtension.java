package com.elastic.runner.gradle;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.time.Instant;

public abstract class ElasticTestClustersExtension {
    private final NamedDomainObjectContainer<ElasticClusterSpec> clusters;
    private final ElasticSuiteBindings suites;
    private final String buildId;

    @Inject
    public ElasticTestClustersExtension(ObjectFactory objects, ProjectLayout layout) {
        this.clusters = objects.domainObjectContainer(ElasticClusterSpec.class,
                name -> objects.newInstance(ElasticClusterSpec.class, name, objects, layout));
        NamedDomainObjectContainer<ElasticSuiteBinding> bindings =
                objects.domainObjectContainer(ElasticSuiteBinding.class,
                        name -> objects.newInstance(ElasticSuiteBinding.class, name, objects));
        this.suites = new ElasticSuiteBindings(bindings);
        this.buildId = "er" + Long.toUnsignedString(Instant.now().toEpochMilli(), 36);
    }

    public NamedDomainObjectContainer<ElasticClusterSpec> getClusters() {
        return clusters;
    }

    public void clusters(Action<? super NamedDomainObjectContainer<ElasticClusterSpec>> action) {
        action.execute(clusters);
    }

    public ElasticSuiteBindings getSuites() {
        return suites;
    }

    public void suites(Action<? super ElasticSuiteBindings> action) {
        action.execute(suites);
    }

    public String getBuildId() {
        return buildId;
    }
}
