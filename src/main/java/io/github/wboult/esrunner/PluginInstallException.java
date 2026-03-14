package io.github.wboult.esrunner;

import java.nio.file.Path;

/**
 * Failure while installing one requested plugin before startup.
 */
public final class PluginInstallException extends ElasticRunnerException {
    /**
     * Creates a plugin-install failure with a message only.
     *
     * @param message failure summary
     */
    public PluginInstallException(String message) {
        super(Kind.PLUGIN_INSTALL, message);
    }

    /**
     * Creates a plugin-install failure with an underlying cause.
     *
     * @param message failure summary
     * @param cause underlying failure
     */
    public PluginInstallException(String message, Throwable cause) {
        super(Kind.PLUGIN_INSTALL, message, cause);
    }

    PluginInstallException(String message, Throwable cause, Path diagnosticsFile) {
        super(Kind.PLUGIN_INSTALL, message, cause, diagnosticsFile);
    }

    @Override
    ElasticRunnerException copyWith(String message, Throwable cause, Path diagnosticsFile) {
        return new PluginInstallException(message, cause, diagnosticsFile);
    }
}
