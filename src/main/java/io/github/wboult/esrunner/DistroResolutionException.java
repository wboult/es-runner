package io.github.wboult.esrunner;

import java.nio.file.Path;

/**
 * Failure while resolving or validating the requested distro archive.
 */
public final class DistroResolutionException extends ElasticRunnerException {
    /**
     * Creates a resolution failure with a message only.
     *
     * @param message failure summary
     */
    public DistroResolutionException(String message) {
        super(Kind.DISTRO_RESOLUTION, message);
    }

    /**
     * Creates a resolution failure with an underlying cause.
     *
     * @param message failure summary
     * @param cause underlying failure
     */
    public DistroResolutionException(String message, Throwable cause) {
        super(Kind.DISTRO_RESOLUTION, message, cause);
    }

    DistroResolutionException(String message, Throwable cause, Path diagnosticsFile) {
        super(Kind.DISTRO_RESOLUTION, message, cause, diagnosticsFile);
    }

    @Override
    ElasticRunnerException copyWith(String message, Throwable cause, Path diagnosticsFile) {
        return new DistroResolutionException(message, cause, diagnosticsFile);
    }
}
