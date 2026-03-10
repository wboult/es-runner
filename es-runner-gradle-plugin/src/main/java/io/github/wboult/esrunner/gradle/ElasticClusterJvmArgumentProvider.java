package io.github.wboult.esrunner.gradle;

import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.api.provider.Provider;

import java.util.List;

/**
 * Lazily resolves shared cluster metadata only when Gradle is about to fork a
 * test JVM that actually needs it.
 */
final class ElasticClusterJvmArgumentProvider implements CommandLineArgumentProvider {
    private final Provider<ElasticClusterService> service;
    private final String buildId;
    private final String taskPath;
    private final String projectPath;
    private final String taskName;
    private final NamespaceMode namespaceMode;

    ElasticClusterJvmArgumentProvider(Provider<ElasticClusterService> service,
                                      String buildId,
                                      String taskPath,
                                      String projectPath,
                                      String taskName,
                                      NamespaceMode namespaceMode) {
        this.service = service;
        this.buildId = buildId;
        this.taskPath = taskPath;
        this.projectPath = projectPath;
        this.taskName = taskName;
        this.namespaceMode = namespaceMode;
    }

    @Override
    public Iterable<String> asArguments() {
        ElasticClusterMetadata metadata = service.get().metadata();
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
