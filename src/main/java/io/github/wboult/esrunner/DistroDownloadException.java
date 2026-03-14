package io.github.wboult.esrunner;

import java.nio.file.Path;

/**
 * Failure while downloading a distro archive.
 */
public final class DistroDownloadException extends ElasticRunnerException {
    /**
     * Creates a download failure with a message only.
     *
     * @param message failure summary
     */
    public DistroDownloadException(String message) {
        super(Kind.DISTRO_DOWNLOAD, message);
    }

    /**
     * Creates a download failure with an underlying cause.
     *
     * @param message failure summary
     * @param cause underlying failure
     */
    public DistroDownloadException(String message, Throwable cause) {
        super(Kind.DISTRO_DOWNLOAD, message, cause);
    }

    DistroDownloadException(String message, Throwable cause, Path diagnosticsFile) {
        super(Kind.DISTRO_DOWNLOAD, message, cause, diagnosticsFile);
    }

    @Override
    ElasticRunnerException copyWith(String message, Throwable cause, Path diagnosticsFile) {
        return new DistroDownloadException(message, cause, diagnosticsFile);
    }
}
