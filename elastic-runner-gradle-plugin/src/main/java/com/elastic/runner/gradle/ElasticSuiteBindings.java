package com.elastic.runner.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;

public final class ElasticSuiteBindings {
    private final NamedDomainObjectContainer<ElasticSuiteBinding> bindings;

    ElasticSuiteBindings(NamedDomainObjectContainer<ElasticSuiteBinding> bindings) {
        this.bindings = bindings;
    }

    public NamedDomainObjectContainer<ElasticSuiteBinding> getBindings() {
        return bindings;
    }

    public void register(String name, Action<? super ElasticSuiteBinding> action) {
        bindings.register(name, action);
    }

    public void register(String name, Closure<?> closure) {
        ElasticSuiteBinding binding = bindings.maybeCreate(name);
        configureClosure(binding, closure);
    }

    public ElasticSuiteBinding matchingName(String name, Action<? super ElasticSuiteBinding> action) {
        ElasticSuiteBinding binding = bindings.maybeCreate(name);
        action.execute(binding);
        return binding;
    }

    public ElasticSuiteBinding matchingName(String name, Closure<?> closure) {
        ElasticSuiteBinding binding = bindings.maybeCreate(name);
        configureClosure(binding, closure);
        return binding;
    }

    public ElasticSuiteBinding findByName(String name) {
        return bindings.findByName(name);
    }

    private void configureClosure(ElasticSuiteBinding binding, Closure<?> closure) {
        Closure<?> cloned = (Closure<?>) closure.clone();
        cloned.setResolveStrategy(Closure.DELEGATE_FIRST);
        cloned.setDelegate(binding);
        cloned.call(binding);
    }
}
