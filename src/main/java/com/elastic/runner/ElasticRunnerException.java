package com.elastic.runner;

public class ElasticRunnerException extends RuntimeException {
    public ElasticRunnerException(String message) {
        super(message);
    }

    public ElasticRunnerException(String message, Throwable cause) {
        super(message, cause);
    }
}
