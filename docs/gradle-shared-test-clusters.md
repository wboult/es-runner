# Gradle shared test clusters

Elastic Runner ships with a Gradle plugin that starts a shared Elasticsearch
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

- Gradle plugin id: `com.elastic.runner.shared-test-clusters`
- test helper artifact: `com.elastic:elastic-runner-gradle-test-support`

The plugin wires test tasks. The helper artifact keeps test code clean by
turning injected system properties into an `ElasticClient` plus namespace-aware
resource names.

These identifiers are current repo-internal identifiers, not yet a committed
public publishing namespace. Before the first public release, they should move
to an owner-controlled namespace for Maven Central and the Gradle Plugin Portal.

## Recommended build layout

Apply the plugin once in the root build:

```groovy
plugins {
    id 'com.elastic.runner.shared-test-clusters'
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
        }
    }

    suites {
        matchingName("integrationTest") {
            useCluster("integration")
            namespaceMode.set(com.elastic.runner.gradle.NamespaceMode.SUITE)
        }
    }
}
```

Then define the suites normally in subprojects:

```groovy
subprojects {
    apply plugin: 'java'

    dependencies {
        testImplementation "com.elastic:elastic-runner-gradle-test-support:${project.version}"
    }

    testing {
        suites {
            register("integrationTest", org.gradle.api.plugins.jvm.JvmTestSuite) {
                useJUnitJupiter()
            }
        }
    }
}
```

If you are wiring this inside the Elastic Runner repo itself, use
`testImplementation(project(":elastic-runner-gradle-test-support"))` instead of
published coordinates.

## Test-side usage

```java
import com.elastic.runner.ElasticClient;
import com.elastic.runner.gradle.testsupport.ElasticGradleTestEnv;

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

Example using a private GCS mirror:

```groovy
elasticTestClusters {
    clusters {
        register("integration") {
            version.set("9.3.1")
            download.set(true)
            downloadBaseUrl.set("gs://elastic-mirror/elasticsearch/")
        }
    }
}
```

Mirror auth follows the same rules as the main library. See
`docs/cloud-storage-mirrors.md`.

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

