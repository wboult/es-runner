package io.github.wboult.esrunner.gradle.docker;

import io.github.wboult.esrunner.gradle.ElasticClusterJvmArgumentProvider;
import io.github.wboult.esrunner.gradle.ElasticSuiteBinding;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.tasks.testing.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root Gradle plugin that provisions build-scoped shared Docker-backed
 * Elasticsearch clusters and injects their connection details into matching
 * test tasks.
 */
public final class DockerSharedTestClustersPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        if (project != project.getRootProject()) {
            throw new GradleException("io.github.wboult.es-runner.docker-shared-test-clusters must be applied to the root project.");
        }

        DockerTestClustersExtension extension = project.getExtensions()
                .create("dockerElasticTestClusters", DockerTestClustersExtension.class);

        project.getGradle().projectsEvaluated(gradle -> configure(project, extension));
    }

    private void configure(Project rootProject, DockerTestClustersExtension extension) {
        Map<String, Provider<DockerClusterService>> services = registerServices(rootProject, extension);
        rootProject.getAllprojects().forEach(project -> project.getTasks().withType(Test.class).configureEach(task -> {
            ElasticSuiteBinding binding = extension.getSuites().findByName(task.getName());
            if (binding == null) {
                return;
            }
            String clusterName = binding.getCluster().getOrNull();
            if (clusterName == null || clusterName.isBlank()) {
                throw new GradleException("Suite binding '" + binding.getName() + "' must define useCluster(...).");
            }
            Provider<DockerClusterService> service = services.get(clusterName);
            if (service == null) {
                throw new GradleException("Suite binding '" + binding.getName()
                        + "' references missing Docker cluster '" + clusterName + "'.");
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

    private Map<String, Provider<DockerClusterService>> registerServices(Project rootProject,
                                                                         DockerTestClustersExtension extension) {
        Map<String, Provider<DockerClusterService>> services = new LinkedHashMap<>();
        BuildServiceRegistry sharedServices = rootProject.getGradle().getSharedServices();
        extension.getClusters().forEach(spec -> {
            Provider<DockerClusterService> provider = sharedServices.registerIfAbsent(
                    serviceName(spec.getName()),
                    DockerClusterService.class,
                    registration -> {
                        registration.getParameters().getName().set(spec.getName());
                        registration.getParameters().getDistribution().set(spec.getDistribution());
                        registration.getParameters().getImage().set(spec.getImage());
                        registration.getParameters().getClusterName().set(spec.getClusterName());
                        registration.getParameters().getStartupTimeoutMillis().set(spec.getStartupTimeoutMillis());
                        registration.getParameters().getEnvVars().set(spec.getEnvVars());
                    }
            );
            services.put(spec.getName(), provider);
        });
        return services;
    }

    private String serviceName(String clusterName) {
        return "elasticDockerCluster_" + clusterName.replaceAll("[^A-Za-z0-9_]+", "_");
    }
}
