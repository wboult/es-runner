package io.github.wboult.esrunner.embedded;

import org.elasticsearch.env.Environment;
import org.elasticsearch.node.Node;
import org.elasticsearch.plugins.Plugin;

import java.util.Collection;

/**
 * Package-private subclass that exists solely to expose Node's protected constructor.
 *
 * <p>Elasticsearch's {@link Node} has two constructors:
 * <ul>
 *   <li>Public {@code Node(Environment)} — no classpath plugins; Node loads everything from the
 *       filesystem at {@code path.home/modules/} and {@code path.home/plugins/}.</li>
 *   <li>Protected {@code Node(Environment, Collection&lt;Class&lt;? extends Plugin&gt;&gt;, boolean)} —
 *       allows injecting plugin classes directly from the classpath (used by ES test framework).</li>
 * </ul>
 *
 * <p>We use the <em>public</em> constructor via the parent class ({@code super(environment)}) when
 * calling {@link EmbeddedNode#EmbeddedNode(Environment)} so that all standard modules are loaded
 * from the distribution's {@code modules/} directory.  This is the simplest way to get
 * {@code analysis-common} (and every other standard module) working without any classpath tricks —
 * those jars are not published to Maven Central or the Elastic Maven repo separately.
 *
 * <p>The protected constructor variant is kept for future experimentation where specific classpath
 * plugins might be provided instead.
 */
final class EmbeddedNode extends Node {

    /**
     * Filesystem-modules constructor.
     *
     * <p>No classpath plugins are injected. The node loads all modules (analysis-common,
     * transport-netty4, x-pack-core, …) from {@code environment.modulesFile()} on the filesystem,
     * which must correspond to a real, extracted Elasticsearch distribution.
     *
     * @param environment prepared by {@link org.elasticsearch.node.internal.InternalSettingsPreparer}
     *                    with {@code path.home} pointing at the extracted ES distribution root
     */
    EmbeddedNode(Environment environment) {
        // Call the public Node(Environment) constructor via super.
        // Node will discover and load all modules from esHome/modules/.
        super(environment);
    }

    /**
     * Classpath-plugins constructor (kept for future experimentation).
     *
     * <p>Instead of loading modules from the filesystem, the provided plugin classes are
     * instantiated directly.  This is the approach used by Elasticsearch's own test framework
     * ({@code ESSingleNodeTestCase}), but it requires each needed module class to be present
     * on the classpath — which is difficult for unpublished modules like {@code analysis-common}.
     *
     * @param environment     prepared environment (path.home still used for data/logs/config)
     * @param classpathPlugins plugin classes to load directly; must be on the test classpath
     */
    EmbeddedNode(Environment environment, Collection<Class<? extends Plugin>> classpathPlugins) {
        super(environment, classpathPlugins, false);
    }
}
