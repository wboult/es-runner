package com.elastic.runner.gradle;

/**
 * Strategy used to derive per-test resource namespaces for shared clusters.
 */
public enum NamespaceMode {
    /**
     * Uses build id, project path, and suite name. This is the safest default
     * for parallel integration suites.
     */
    SUITE,

    /**
     * Uses build id and project path only. Use this when multiple suite tasks
     * intentionally share namespaced state inside one project.
     */
    PROJECT
}
