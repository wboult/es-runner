---
title: Use shared Gradle test clusters
description: Reuse one Elasticsearch process across Gradle projects and suites with suite-level namespaces.
sidebar:
  order: 10
---

ES Runner includes a Gradle plugin for build-scoped shared Elasticsearch
clusters.

Use it when you want:

- one Elasticsearch process per build instead of per test JVM
- multiple Gradle projects to share the same cluster
- suite-level namespaces so parallel suites do not collide

## Use a composite build today

The plugin and helper module work now, and the release workflow now wires
Plugin Portal and Maven Central publication. Until the first live release,
still treat composite-build consumption as the default path:

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

A checked-in canonical sample also lives in
`samples/gradle-shared-cluster-multiproject-sample/`. It uses normal
plugin/helper coordinates and can be pointed at a local Maven repo with
`-PesRunnerRepositoryUrl=...` before publication, then at published artifacts
later with only `-PesRunnerVersion=...`.

That sample is intentionally more realistic than the TestKit fixture:

- it has a small internal `sample-support` subproject
- it bulk-loads fixture orders into namespaced indices
- it includes a negative-path suite that proves raw non-namespaced access fails
- it shows `env.template(...)`, `env.index(...)`, `env.indexPattern(...)`, and
  `env.alias(...)` working together across multiple projects and suites

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
String ordersPattern = env.indexPattern("orders");
String ordersTemplate = env.template("orders-template");
String newOrder = """
        {
          "status": "new"
        }
        """;
client.putIndexTemplate(ordersTemplate, """
        {
          "index_patterns": ["%s"]
        }
        """.formatted(ordersPattern));
client.createIndex(orders);
client.indexDocument(orders, newOrder);
client.refresh(orders);
```

## Resource naming model

Use one naming model consistently:

- concrete resource ids:
  - `env.index("orders")`
  - `env.alias("orders-read")`
  - `env.template("orders-template")`
- wildcard-based matching:
  - `env.indexPattern("orders")`

Concrete helpers produce one physical resource id inside the suite namespace.
Use those ids for CRUD, refresh, alias creation, template ids, and cleanup.

Use `env.indexPattern(...)` only when Elasticsearch expects wildcard matching,
such as template `index_patterns`. Do not pass `orders-*` into `env.index(...)`;
concrete helpers now reject pattern-like input so a bad pattern cannot be
silently sanitized into the wrong physical name.

The helper prefixes logical resource names with the suite namespace, and the
plugin only starts the shared cluster when a bound test task actually forks.
It waits for yellow cluster health before injecting `elastic.runner.baseUri`
into that suite task. Applying the plugin alone does not start Elasticsearch,
and tasks like `classes`, `jar`, or unrelated test tasks stay cold.

Yellow health is only the startup floor. Suites still need to install their
own templates, create indices, and `refresh(...)` before asserting on
search results.

## Lazy startup behavior

Applying the plugin does not start Elasticsearch by itself.

The shared cluster stays cold for commands such as:

- `./gradlew classes`
- `./gradlew jar`
- `./gradlew :someProject:test` when that task is not bound to a shared cluster

The cluster only boots when Gradle actually executes a bound suite task and
forks its test JVM. That keeps ordinary development tasks fast while still
giving integration suites a build-scoped shared node when they really run.

If the shared cluster starts but the suite still returns zero hits or reads the
wrong resources, jump to [Troubleshooting](../troubleshooting/). The usual
causes are missing refresh, using `index(...)` where `indexPattern(...)` was
required, or stale state in a reused `workDir`.

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
- `:app:integrationTest` using `env.indexPattern("orders")`
  - `erabc123_app_integrationtest-orders-*`
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

## Complete minimal example

Below is a self-contained two-subproject build that you can copy and adapt.
It assumes published artifacts; before publication, swap to a composite build
(see "Use a composite build today" above).

**`settings.gradle`:**

```groovy
pluginManagement {
    plugins {
        id 'io.github.wboult.es-runner.shared-test-clusters' version '0.1.0'
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = 'my-project'
include('app', 'search')
```

**`build.gradle` (root):**

```groovy
import org.gradle.api.plugins.jvm.JvmTestSuite

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
    }
}

subprojects {
    apply plugin: 'java'

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }

    testing {
        suites {
            withType(JvmTestSuite).configureEach {
                useJUnitJupiter()
                dependencies {
                    implementation("io.github.wboult:es-runner-gradle-test-support:0.1.0")
                }
            }

            register("integrationTest", JvmTestSuite)
        }
    }

    tasks.named("check") {
        dependsOn(tasks.named("integrationTest"))
    }
}
```

**`app/src/integrationTest/java/example/AppIntegrationTest.java`:**

```java
import io.github.wboult.esrunner.ElasticClient;
import io.github.wboult.esrunner.gradle.testsupport.ElasticGradleTestEnv;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppIntegrationTest {
    @Test
    void indexAndSearch() {
        ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
        ElasticClient client = env.client();

        String orders = env.index("orders");
        String shippedOrder = """
                {
                  "status": "shipped"
                }
                """;
        client.createIndex(orders);
        client.indexDocument(orders, shippedOrder);
        client.refresh(orders);

        long count = client.countValue(orders);
        assertEquals(1, count);
    }
}
```

Run it with:

```bash
./gradlew check
```

For a more realistic multi-project example with fixture loading and a
negative-path suite, see
[`samples/gradle-shared-cluster-multiproject-sample/`](https://github.com/wboult/es-runner/tree/main/samples/gradle-shared-cluster-multiproject-sample).

## Related

- [`samples/gradle-shared-cluster-multiproject-sample/`](https://github.com/wboult/es-runner/tree/main/samples/gradle-shared-cluster-multiproject-sample)
- [Shared cluster best practices](../gradle-shared-test-cluster-best-practices/)
- [Gradle shared cluster plugin design](../../explanation/gradle-shared-cluster-plugin-design/)
- [Troubleshooting](../troubleshooting/)
- [Configuration reference](../../reference/configuration/)

