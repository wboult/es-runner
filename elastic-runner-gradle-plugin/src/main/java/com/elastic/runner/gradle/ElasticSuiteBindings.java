package com.elastic.runner.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;

/**
 * Fluent wrapper around the named suite binding container exposed in the DSL.
 */
public final class ElasticSuiteBindings {
    private final NamedDomainObjectContainer<ElasticSuiteBinding> bindings;

    ElasticSuiteBindings(NamedDomainObjectContainer<ElasticSuiteBinding> bindings) {
        this.bindings = bindings;
    }

    /**
     * Returns the underlying named binding container.
     *
     * @return binding container
     */
    public NamedDomainObjectContainer<ElasticSuiteBinding> getBindings() {
        return bindings;
    }

    /**
     * Registers a new suite binding.
     *
     * @param name suite name
     * @param action binding configuration
     */
    public void register(String name, Action<? super ElasticSuiteBinding> action) {
        bindings.register(name, action);
    }

    /**
     * Registers or configures a suite binding using Groovy DSL syntax.
     *
     * @param name suite name
     * @param closure binding configuration
     */
    public void register(String name, Closure<?> closure) {
        ElasticSuiteBinding binding = bindings.maybeCreate(name);
        configureClosure(binding, closure);
    }

    /**
     * Finds or creates a suite binding by name and configures it.
     *
     * @param name suite name
     * @param action binding configuration
     * @return configured binding
     */
    public ElasticSuiteBinding matchingName(String name, Action<? super ElasticSuiteBinding> action) {
        ElasticSuiteBinding binding = bindings.maybeCreate(name);
        action.execute(binding);
        return binding;
    }

    /**
     * Finds or creates a suite binding by name and configures it with a Groovy
     * closure.
     *
     * @param name suite name
     * @param closure binding configuration
     * @return configured binding
     */
    public ElasticSuiteBinding matchingName(String name, Closure<?> closure) {
        ElasticSuiteBinding binding = bindings.maybeCreate(name);
        configureClosure(binding, closure);
        return binding;
    }

    /**
     * Returns an existing binding by name if present.
     *
     * @param name suite name
     * @return existing binding or {@code null}
     */
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
