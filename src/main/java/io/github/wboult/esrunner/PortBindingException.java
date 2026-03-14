package io.github.wboult.esrunner;

import java.nio.file.Path;

/**
 * Failure while waiting for the HTTP listener to bind and publish a usable port.
 */
public final class PortBindingException extends ElasticRunnerException {
    /**
     * Creates a port-binding failure with a message only.
     *
     * @param message failure summary
     */
    public PortBindingException(String message) {
        super(Kind.PORT_BINDING, message);
    }

    /**
     * Creates a port-binding failure with an underlying cause.
     *
     * @param message failure summary
     * @param cause underlying failure
     */
    public PortBindingException(String message, Throwable cause) {
        super(Kind.PORT_BINDING, message, cause);
    }

    PortBindingException(String message, Throwable cause, Path diagnosticsFile) {
        super(Kind.PORT_BINDING, message, cause, diagnosticsFile);
    }

    @Override
    ElasticRunnerException copyWith(String message, Throwable cause, Path diagnosticsFile) {
        return new PortBindingException(message, cause, diagnosticsFile);
    }
}
