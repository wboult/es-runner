package io.github.wboult.esrunner.embedded;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedHomeTest {

    @Test
    void prepareBundledHomeCopiesRequiredStructureAndFiltersToBundledModules(@TempDir Path tempDir) throws Exception {
        Path rawHome = tempDir.resolve("raw-home");
        Files.createDirectories(rawHome.resolve("config"));
        Files.writeString(rawHome.resolve("config").resolve("elasticsearch.yml"), "# test\n");
        Files.createDirectories(rawHome.resolve("lib"));
        Files.writeString(rawHome.resolve("lib").resolve("elasticsearch-9.3.1.jar"), "stub");
        Files.createDirectories(rawHome.resolve("modules").resolve("transport-netty4"));
        Files.writeString(
                rawHome.resolve("modules").resolve("transport-netty4").resolve("plugin-descriptor.properties"),
                "name=transport-netty4\nmodulename=org.elasticsearch.transport.netty4\n");
        Files.createDirectories(rawHome.resolve("modules").resolve("analysis-common"));
        Files.writeString(rawHome.resolve("modules").resolve("analysis-common").resolve("plugin-descriptor.properties"), "name=analysis-common\n");
        Files.createDirectories(rawHome.resolve("modules").resolve("x-pack-ml"));
        Files.writeString(rawHome.resolve("modules").resolve("x-pack-ml").resolve("plugin-descriptor.properties"), "name=x-pack-ml\n");
        Files.createDirectories(rawHome.resolve("modules").resolve("x-pack-esql"));
        Files.writeString(rawHome.resolve("modules").resolve("x-pack-esql").resolve("plugin-descriptor.properties"), "name=x-pack-esql\n");
        Files.createDirectories(rawHome.resolve("modules").resolve("x-pack-security"));
        Files.writeString(rawHome.resolve("modules").resolve("x-pack-security").resolve("plugin-descriptor.properties"), "name=x-pack-security\n");

        Path staged = EmbeddedHome.prepareBundledHome(
                rawHome,
                tempDir.resolve("staged-home"),
                Set.of("transport-netty4", "analysis-common"));

        assertTrue(Files.exists(staged.resolve("config").resolve("elasticsearch.yml")));
        assertTrue(Files.exists(staged.resolve("lib").resolve("elasticsearch-9.3.1.jar")));
        assertTrue(Files.isDirectory(staged.resolve("modules").resolve("transport-netty4")));
        assertTrue(Files.isDirectory(staged.resolve("modules").resolve("analysis-common")));
        assertFalse(Files.readString(
                staged.resolve("modules").resolve("transport-netty4").resolve("plugin-descriptor.properties"))
                .contains("modulename="));
        assertFalse(Files.exists(staged.resolve("modules").resolve("x-pack-ml")));
        assertFalse(Files.exists(staged.resolve("modules").resolve("x-pack-esql")));
        assertFalse(Files.exists(staged.resolve("modules").resolve("x-pack-security")));
        assertTrue(Files.isDirectory(staged.resolve("plugins")));
    }

    @Test
    void prepareClasspathPluginHomeCopiesConfigAndLibButLeavesPluginDirsEmpty(@TempDir Path tempDir) throws Exception {
        Path rawHome = tempDir.resolve("raw-opensearch-home");
        Files.createDirectories(rawHome.resolve("config"));
        Files.writeString(rawHome.resolve("config").resolve("opensearch.yml"), "# test\n");
        Files.createDirectories(rawHome.resolve("lib"));
        Files.writeString(rawHome.resolve("lib").resolve("opensearch-3.5.0.jar"), "stub");
        Files.createDirectories(rawHome.resolve("plugins").resolve("security"));
        Files.createDirectories(rawHome.resolve("modules").resolve("unused"));

        Path staged = EmbeddedHome.prepareClasspathPluginHome(rawHome, tempDir.resolve("staged-classpath-home"));

        assertTrue(Files.exists(staged.resolve("config").resolve("opensearch.yml")));
        assertTrue(Files.exists(staged.resolve("lib").resolve("opensearch-3.5.0.jar")));
        assertTrue(Files.isDirectory(staged.resolve("plugins")));
        assertTrue(Files.isDirectory(staged.resolve("modules")));
        assertFalse(Files.exists(staged.resolve("plugins").resolve("security")));
        assertFalse(Files.exists(staged.resolve("modules").resolve("unused")));
    }
}
