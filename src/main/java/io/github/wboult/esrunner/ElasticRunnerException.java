package io.github.wboult.esrunner;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Base unchecked exception for ES Runner failures.
 *
 * <p>Callers that need to distinguish failure modes can either catch one of
 * the more specific subclasses or inspect {@link #kind()}.</p>
 */
public class ElasticRunnerException extends RuntimeException {
    /**
     * High-level failure category for one ES Runner exception.
     */
    public enum Kind {
        /** Generic uncategorized failure. */
        GENERAL,
        /** Failure while resolving or validating the requested distro archive. */
        DISTRO_RESOLUTION,
        /** Failure while downloading a distro archive. */
        DISTRO_DOWNLOAD,
        /** Failure while preparing or launching the external process. */
        PROCESS_START,
        /** Failure because the node did not become ready before the startup timeout. */
        STARTUP_TIMEOUT,
        /** Failure while waiting for the HTTP listener to bind. */
        PORT_BINDING,
        /** Failure while installing a configured plugin. */
        PLUGIN_INSTALL
    }

    private final Kind kind;
    private Path diagnosticsFile;

    /**
     * Creates a general failure with a message only.
     *
     * @param message failure summary
     */
    public ElasticRunnerException(String message) {
        this(Kind.GENERAL, message, null, null);
    }

    /**
     * Creates a general failure with both a message and an underlying cause.
     *
     * @param message failure summary
     * @param cause underlying failure
     */
    public ElasticRunnerException(String message, Throwable cause) {
        this(Kind.GENERAL, message, cause, null);
    }

    /**
     * Creates a general failure with a message, cause, and diagnostics file path.
     *
     * @param message failure summary
     * @param cause underlying failure
     * @param diagnosticsFile diagnostics file written for this failure
     */
    public ElasticRunnerException(String message, Throwable cause, Path diagnosticsFile) {
        this(Kind.GENERAL, message, cause, diagnosticsFile);
    }

    /**
     * Creates a categorized failure.
     *
     * @param kind failure kind
     * @param message failure summary
     */
    public ElasticRunnerException(Kind kind, String message) {
        this(kind, message, null, null);
    }

    /**
     * Creates a categorized failure with an underlying cause.
     *
     * @param kind failure kind
     * @param message failure summary
     * @param cause underlying failure
     */
    public ElasticRunnerException(Kind kind, String message, Throwable cause) {
        this(kind, message, cause, null);
    }

    /**
     * Creates a categorized failure with a diagnostics file path.
     *
     * @param kind failure kind
     * @param message failure summary
     * @param cause underlying failure
     * @param diagnosticsFile diagnostics file written for this failure
     */
    public ElasticRunnerException(Kind kind, String message, Throwable cause, Path diagnosticsFile) {
        super(message, cause);
        this.kind = kind == null ? Kind.GENERAL : kind;
        this.diagnosticsFile = diagnosticsFile;
    }

    /**
     * Returns the high-level failure category for this exception.
     *
     * @return failure kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the optional diagnostics file created for this failure.
     *
     * @return diagnostics file path when one was written
     */
    public Optional<Path> diagnosticsFile() {
        return Optional.ofNullable(diagnosticsFile);
    }

    ElasticRunnerException copyWith(String message, Throwable cause, Path diagnosticsFile) {
        return new ElasticRunnerException(kind, message, cause, diagnosticsFile);
    }
}
