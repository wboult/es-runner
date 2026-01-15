package com.elastic.runner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PortPickerTest {

    @Test
    void picksPortWithinRange() {
        int port = PortPicker.pick(25000, 25100);
        assertTrue(port >= 25000 && port <= 25100, "Expected port within range, got " + port);
    }
}
