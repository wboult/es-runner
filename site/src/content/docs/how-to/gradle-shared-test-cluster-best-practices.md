---
title: Shared cluster best practices
description: Keep shared Gradle Elasticsearch clusters predictable with good namespacing, cleanup, and suite boundaries.
sidebar:
  order: 11
---

Shared Gradle clusters work well when you treat one suite as the unit of shared
state and one build as the unit of cluster lifetime.

These guidelines keep that model fast and predictable.

## Keep `SUITE` as the default namespace mode

Use `NamespaceMode.SUITE` unless suites in the same project must intentionally
share physical resource names.

Why:

- tests inside one suite can still share setup state
- parallel suites do not collide on indices, aliases, templates, or data streams
- leftover resources from one suite do not affect another suite later in the build

## Use logical names in tests

Generate physical names from the injected namespace instead of hard-coding them:

```java
ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();

String orders = env.index("orders");
String ordersPattern = env.indexPattern("orders");
String ordersAlias = env.alias("orders");
String ordersTemplate = env.template("orders-template");
```

That keeps test code readable and automatically scopes resources per suite.

Use `env.indexPattern(...)` only for wildcard-based patterns such as template
`index_patterns`. Do not pass `orders-*` into `env.index(...)`; that is a
concrete-name helper and now fails fast on pattern-like input.

## Share state only inside one suite

The intended shared-state boundary is one suite task.

- `integrationTest` may share setup data across test classes
- `smokeTest` should not depend on `integrationTest` leftovers
- a later Gradle invocation should not depend on the previous build's data

If suites need incompatible state models, split them across different cluster
definitions instead of overloading one shared node.

## Keep cluster-wide behavior stable

Shared clusters are a good fit when suites can agree on:

- normal mappings and templates
- standard CRUD/search behavior
- stable cluster settings

Use separate clusters when suites need incompatible node-level behavior such as
different security settings or plugin sets.

## Bind only suites that really need Elasticsearch

The plugin starts lazily. It only boots Elasticsearch when a bound test task
actually forks a JVM.

That means tasks like `classes`, `jar`, and unrelated tests stay cold even when
the plugin is applied at the root.

Still, bind only real integration-style suites:

- `integrationTest`
- `smokeTest`
- `acceptanceTest`

Do not wire ordinary unit-test tasks to shared infrastructure by default.

## Clean up inside your namespace

If a suite reuses the same logical names heavily, reset only its own resources.

Prefer:

- deleting `env.index("orders")`
- recreating suite-owned templates or pipelines by logical name

Avoid:

- cluster-wide wildcard deletes
- deleting resources outside the current namespace

One suite should not need to know another suite's physical names.

## Keep logical names stable

Use stable logical names like:

- `orders`
- `customers`
- `orders-template`

Do not add extra randomness unless one test method truly needs fresh data that
must not be shared with other tests in the same suite. The suite namespace
already handles cross-suite isolation.

## Make distro and work directories explicit

Use durable locations for `distrosDir` and `workDir` so local and CI behavior
is repeatable and failures are easy to inspect:

```groovy
elasticTestClusters {
    clusters {
        register("integration") {
            version.set("9.3.1")
            download.set(true)
            distrosDir.set(layout.rootDirectory.dir(".gradle/elasticsearch/distros").asFile.absolutePath)
            workDir.set(layout.rootProject.layout.buildDirectory.dir("elastic-test-clusters/integration").get().asFile.absolutePath)
        }
    }
}
```

## Recommended default

For most multi-project builds:

- one shared ES cluster per build
- `NamespaceMode.SUITE`
- only integration-style suites bound
- logical names created via `ElasticGradleTestEnv`
- cluster-wide behavior kept stable for the whole build

## Related

- [Use shared Gradle test clusters](../gradle-shared-test-clusters/)
- [Gradle shared cluster plugin design](../../explanation/gradle-shared-cluster-plugin-design/)
