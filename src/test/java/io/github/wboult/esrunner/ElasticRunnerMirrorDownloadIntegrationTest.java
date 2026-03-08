package io.github.wboult.esrunner;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticRunnerMirrorDownloadIntegrationTest {

    @Test
    void downloadsFromConfiguredMirrorBaseUrl() throws Exception {
        String baseUrl = System.getenv("ES_MIRROR_TEST_BASE_URL");
        Assumptions.assumeTrue(baseUrl != null && !baseUrl.isBlank(), "Mirror test base URL not set");

        String version = System.getenv().getOrDefault("ES_VERSION", "9.3.1");
        String expectedContent = System.getenv("ES_MIRROR_TEST_EXPECTED_CONTENT");
        Path distrosDir = Files.createTempDirectory("es-runner-mirror-it-");

        ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
                .version(version)
                .distrosDir(distrosDir)
                .download(true)
                .downloadBaseUrl(baseUrl));

        Path zip = ElasticRunner.resolveDistroZip(config);

        assertTrue(Files.exists(zip));
        if (expectedContent != null) {
            assertEquals(expectedContent, Files.readString(zip));
        }
    }
}
