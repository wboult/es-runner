package io.github.wboult.esrunner.embedded.opensearch.v2;

import org.opensearch.env.Environment;
import org.opensearch.node.Node;
import org.opensearch.plugins.Plugin;

import java.util.Collection;

final class EmbeddedNode extends Node {

    EmbeddedNode(Environment environment, Collection<Class<? extends Plugin>> classpathPlugins) {
        super(environment, classpathPlugins, true);
    }
}
