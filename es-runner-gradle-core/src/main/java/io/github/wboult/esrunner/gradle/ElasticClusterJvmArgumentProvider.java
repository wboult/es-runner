package io.github.wboult.esrunner.gradle;

import org.gradle.api.provider.Provider;
import org.gradle.process.CommandLineArgumentProvider;

import java.util.List;

/**
 * Lazily resolves shared cluster metadata only when Gradle is about to fork a
 * test JVM that actually needs it.
 */
public final class ElasticClusterJvmArgumentProvider implements CommandLineArgumentProvider {
    private final Provider<? extends SharedClusterBackend> backend;
    private final String buildId;
    private final String taskPath;
    private final String projectPath;
    private final String taskName;
    private final NamespaceMode namespaceMode;

    /**
     * Creates the lazy argument provider for one bound test task.
     *
     * @param backend shared cluster backend provider
     * @param buildId build-scoped namespace seed
     * @param taskPath full Gradle task path
     * @param projectPath Gradle project path
     * @param taskName Gradle task name
     * @param namespaceMode namespace derivation mode
     */
    public ElasticClusterJvmArgumentProvider(Provider<? extends SharedClusterBackend> backend,
                                             String buildId,
                                             String taskPath,
                                             String projectPath,
                                             String taskName,
                                             NamespaceMode namespaceMode) {
        this.backend = backend;
        this.buildId = buildId;
        this.taskPath = taskPath;
        this.projectPath = projectPath;
        this.taskName = taskName;
        this.namespaceMode = namespaceMode;
    }

    @Override
    public Iterable<String> asArguments() {
        ElasticClusterMetadata metadata = backend.get().metadata();
        String namespace = ElasticNamespace.namespace(buildId, projectPath, taskName, namespaceMode);
        return List.of(
                systemProperty("elastic.runner.baseUri", metadata.baseUri()),
                systemProperty("elastic.runner.httpPort", Integer.toString(metadata.httpPort())),
                systemProperty("elastic.runner.clusterName", metadata.clusterName()),
                systemProperty("elastic.runner.buildId", buildId),
                systemProperty("elastic.runner.suiteId", taskPath),
                systemProperty("elastic.runner.namespace", namespace),
                systemProperty("elastic.runner.resourcePrefix", namespace)
        );
    }

    private static String systemProperty(String name, String value) {
        return "-D" + name + "=" + value;
    }
}
