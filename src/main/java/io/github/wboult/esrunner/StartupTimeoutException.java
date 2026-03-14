package io.github.wboult.esrunner;

import java.nio.file.Path;

/**
 * Failure because the node did not become ready before the configured startup timeout.
 */
public final class StartupTimeoutException extends ElasticRunnerException {
    /**
     * Creates a startup-timeout failure with a message only.
     *
     * @param message failure summary
     */
    public StartupTimeoutException(String message) {
        super(Kind.STARTUP_TIMEOUT, message);
    }

    /**
     * Creates a startup-timeout failure with an underlying cause.
     *
     * @param message failure summary
     * @param cause underlying failure
     */
    public StartupTimeoutException(String message, Throwable cause) {
        super(Kind.STARTUP_TIMEOUT, message, cause);
    }

    StartupTimeoutException(String message, Throwable cause, Path diagnosticsFile) {
        super(Kind.STARTUP_TIMEOUT, message, cause, diagnosticsFile);
    }

    @Override
    ElasticRunnerException copyWith(String message, Throwable cause, Path diagnosticsFile) {
        return new StartupTimeoutException(message, cause, diagnosticsFile);
    }
}
