package com.elastic.runner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DistroDescriptorTest {

    @Test
    void buildsFileNameFromVersionAndOs() {
        DistroDescriptor descriptor = DistroDescriptor.forVersion("9.2.4");
        String fileName = descriptor.fileName();

        assertTrue(fileName.contains("9.2.4"));
        assertTrue(fileName.contains(Os.classifier()));
        assertTrue(fileName.endsWith("." + Os.archiveExtension()));
    }
}
