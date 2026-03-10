---
title: Use shared Gradle test clusters
description: Reuse one Elasticsearch process across Gradle projects and suites with suite-level namespaces.
---

ES Runner includes a Gradle plugin for build-scoped shared Elasticsearch
clusters.

Use it when you want:

- one Elasticsearch process per build instead of per test JVM
- multiple Gradle projects to share the same cluster
- suite-level namespaces so parallel suites do not collide

## Use a composite build today

The plugin and helper module work now, but Plugin Portal and Maven Central
publication are still pending. Until then, include ES Runner as a composite
build:

```groovy
pluginManagement {
    includeBuild("../es-runner")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

A checked-in consumer sample also lives in
`samples/gradle-shared-cluster-multiproject/`. It uses normal plugin/helper
coordinates and can be pointed at a local Maven repo with
`-PesRunnerRepositoryUrl=...` before publication, then at published artifacts
later with only `-PesRunnerVersion=...`.

## Apply the plugin in the root build

```groovy
plugins {
    id 'io.github.wboult.es-runner.shared-test-clusters'
}

elasticTestClusters {
    clusters {
        register("integration") {
            version.set("9.3.1")
            download.set(true)
            clusterName.set("shared-it")
            quiet.set(true)
            startupTimeoutMillis.set(180_000L)
        }
    }

    suites {
        matchingName("integrationTest") {
            useCluster("integration")
            namespaceMode.set(io.github.wboult.esrunner.gradle.NamespaceMode.SUITE)
        }
        matchingName("smokeTest") {
            useCluster("integration")
            namespaceMode.set(io.github.wboult.esrunner.gradle.NamespaceMode.SUITE)
        }
    }
}
```

## Define the suites in subprojects

```groovy
subprojects {
    apply plugin: 'java'

    testing {
        suites {
            withType(org.gradle.api.plugins.jvm.JvmTestSuite).configureEach {
                useJUnitJupiter()
                dependencies {
                    implementation("io.github.wboult:es-runner-gradle-test-support:${project.version}")
                }
            }

            register("integrationTest", org.gradle.api.plugins.jvm.JvmTestSuite)
            register("smokeTest", org.gradle.api.plugins.jvm.JvmTestSuite)
        }
    }
}
```

If you are using this from the ES Runner source tree itself, depend on
`project(":es-runner-gradle-test-support")` instead of published
coordinates.

## Use the injected test environment

```java
import io.github.wboult.esrunner.ElasticClient;
import io.github.wboult.esrunner.gradle.testsupport.ElasticGradleTestEnv;

ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
ElasticClient client = env.client();

String orders = env.index("orders");
client.createIndex(orders);
client.indexDocument(orders, "1", "{\"status\":\"new\"}");
client.refresh(orders);
```

The helper prefixes logical resource names with the suite namespace, and the
plugin only starts the shared cluster when a bound test task actually forks.
It waits for yellow cluster health before injecting `elastic.runner.baseUri`
into that suite task. Applying the plugin alone does not start Elasticsearch,
and tasks like `classes`, `jar`, or unrelated test tasks stay cold.

## What gets injected

Bound test tasks receive these system properties:

- `elastic.runner.baseUri`
- `elastic.runner.httpPort`
- `elastic.runner.clusterName`
- `elastic.runner.buildId`
- `elastic.runner.suiteId`
- `elastic.runner.namespace`
- `elastic.runner.resourcePrefix`

## Namespace modes

- `SUITE`: one namespace per suite task. Recommended.
- `PROJECT`: one namespace per project.

With `SUITE`, tests within the same suite can share state, but parallel suites
stay isolated from each other.

Example:

- `:app:integrationTest` using `env.index("orders")`
  - `erabc123_app_integrationtest-orders`
- `:search:smokeTest` using `env.index("orders")`
  - `erabc123_search_smoketest-orders`

So one shared ES9 cluster can safely back multiple projects and suite tasks.

## Configure mirrors and private distros

Cluster definitions accept the same distro settings as the core library:

- `distroZip`
- `version`
- `download`
- `downloadBaseUrl`
- `distrosDir`

Shared test-cluster defaults also disable Elasticsearch disk-threshold
allocation checks, which avoids single-node local builds getting stuck red on
machines with a low free-disk percentage.

That means you can point shared Gradle clusters at:

- official Elastic downloads
- `https://` mirrors (including pre-signed S3 / GCS / Azure SAS URLs)
- `file://` mirrors

See [Cloud storage mirrors](../cloud-storage-mirrors/) for per-provider
guidance including how to generate pre-signed / SAS HTTPS URLs for private
S3, GCS, and Azure Blob buckets.

## Related

- `samples/gradle-shared-cluster-multiproject/`
- [Shared cluster best practices](../gradle-shared-test-cluster-best-practices/)
- [Gradle shared cluster plugin design](../../explanation/gradle-shared-cluster-plugin-design/)
- [Configuration reference](../../reference/configuration/)

