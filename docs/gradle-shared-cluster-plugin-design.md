# Gradle Shared Cluster Plugin Design

This document proposes a Gradle integration for ES Runner that starts a
small number of shared Elasticsearch servers once per build and makes them
available to multiple Gradle projects and test suites without repeated
per-JVM/per-suite startup.

Status: initial implementation now exists in this repo. This document records
the design rationale and the intended evolution beyond the current feature set.

## Goals

- Start Elasticsearch once per cluster definition for the whole Gradle build.
- Share that cluster across multiple Gradle projects and test suites.
- Give each suite its own namespace so leftover state does not affect other
  suites.
- Keep the user-facing Gradle DSL small and obvious.
- Work with standard Gradle JVM test suites and plain `Test` tasks.
- Shut clusters down automatically at the end of the build, even on failure.

## Non-goals

- Sharing a cluster across separate Gradle invocations.
- Per-test-method namespace management.
- Supporting every Gradle test ecosystem on day one.
- Hiding Elasticsearch entirely from tests. Tests still need to choose their
  own logical index/data stream names inside the provided namespace.

## User experience

### Root build

```kotlin
plugins {
    id("io.github.wboult.es-runner.shared-test-clusters")
}

elasticTestClusters {
    clusters {
        register("integration") {
            version.set("9.3.1")
            download.convention(true)
            distrosDir.set(layout.rootDirectory.dir(".gradle/elasticsearch/distros"))
            workDir.set(layout.rootDirectory.dir(".gradle/elasticsearch/work"))
            clusterName.set("gradle-it")
            settings.put("xpack.security.enabled", "false")
            settings.put("discovery.type", "single-node")
        }
    }

    suites {
        matchingName("integrationTest") {
            useCluster("integration")
            namespaceMode.set(NamespaceMode.SUITE)
        }
    }
}
```

### Subprojects

```kotlin
plugins {
    java
}

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
        }
    }
}
```

That is the intended default path:

- the root project defines cluster specs once
- subprojects just declare suites
- matching suites automatically receive connection and namespace properties

## Why a root-scoped plugin

The plugin should be applied once at the root because the shared servers are a
build-wide concern:

- one build service should own the live cluster processes
- one root-level cache/work directory should hold extracted distros and logs
- all subprojects should see the same cluster definitions

Gradle shared build services are scoped to the build, not the project, which
makes them the right primitive for this job.

## Core model

### 1) Cluster definition

A cluster definition describes a reusable server pool entry:

- version or explicit distro source
- download and mirror configuration
- work directory roots
- ports / heap / settings / plugins
- node count, if multi-node support is added later

Example DSL:

```kotlin
elasticTestClusters {
    clusters {
        register("search") {
            version.set("9.3.1")
            download.set(true)
            downloadBaseUrl.set("https://mirror.example.com/elasticsearch/")
            heap.set("512m")
        }
    }
}
```

### 2) Suite binding

Each Gradle suite or `Test` task binds to one cluster definition.

Example:

```kotlin
elasticTestClusters {
    suites {
        matchingName("integrationTest") {
            useCluster("search")
        }
    }
}
```

### 3) Suite namespace

Every suite gets a namespace token that is stable for the duration of that
suite task and unique across the build.

Default formula:

```text
er_<build-id>_<normalized-project-path>_<suite-name>
```

Example:

```text
er_20260307T192501_app_search_integrationTest
```

Tests use that namespace as a prefix when creating indices, templates, aliases,
pipelines, and other resources.

## Test-side contract

The plugin should inject a minimal set of system properties into every bound
test task:

- `elastic.runner.baseUri`
- `elastic.runner.httpPort`
- `elastic.runner.clusterName`
- `elastic.runner.namespace`
- `elastic.runner.resourcePrefix`
- `elastic.runner.buildId`
- `elastic.runner.suiteId`

Tests can then do either of the following:

1. Construct clients directly from `elastic.runner.baseUri`
2. Use a tiny helper API provided by a companion test-support artifact

Proposed helper API:

```java
ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
ElasticClient client = env.client();
String ordersIndex = env.index("orders");
```

The helper would:

- read the injected system properties
- create an `ElasticClient`
- prefix logical resource names consistently

## Namespace behavior

The default isolation level should be suite-wide, not per test class or method.
That matches the intended usage:

- tests inside one suite may share state
- suites must not collide with each other

Why suite-level namespacing is the right default:

