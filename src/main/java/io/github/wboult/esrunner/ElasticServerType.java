package io.github.wboult.esrunner;

/**
 * Runtime model used to host a started Elasticsearch server.
 */
public enum ElasticServerType {
    /**
     * Elasticsearch running in a separate OS process.
     */
    PROCESS,

    /**
     * Elasticsearch running inside the current JVM.
     */
    EMBEDDED
}
