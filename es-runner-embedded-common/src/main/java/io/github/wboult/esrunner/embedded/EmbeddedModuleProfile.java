package io.github.wboult.esrunner.embedded;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bundled embedded-runtime profile for one Elasticsearch major line.
 */
public record EmbeddedModuleProfile(
        String elasticsearchVersion,
        int javaVersion,
        Set<String> includedModules
) {
    /**
     * Normalizes loaded profile values.
     *
     * @param elasticsearchVersion Elasticsearch version for this profile
     * @param javaVersion minimum Java version required by this profile
     * @param includedModules built-in modules staged into the embedded home
     */
    public EmbeddedModuleProfile {
        Objects.requireNonNull(elasticsearchVersion, "elasticsearchVersion");
        includedModules = includedModules == null ? Set.of() : Set.copyOf(includedModules);
    }

    /**
     * Loads a bundled embedded profile from resources adjacent to the given class.
     *
     * @param anchor resource lookup anchor
     * @param propertiesResource profile properties resource name
     * @return loaded profile
     */
    public static EmbeddedModuleProfile load(Class<?> anchor, String propertiesResource) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(propertiesResource, "propertiesResource");

        Properties properties = new Properties();
        try (InputStream input = anchor.getResourceAsStream(propertiesResource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing embedded profile resource " + propertiesResource
                        + " next to " + anchor.getName());
            }
            properties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read embedded profile " + propertiesResource, e);
        }

        String version = properties.getProperty("elasticsearch.version");
        int javaVersion = Integer.parseInt(properties.getProperty("java.version"));
        String moduleResource = properties.getProperty("modules.resource");

        try (InputStream modulesInput = anchor.getResourceAsStream(moduleResource)) {
            if (modulesInput == null) {
                throw new IllegalArgumentException("Missing embedded module list " + moduleResource
                        + " next to " + anchor.getName());
            }
            List<String> modules = new String(modulesInput.readAllBytes())
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .collect(Collectors.toList());
            return new EmbeddedModuleProfile(version, javaVersion, new LinkedHashSet<>(modules));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read embedded module list " + moduleResource, e);
        }
    }
}
