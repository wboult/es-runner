---
title: Gradle shared cluster plugin design
description: Proposed Gradle integration for sharing Elasticsearch test clusters across projects and suites.
---

# Gradle shared cluster plugin design

Status: initial implementation now exists in this repo. This page explains the
reasoning behind the current shape and the planned extensions.

The goal is a Gradle plugin that starts a small number of Elasticsearch
clusters once per build and shares them across multiple Gradle projects and
test suites.

## Desired user experience

Root build:

```kotlin
plugins {
    id("com.elastic.runner.shared-test-clusters")
}

elasticTestClusters {
    clusters {
        register("integration") {
            version.set("9.2.4")
            download.set(true)
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

Subprojects just define their JVM suites:

```kotlin
testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
        }
    }
}
```

## Key ideas

### Shared clusters are build-scoped

The plugin should use a root-scoped Gradle shared build service so one cluster
definition maps to one live Elasticsearch process for the whole build.

### Suites are namespace-scoped

Each suite gets a unique namespace such as:

```text
er_<build-id>_<project>_<suite>
```

Tests can then safely create indices, aliases, templates, and pipelines
without colliding with other suites running in parallel.

### Tests get simple injected properties

The plugin should inject:

- `elastic.runner.baseUri`
- `elastic.runner.namespace`
- `elastic.runner.resourcePrefix`

That enables an easy companion helper API:

```java
ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
ElasticClient client = env.client();
String orders = env.index("orders");
```

## Why suite-level namespaces

This matches the common integration-test model:

- tests within one suite may share state
- suites should not affect each other

Suite-level isolation is much cheaper than per-test isolation and still avoids
parallel overlap between projects or tasks.

## Lifecycle

The intended lifecycle is:

1. register cluster definitions in the root build
2. bind `JvmTestSuite` or `Test` tasks to those definitions
3. lazily start a cluster the first time a consuming task runs
4. inject connection + namespace properties into each test task
5. close all clusters automatically when the build ends

## Recommended deliverables

Split the feature into:

- a Gradle plugin artifact for build wiring
- a small test-support artifact for namespace and client helpers

That keeps test code clean without forcing Gradle plugin classes onto the test
runtime classpath.

## Related

- [API reference](../reference/api/)
