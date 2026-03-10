# Gradle shared test cluster best practices

ES Runner's shared Gradle cluster plugin is designed to let one build-scoped
Elasticsearch node serve multiple projects and suites without turning the build
into a flaky shared-state mess.

These are the practices that keep that model predictable.

## 1. Keep the default `SUITE` namespace mode

Use `NamespaceMode.SUITE` unless you have a very specific reason not to.

Why:

- tests in the same suite can still share state intentionally
- parallel suites do not collide on indices, aliases, templates, or data streams
- leftover resources from one suite do not poison another suite later in the build

Only switch to `PROJECT` when all suites in that project are deliberately
sharing the same logical resource set.

## 2. Use logical names in test code

Do not hard-code physical index names like `orders-it-123`.

Instead, derive resource names from the injected namespace:

```java
ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();

String orders = env.index("orders");
String ordersTemplate = env.template("orders-template");
String ordersAlias = env.alias("orders");
```

That keeps test code readable while still making the actual physical names
unique per suite.

## 3. Share state only inside one suite

The intended unit of shared state is one suite task, not one project and not
one whole build.

That means:

- `integrationTest` may share setup data across multiple test classes
- `smokeTest` should not assume it can see `integrationTest` state
- a later Gradle invocation should not depend on leftovers from the previous one

If two suites genuinely need incompatible shared state, give them separate
cluster definitions instead of forcing one node to serve both.

## 4. Keep cluster-wide settings stable for the whole build

Shared clusters work best when node-level behavior is fixed for the duration of
the build.

Good candidates for a shared cluster:

- common mappings and templates
- normal index CRUD and search
- stable cluster settings

Bad candidates for one shared cluster:

- suites that need different security settings
- suites that install different plugins
- suites that mutate cluster-wide behavior in incompatible ways

When suites need materially different node behavior, define more than one
cluster in `elasticTestClusters { clusters { ... } }`.

## 5. Bind only real integration-style suites

The plugin starts lazily, but you should still bind only the suites that
actually need Elasticsearch.

Good:

- `integrationTest`
- `smokeTest`
- `acceptanceTest`

Usually not worth binding:

- `test`
- `classes`
- lint, docs, packaging, or publish tasks

That keeps the build graph clear and prevents accidental coupling between unit
tests and infrastructure.

## 6. Clean up within the namespace, not outside it

If a suite reuses logical names heavily, reset its own data between tests or at
the end of the suite.

Prefer:

- deleting `env.index("orders")`
- recreating suite-owned templates or pipelines by logical name

Avoid:

- wildcard deletes across the whole cluster
- deleting resources that do not belong to the current namespace

One suite should never need to know another suite's physical names.

## 7. Use stable logical resource names

Inside a suite, prefer stable logical names such as:

- `orders`
- `customers`
- `orders-template`
- `orders-read`

Do not build extra randomness into those names unless one test method truly
needs isolated data from another test method. The suite namespace already gives
you cross-suite isolation.

## 8. Separate data isolation from cluster isolation

Most of the time you do not need a separate node per suite. You only need:

- separate resource names
- clear rules about which suites may share state

Use more clusters only when node-level differences matter. Use namespace
isolation when the node can be shared safely.

## 9. Keep distro and work directories explicit

For reproducible local and CI behavior, set durable locations for:

- `distrosDir`
- `workDir`

Example:

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

That makes reuse predictable and makes failures easier to inspect.

## 10. Let lazy startup do its job

Applying the plugin does not mean Elasticsearch always starts.

The shared cluster only boots when a bound test task actually forks a JVM.
Tasks like `classes`, `jar`, or unrelated test tasks stay cold.

Treat that as a feature:

- wire the plugin at the root once
- bind only the suites that need it
- do not add ad-hoc task hooks that force eager startup

## Suggested default

For most multi-project builds, this is the right starting point:

- one shared ES cluster per build
- `NamespaceMode.SUITE`
- only `integrationTest` and `smokeTest` bound
- logical names created through `ElasticGradleTestEnv`
- cluster-wide settings kept stable for the whole build

## Related

- [docs/gradle-shared-test-clusters.md](docs/gradle-shared-test-clusters.md)
- [docs/gradle-shared-cluster-plugin-design.md](docs/gradle-shared-cluster-plugin-design.md)
