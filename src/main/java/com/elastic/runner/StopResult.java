package com.elastic.runner;

import java.time.Duration;

public record StopResult(boolean wasRunning, boolean graceful, boolean forced, Duration waitTime) {
    public boolean stopped() {
        return !wasRunning || graceful || forced;
    }
}
