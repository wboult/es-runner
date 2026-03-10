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
                    registration -> {
                        registration.getParameters().getName().set(spec.getName());
                        registration.getParameters().getDistroZip().set(spec.getDistroZip());
                        registration.getParameters().getVersion().set(spec.getVersion());
                        registration.getParameters().getDistrosDir().set(spec.getDistrosDir());
                        registration.getParameters().getDownload().set(spec.getDownload());
                        registration.getParameters().getDownloadBaseUrl().set(spec.getDownloadBaseUrl());
                        registration.getParameters().getWorkDir().set(spec.getWorkDir());
                        registration.getParameters().getClusterName().set(spec.getClusterName());
                        registration.getParameters().getHttpPort().set(spec.getHttpPort());
                        registration.getParameters().getPortRangeStart().set(spec.getPortRangeStart());
                        registration.getParameters().getPortRangeEnd().set(spec.getPortRangeEnd());
                        registration.getParameters().getHeap().set(spec.getHeap());
                        registration.getParameters().getStartupTimeoutMillis().set(spec.getStartupTimeoutMillis());
                        registration.getParameters().getShutdownTimeoutMillis().set(spec.getShutdownTimeoutMillis());
                        registration.getParameters().getSettings().set(spec.getSettings());
                        registration.getParameters().getPlugins().set(spec.getPlugins());
                        registration.getParameters().getQuiet().set(spec.getQuiet());
                    }
            );
            services.put(spec.getName(), provider);
        });
        return services;
    }

    private String serviceName(String clusterName) {
        return "elasticCluster_" + clusterName.replaceAll("[^A-Za-z0-9_]+", "_");
    }
}
