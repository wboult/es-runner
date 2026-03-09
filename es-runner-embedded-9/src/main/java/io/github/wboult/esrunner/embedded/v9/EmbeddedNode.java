package io.github.wboult.esrunner.embedded.v9;

import org.elasticsearch.env.Environment;
import org.elasticsearch.node.Node;
import org.elasticsearch.plugins.PluginsLoader;

import java.util.Map;

/**
 * Package-private wrapper around Elasticsearch's public embedded node constructor.
 *
 * <p>ES 9.3.x no longer exposes the old 8.1-era classpath-plugin constructor shape. The
 * supported public entry point is {@link Node#Node(Environment, PluginsLoader)}. We mirror the
 * standalone server by building a {@link PluginsLoader} from the staged embedded home's
 * {@code modules/} and {@code plugins/} directories.
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
