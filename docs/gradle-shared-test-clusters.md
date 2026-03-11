# Gradle shared test clusters

ES Runner ships with a Gradle plugin that starts a shared Elasticsearch
server once per cluster definition and reuses it across multiple Gradle
projects and test suites during a single build.

This is intended for production-like integration automation where per-test-JVM
startup cost is too high, but suites still need isolation from each other.

## What it gives you

- one Elasticsearch process per cluster definition per build
- reuse across parallel Gradle projects and suites
- suite-level namespaces so leftover indices, templates, aliases, and
  pipelines do not collide
- automatic shutdown at build completion
- a small helper API for test code

## Plugin pieces

- Gradle plugin id: `io.github.wboult.es-runner.shared-test-clusters`
- test helper artifact: `io.github.wboult:es-runner-gradle-test-support`

The plugin wires test tasks. The helper artifact keeps test code clean by
turning injected system properties into an `ElasticClient` plus namespace-aware
resource names.

## Current availability

The plugin and helper module already work in this repo and in composite builds.
Plugin Portal and Maven Central publication are still pending.

A checked-in consumer sample lives at
`samples/gradle-shared-cluster-multiproject/`. It uses normal plugin/helper
coordinates instead of `includeBuild`, so it is the closest approximation of
how a published consumer build will look.

Until publication, the correct setup is a composite build:

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

If you want to prove the published-artifact flow before release, run the sample
with:

- `-PesRunnerVersion=...`
- `-PesRunnerRepositoryUrl=file:///...` pointing at a Maven repo containing the
  ES Runner core/helper/plugin artifacts
- optionally `-PesDistroZip=/path/to/elasticsearch-9.3.1.zip`

## Recommended build layout

Apply the plugin once in the root build:

```groovy
plugins {
    id 'io.github.wboult.es-runner.shared-test-clusters'
}

elasticTestClusters {
    clusters {
        register("integration") {
            version.set("9.3.1")
            download.set(true)
            distrosDir.set(layout.rootDirectory.dir(".gradle/elasticsearch/distros").asFile.absolutePath)
            workDir.set(layout.rootProject.layout.buildDirectory.dir("elastic-test-clusters/integration").get().asFile.absolutePath)
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

Then define the suites normally in subprojects. A realistic setup can have
multiple projects and multiple suite tasks all sharing one ES9 node:

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

If you are wiring this inside the ES Runner repo itself, use
`testImplementation(project(":es-runner-gradle-test-support"))` instead of
published coordinates.

## Test-side usage

```java
import io.github.wboult.esrunner.ElasticClient;
import io.github.wboult.esrunner.gradle.testsupport.ElasticGradleTestEnv;

ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
ElasticClient client = env.client();

String ordersIndex = env.index("orders");
String ordersTemplate = env.template("orders-template");

client.createIndex(ordersIndex);
client.indexDocument(ordersIndex, "1", "{\"status\":\"new\"}");
client.refresh(ordersIndex);
```

The helper prefixes logical resource names with the injected suite namespace,
so tests can use stable logical names without colliding with other suites.

The plugin starts the cluster lazily only when a bound test task actually
forks a JVM, and waits for yellow health before it injects
`elastic.runner.baseUri` into that suite. That means unrelated Gradle tasks
and non-bound test tasks do not pay startup cost just because the plugin is
applied, while the first real suite still does not need its own startup
polling to survive initial contact with a fresh single-node cluster.

## Lazy startup behavior

Applying the plugin does not start Elasticsearch on its own.

The cluster stays cold for commands such as:

- `./gradlew classes`
- `./gradlew jar`
- `./gradlew :someProject:test` when that task is not bound to a shared cluster

The cluster starts only when Gradle actually executes a bound test task and
forks its test JVM. This keeps normal development tasks fast while preserving
the shared-cluster benefit for real integration suites.

See [docs/gradle-shared-test-cluster-best-practices.md](docs/gradle-shared-test-cluster-best-practices.md)
for concrete guidance on namespacing, cleanup, and suite boundaries.

## Injected system properties

Bound test tasks receive:

- `elastic.runner.baseUri`
- `elastic.runner.httpPort`
- `elastic.runner.clusterName`
- `elastic.runner.buildId`
- `elastic.runner.suiteId`
- `elastic.runner.namespace`
- `elastic.runner.resourcePrefix`

Use `elastic.runner.baseUri` to create raw clients yourself if you do not want
the helper artifact.

## Namespace model

Default namespace mode is `SUITE`.

That means:

- state may be shared by tests inside one suite task
- parallel suites do not share resource names
- leftover resources from one suite do not affect another suite

Current modes:

- `SUITE`: namespace includes build id, project path, and suite task name
- `PROJECT`: namespace includes build id and project path only

`SUITE` is the recommended default for integration automation.

Example:

- build id: `erabc123`
- `:app:integrationTest` using `env.index("orders")`
  - physical index: `erabc123_app_integrationtest-orders`
- `:search:smokeTest` using `env.index("orders")`
  - physical index: `erabc123_search_smoketest-orders`

That lets tests within one suite intentionally share state, while different
projects and suites can reuse the same node without colliding.

## Cluster configuration

Cluster definitions map closely to `ElasticRunnerConfig`. Common properties:

- `distroZip`
- `version`
- `distrosDir`
- `download`
- `downloadBaseUrl`
- `workDir`
- `clusterName`
- `httpPort`
- `portRangeStart`
- `portRangeEnd`
- `heap`
- `startupTimeoutMillis`
- `shutdownTimeoutMillis`
- `settings`
- `plugins`
- `quiet`

Shared test-cluster defaults also add:

- `cluster.routing.allocation.disk.threshold_enabled=false`

That keeps local single-node builds from getting stuck red on machines with a
low free-disk percentage, which is a common source of confusing laptop and CI
failures.

Example using an internal HTTPS mirror:

```groovy
elasticTestClusters {
    clusters {
        register("integration") {
            version.set("9.3.1")
            download.set(true)
            downloadBaseUrl.set("https://internal-mirror.example.com/elasticsearch/")
        }
    }
}
```

Mirror configuration follows the same rules as the core library. See
`docs/cloud-storage-mirrors.md`. For cloud storage (S3, GCS, Azure), use
HTTPS pre-signed or SAS URLs rather than native cloud scheme URIs.

## Operational guidance

Use shared clusters when:

- suites are expensive enough that repeated startup is wasteful
- test data can be isolated by namespacing
- cluster-wide settings are stable for the whole build

Avoid shared clusters when:

- suites need incompatible cluster settings
- suites mutate node-level behavior in ways that affect each other
- you need isolation across separate Gradle invocations

## Current scope

The initial implementation covers:

- root-scoped plugin application
- shared single-node clusters via a Gradle BuildService
- `Test` task wiring, including `JvmTestSuite` tasks
- suite/project namespace modes
- injected system properties
- test helper API

See `docs/gradle-shared-cluster-plugin-design.md` for the design rationale and
future directions.

