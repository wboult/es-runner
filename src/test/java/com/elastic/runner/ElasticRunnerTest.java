package com.elastic.runner;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void resolveDistroZipDownloadsFromFileMirror() throws IOException {
        String version = "9.2.4";
        DistroDescriptor descriptor = DistroDescriptor.forVersion(version);
        Path mirrorDir = Files.createTempDirectory("elastic-runner-mirror");
        Path distrosDir = Files.createTempDirectory("elastic-runner-distros");
        Path mirroredZip = mirrorDir.resolve(descriptor.fileName());
        Files.writeString(mirroredZip, "mirror-zip");

        ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
                .version(version)
                .distrosDir(distrosDir)
                .download(true)
                .downloadBaseUrl(mirrorDir.toUri().toString()));

        Path resolved = ElasticRunner.resolveDistroZip(config);

        assertEquals(distrosDir.resolve(descriptor.fileName()).toAbsolutePath(), resolved);
        assertEquals("mirror-zip", Files.readString(resolved));
    }

    @Test
    void resolveHttpPortSettingUsesConfiguredRangeForDynamicPorts() {
        assertEquals("9200-9300", ElasticRunner.resolveHttpPortSetting(ElasticRunnerConfig.defaults()));
    }

    @Test
    void resolveHttpPortSettingUsesExplicitPortWhenConfigured() {
        ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder.httpPort(9250));

        assertEquals("9250", ElasticRunner.resolveHttpPortSetting(config));
    }

    @Test
    void findHttpPublishPortIgnoresTransportPublishAddress() {
        List<String> lines = List.of(
                "[2026-03-08T10:00:00,000][INFO ][o.e.t.TransportService   ] publish_address {127.0.0.1:9307}, bound_addresses {127.0.0.1:9307}",
                "[2026-03-08T10:00:01,000][INFO ][o.e.h.AbstractHttpServerTransport] publish_address {127.0.0.1:62751}, bound_addresses {127.0.0.1:62751}"
        );

        assertEquals(62751, ElasticRunner.findHttpPublishPort(lines).orElseThrow());
    }

    @Test
    void findHttpPublishPortSupportsIpv6Addresses() {
        List<String> lines = List.of(
                "[2026-03-08T10:00:01,000][INFO ][o.e.h.AbstractHttpServerTransport] publish_address {[::1]:62123}, bound_addresses {[::1]:62123}"
        );

        assertTrue(ElasticRunner.findHttpPublishPort(lines).isPresent());
        assertEquals(62123, ElasticRunner.findHttpPublishPort(lines).getAsInt());
    }
}
