package io.github.wboult.esrunner;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Unchecked exception thrown when ES Runner cannot prepare, start, or stop
 * an Elasticsearch distribution successfully.
 */
public class ElasticRunnerException extends RuntimeException {
    private final Path diagnosticsFile;

    /**
     * Creates an exception with a message only.
     *
     * @param message failure summary
     */
    public ElasticRunnerException(String message) {
        this(message, null, null);
    }

    /**
     * Creates an exception with both a message and an underlying cause.
     *
     * @param message failure summary
     * @param cause underlying failure
     */
    public ElasticRunnerException(String message, Throwable cause) {
        this(message, cause, null);
    }

    /**
     * Creates an exception with a message, cause, and diagnostics file path.
     *
     * @param message failure summary
     * @param cause underlying failure
     * @param diagnosticsFile diagnostics file written for this failure
     */
    public ElasticRunnerException(String message, Throwable cause, Path diagnosticsFile) {
        super(message, cause);
        this.diagnosticsFile = diagnosticsFile;
    }

    /**
     * Returns the optional diagnostics file created for this failure.
     *
     * @return diagnostics file path when one was written
     */
    public Optional<Path> diagnosticsFile() {
        return Optional.ofNullable(diagnosticsFile);
    }
}