- avoids expensive per-test setup
- still supports test order independence inside a suite if teams want it
- isolates parallel Gradle suites across subprojects
- avoids failures when a suite leaves indices or templates behind

Optional future modes:

- `PROJECT`: one namespace shared by all suites in a project
- `TASK`: one namespace per task path
- `CUSTOM`: explicit prefix/provider

## Lifecycle design

### 1) Register a shared build service

The plugin registers a root build service such as:

```text
ElasticClusterPoolService
```

Responsibilities:

- lazily start cluster instances on first use
- cache live instances by cluster definition
- expose connection metadata to consuming test tasks
- stop all clusters in `close()`

### 2) Make test tasks use the build service

For every bound `Test` task:

- mark the task as using the build service
- resolve the assigned cluster handle lazily
- inject system properties just before execution

This avoids ad hoc start/stop tasks and lets Gradle own lifecycle ordering.

### 3) Keep one cluster per definition per build

If five projects bind `integrationTest` to the same `integration` cluster
definition, the plugin should still start only one Elasticsearch process for
that definition during the build.

### 4) Stop on build completion

The build service closes on build completion, including failed builds, which is
exactly what we want for ephemeral integration clusters.

## Parallelism model

Shared clusters plus suite namespaces allow concurrent execution without index
name collisions.

What remains shared:

- the JVM process running Elasticsearch
- node-level caches
- cluster-level settings
- disk and CPU limits

What becomes isolated:

- indices
- aliases
- index templates
- ingest pipelines
- component templates
- data streams

Practical rule:

- cluster settings should be stable and build-wide
- test data and resources should always be namespaced per suite

## Multi-project behavior

The plugin should support:

- many subprojects
- many suites
- one or more shared cluster definitions

Recommended default matching:

- all `integrationTest` suites bind to `integration`
- all `functionalTest` suites bind to `functional`

Example:

```kotlin
elasticTestClusters {
    suites {
        matchingName("integrationTest") { useCluster("integration") }
        matchingName("functionalTest") { useCluster("functional") }
    }
}
```

## Failure handling

The plugin should fail early and clearly when:

- no cluster definition matches a requested suite
- the distro cannot be resolved
- a shared cluster fails to start
- injected system properties cannot be computed

Desired failure semantics:

- if the cluster cannot start, all dependent suites fail before test execution
- if one suite fails, the cluster remains alive for other suites unless the
  process itself is unhealthy
- the build service always attempts shutdown at the end of the build

## Recommended plugin split

Use two deliverables:

### 1) Gradle plugin artifact

Suggested coordinates / id:

- artifact: `io.github.wboult:es-runner-gradle-plugin`
- plugin id: `io.github.wboult.es-runner.shared-test-clusters`

Responsibilities:

- Gradle DSL
- build service registration
- suite/task wiring
- system property injection

### 2) Test-support artifact

Suggested artifact:

- `io.github.wboult:es-runner-gradle-test-support`

Responsibilities:

- `ElasticGradleTestEnv`
- namespace/resource name helpers
- convenience client construction

This split keeps test code clean without forcing the Gradle plugin jar onto the
test runtime classpath.

## Recommended implementation phases

### Phase 1: happy path

- root plugin with one shared single-node cluster
- `JvmTestSuite` integration
- suite namespace injection
- helper API for `baseUri` + `namespace`

### Phase 2: broader Gradle compatibility

- support plain `Test` tasks directly
- support multiple cluster definitions
- support custom namespace providers

### Phase 3: operational polish

- build scan / logging integration
- optional health checks between suites
- optional namespace cleanup hooks
- optional multi-node cluster definitions

## Example of the intended end state

```kotlin
plugins {
    id("io.github.wboult.es-runner.shared-test-clusters")
}

elasticTestClusters {
    clusters {
        register("integration") {
            version.set("9.3.1")
            download.set(true)
        }
    }

    suites {
        allJvmIntegrationTests {
            useCluster("integration")
            namespaceMode.set(NamespaceMode.SUITE)
        }
    }
}
```

And in tests:

```java
ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
ElasticClient client = env.client();

client.createIndex(env.index("orders"));
client.indexDocument(env.index("orders"), "1", "{\"status\":\"new\"}");
```

This gives:

- one shared ES process per cluster definition
- no per-test-JVM startup churn
- no cross-suite index collisions
- an obvious path for multi-project builds

