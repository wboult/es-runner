package io.github.wboult.esrunner;

import java.time.Duration;

/**
 * Outcome of a stop request against a running or already-stopped server.
 *
 * @param wasRunning whether the server was still running when stop was invoked
 * @param graceful whether shutdown completed without a forced termination
 * @param forced whether the process tree had to be killed forcibly
 * @param waitTime how long shutdown took
 */
public record StopResult(boolean wasRunning, boolean graceful, boolean forced, Duration waitTime) {
    /**
     * Returns whether the server is considered stopped after the operation.
     *
     * @return {@code true} when the process was already down or was terminated
     */
    public boolean stopped() {
        return !wasRunning || graceful || forced;
    }
}
