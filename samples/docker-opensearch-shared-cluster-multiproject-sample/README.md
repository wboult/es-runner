# Multi-project Docker OpenSearch shared-cluster sample

This is the canonical public sample for the ES Runner Docker-backed
shared-cluster Gradle plugin. It consumes the plugin and helper artifact
through normal published coordinates and shows how one build-scoped
OpenSearch 3 container can support multiple projects and suites without
data collisions.

It proves:

- one shared OpenSearch 3 container per build
- multiple Gradle projects using that same cluster
- seeded test data loaded through a small internal `sample-support` module
- suite-level namespace isolation
- a negative-path suite that proves raw non-namespaced access fails
- concrete template ids plus wildcard index patterns through `env.template(...)`
  and `env.indexPattern(...)`

## Layout

- `sample-support`
  - common fixture loader and metadata writer used by test suites
- `app`
  - integration suite that seeds orders into its own namespace
  - smoke suite that proves it starts with a fresh namespace
- `search`
  - integration suite that applies a template, seeds data, adds an alias, and queries it
  - smoke suite that exercises a failure path against the raw logical index name

All four suites share one OpenSearch container, but they use different
physical resource names because each suite gets its own namespace.

## What to copy first

If you are wiring the plugin into a real consumer build, start with:

- `settings.gradle`
  - plugin version + repository setup
- `build.gradle`
  - root plugin application
  - one shared cluster definition
  - suite binding for `integrationTest` and `smokeTest`
- `sample-support`
  - a minimal pattern for test-side fixture helpers
- `search/src/integrationTest/.../SearchIntegrationTest.java`
  - the clearest example of `env.index(...)`, `env.template(...)`,
    `env.indexPattern(...)`, and `env.alias(...)` used together

## How to run after publication

This sample requires a working Docker daemon.

When the artifacts are published, run from this directory with:

```bash
./gradlew \
  :app:check \
  :search:check \
  -PesRunnerVersion=0.1.0 \
  -PosVersion=3.5.0
```

## How to run before publication

Point the build at a Maven repository that contains:

- `io.github.wboult:es-runner`
- `io.github.wboult:es-runner-gradle-test-support`
- `io.github.wboult:es-runner-gradle-core`
- plugin marker for `io.github.wboult.es-runner.docker-shared-test-clusters`

Then run:

```bash
./gradlew \
  :app:check \
  :search:check \
  -PesRunnerVersion=0.1.0 \
  -PosVersion=3.5.0 \
  -PesRunnerRepositoryUrl=file:///path/to/repo
```

The suites write metadata into `build/es-runner/*.properties` inside each
project so you can inspect the injected namespace, suite id, base URI, and the
resources each suite touched.
