package io.github.wboult.esrunner.embedded.opensearch.v3;

import org.opensearch.Version;
import org.opensearch.env.Environment;
import org.opensearch.node.Node;
import org.opensearch.plugins.Plugin;
import org.opensearch.plugins.PluginInfo;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

final class EmbeddedNode extends Node {

    EmbeddedNode(Environment environment, Collection<String> classpathPluginClassNames) {
        super(environment, createPluginInfos(classpathPluginClassNames), true);
    }

    private static Collection<PluginInfo> createPluginInfos(Collection<String> classpathPluginClassNames) {
        Set<PluginInfo> plugins = new LinkedHashSet<>();
        for (String className : classpathPluginClassNames) {
            Class<? extends Plugin> pluginClass = loadPluginClass(className);
            plugins.add(new PluginInfo(
                    pluginClass.getName(),
                    "classpath plugin",
                    "NA",
                    Version.CURRENT,
                    System.getProperty("java.specification.version"),
                    pluginClass.getName(),
                    java.util.List.of(),
                    false));
        }
        return plugins;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Plugin> loadPluginClass(String className) {
        try {
            Class<?> type = Class.forName(className);
            if (Plugin.class.isAssignableFrom(type) == false) {
                throw new IllegalArgumentException("Classpath plugin is not an OpenSearch Plugin: " + className);
            }
            return (Class<? extends Plugin>) type;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Classpath plugin not found: " + className, e);
        }
    }
}
