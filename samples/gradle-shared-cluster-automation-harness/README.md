# Shared cluster automation harness sample

This sample is meant to look like a small internal automation build rather
than a toy fixture. It consumes the ES Runner shared-cluster plugin and helper
artifact through normal published coordinates and shows how one build-scoped
Elasticsearch 9 node can support multiple projects and suites without data
collisions.

It proves:

- one shared Elasticsearch 9 cluster per build
- multiple Gradle projects using that same cluster
- seeded test data loaded through a small internal support module
- suite-level namespace isolation
- a negative-path suite that proves raw non-namespaced access fails

## Layout

- `automation-support`
  - common fixture loader and metadata writer used by test suites
- `app`
  - integration suite that seeds orders into its own namespace
  - smoke suite that proves it starts with a fresh namespace
- `search`
  - integration suite that applies a template, seeds data, adds an alias, and queries it
  - smoke suite that exercises a failure path against the raw logical index name

All four suites share one Elasticsearch node, but they use different physical
resource names because each suite gets its own namespace.

## How to run after publication

When the artifacts are published, run from this directory with:

```bash
./gradlew \
  :app:check \
  :search:check \
  -PesRunnerVersion=0.1.0
```

## How to run before publication

Point the build at a Maven repository that contains:

- `io.github.wboult:es-runner`
- `io.github.wboult:es-runner-gradle-test-support`
- plugin marker for `io.github.wboult.es-runner.shared-test-clusters`

Then run:

```bash
./gradlew \
  :app:check \
  :search:check \
  -PesRunnerVersion=0.1.0 \
  -PesRunnerRepositoryUrl=file:///path/to/repo
```

Optional local distro override:

```bash
-PesDistroZip=/path/to/elasticsearch-9.3.1.zip
```

The suites write metadata into `build/es-runner/*.properties` inside each
project so you can inspect the injected namespace, suite id, base URI, and the
resources each suite touched.
