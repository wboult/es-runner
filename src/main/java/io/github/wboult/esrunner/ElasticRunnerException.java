package io.github.wboult.esrunner;

/**
 * Unchecked exception thrown when ES Runner cannot prepare, start, or stop
 * an Elasticsearch distribution successfully.
 */
public class ElasticRunnerException extends RuntimeException {
    /**
     * Creates an exception with a message only.
     *
     * @param message failure summary
     */
    public ElasticRunnerException(String message) {
        super(message);
    }

    /**
     * Creates an exception with both a message and an underlying cause.
     *
     * @param message failure summary
     * @param cause underlying failure
     */
    public ElasticRunnerException(String message, Throwable cause) {
        super(message, cause);
    }
}
