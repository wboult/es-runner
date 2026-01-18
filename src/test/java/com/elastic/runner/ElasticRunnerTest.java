package com.elastic.runner;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElasticRunnerTest {

    @Test
    void resolveDistroZipUsesExplicitZip() throws IOException {
        Path tempDir = Files.createTempDirectory("elastic-runner-zip");
        Path zip = tempDir.resolve("elasticsearch.zip").toAbsolutePath();
        Files.writeString(zip, "fake");

        ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder.distroZip(zip));

        assertEquals(zip, ElasticRunner.resolveDistroZip(config));
    }

    @Test
    void resolveDistroZipUsesVersionAndDistrosDir() throws IOException {
        Path distrosDir = Files.createTempDirectory("elastic-runner-distros");
        String version = "9.2.4";
        DistroDescriptor descriptor = DistroDescriptor.forVersion(version);
        Path zip = distrosDir.resolve(descriptor.fileName()).toAbsolutePath();
        Files.writeString(zip, "fake");

        ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
                .version(version)
                .distrosDir(distrosDir)
                .download(false));

        assertEquals(zip, ElasticRunner.resolveDistroZip(config));
    }
}
