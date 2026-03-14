package io.github.wboult.esrunner;

import java.nio.file.Path;

/**
 * Failure while launching or supervising the external process before it becomes ready.
 */
public final class ProcessStartException extends ElasticRunnerException {
    /**
     * Creates a process-start failure with a message only.
     *
     * @param message failure summary
     */
    public ProcessStartException(String message) {
        super(Kind.PROCESS_START, message);
    }

    /**
     * Creates a process-start failure with an underlying cause.
     *
     * @param message failure summary
     * @param cause underlying failure
     */
    public ProcessStartException(String message, Throwable cause) {
        super(Kind.PROCESS_START, message, cause);
    }

    ProcessStartException(String message, Throwable cause, Path diagnosticsFile) {
        super(Kind.PROCESS_START, message, cause, diagnosticsFile);
    }

    @Override
    ElasticRunnerException copyWith(String message, Throwable cause, Path diagnosticsFile) {
        return new ProcessStartException(message, cause, diagnosticsFile);
    }
}
