package io.github.wboult.esrunner.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.tasks.testing.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root Gradle plugin that provisions build-scoped shared Elasticsearch clusters
 * and injects their connection details into matching test tasks.
 */
public final class ElasticSharedTestClustersPlugin implements Plugin<Project> {
    /**
     * Applies the plugin to the root project only.
     *
     * @param project project receiving the plugin
     */
    @Override
    public void apply(Project project) {
        if (project != project.getRootProject()) {
            throw new GradleException("io.github.wboult.es-runner.shared-test-clusters must be applied to the root project.");
        }

        ElasticTestClustersExtension extension = project.getExtensions()
                .create("elasticTestClusters", ElasticTestClustersExtension.class);

        project.getGradle().projectsEvaluated(gradle -> configure(project, extension));
    }

    private void configure(Project rootProject, ElasticTestClustersExtension extension) {
        Map<String, Provider<ElasticClusterService>> services = registerServices(rootProject, extension);
        rootProject.getAllprojects().forEach(project -> project.getTasks().withType(Test.class).configureEach(task -> {
            ElasticSuiteBinding binding = extension.getSuites().findByName(task.getName());
            if (binding == null) {
                return;
            }
            String clusterName = binding.getCluster().getOrNull();
            if (clusterName == null || clusterName.isBlank()) {
                throw new GradleException("Suite binding '" + binding.getName() + "' must define useCluster(...).");
            }
            Provider<ElasticClusterService> service = services.get(clusterName);
            if (service == null) {
                throw new GradleException("Suite binding '" + binding.getName()
                        + "' references missing cluster '" + clusterName + "'.");
            }

            task.usesService(service);
            task.getJvmArgumentProviders().add(new ElasticClusterJvmArgumentProvider(
                    service,
                    extension.getBuildId(),
                    task.getPath(),
                    task.getProject().getPath(),
                    task.getName(),
                    binding.getNamespaceMode().get()
            ));
        }));
    }

    private Map<String, Provider<ElasticClusterService>> registerServices(Project rootProject,
                                                                          ElasticTestClustersExtension extension) {
        Map<String, Provider<ElasticClusterService>> services = new LinkedHashMap<>();
        BuildServiceRegistry sharedServices = rootProject.getGradle().getSharedServices();
        extension.getClusters().forEach(spec -> {
            Provider<ElasticClusterService> provider = sharedServices.registerIfAbsent(
                    serviceName(spec.getName()),
                    ElasticClusterService.class,
                    registration -> spec.copyTo(registration.getParameters())
            );
            services.put(spec.getName(), provider);
        });
        return services;
    }

    private String serviceName(String clusterName) {
        return "elasticCluster_" + clusterName.replaceAll("[^A-Za-z0-9_]+", "_");
    }
}
