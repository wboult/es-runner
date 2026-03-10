package io.github.wboult.esrunner.embedded.v8;

import org.elasticsearch.env.Environment;
import org.elasticsearch.node.Node;
import org.elasticsearch.plugins.PluginsLoader;

import java.util.Map;

/**
 * Package-private wrapper around Elasticsearch's public embedded node constructor.
 */
final class EmbeddedNode extends Node {

    EmbeddedNode(Environment environment) {
        super(environment, createPluginsLoader(environment));
    }

    private static PluginsLoader createPluginsLoader(Environment environment) {
        return PluginsLoader.createPluginsLoader(
                PluginsLoader.loadModulesBundles(environment.modulesDir()),
                PluginsLoader.loadPluginsBundles(environment.pluginsDir()),
                Map.of(),
                false);
    }
}
