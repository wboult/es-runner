# ES Runner

[![CI](https://github.com/wboult/es-runner/actions/workflows/ci-main.yml/badge.svg)](https://github.com/wboult/es-runner/actions/workflows/ci-main.yml)
[![Docs](https://github.com/wboult/es-runner/actions/workflows/docs.yml/badge.svg)](https://github.com/wboult/es-runner/actions/workflows/docs.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/license/mit/)

ES Runner launches a real Elasticsearch or OpenSearch ZIP distribution in an
isolated OS process and gives tests or offline tooling a small Java API for
starting, stopping, and talking to the cluster.

The mainline path is process-backed and Docker-free. If your Gradle build
already standardizes on Docker, the repo also includes a Docker/Testcontainers
shared-cluster plugin.

Documentation: <https://wboult.github.io/es-runner/>
Contributor setup: [CONTRIBUTING.md](CONTRIBUTING.md)

## Table of contents

- [What ES Runner is for](#what-es-runner-is-for)
- [Start with the right path](#start-with-the-right-path)
- [Release status and coordinates](#release-status-and-coordinates)
- [Quick start](#quick-start)
- [OpenSearch](#opensearch)
- [Shared Gradle clusters](#shared-gradle-clusters)
- [Samples and docs](#samples-and-docs)
- [Module overview](#module-overview)
- [Build and test](#build-and-test)

## What ES Runner is for

Use ES Runner when you want a real search node locally but Docker is the wrong
default:

- bare-metal or locked-down CI agents where Docker is unavailable or unwanted
- local automation that should stay close to the official ZIP distribution
- multi-project Gradle builds that should share one build-scoped node instead
  of paying repeated startup cost per suite
- process-level realism without maintaining your own download, extract, config,
  health-check, and shutdown scripts

Use something else when:

- Docker/Testcontainers is already the standard abstraction and you want
  container-level isolation first
- you need per-test container isolation rather than one process or one
  build-scoped shared cluster
- you need production-topology simulation beyond what a small local cluster can
  reasonably model

## Start with the right path

- **One JVM test suite or offline tool**
  Start with the [Getting started tutorial](https://wboult.github.io/es-runner/tutorials/getting-started/)
  or the [First single-node server walkthrough](https://wboult.github.io/es-runner/tutorials/first-server/).
- **A Gradle build with multiple suites or subprojects**
  Start with the [shared-cluster backend guide](https://wboult.github.io/es-runner/how-to/choose-gradle-shared-cluster-backend/)
  and then pick process-backed or Docker-backed shared clusters deliberately.
- **OpenSearch instead of Elasticsearch**
  Use the same process-backed API and follow the
  [Run OpenSearch guide](https://wboult.github.io/es-runner/how-to/run-opensearch/).

## Release status and coordinates

The release pipeline, signing, Maven metadata, and Gradle plugin publication
flow are in the repo now. Until the first live release is cut, the normal ways
to consume ES Runner are:

- a composite build for the Gradle plugins
- a local Maven repo populated with the release dry-run tasks

Publishable coordinates and plugin ids:

| Surface | Coordinates / id |
|---|---|
| Core library | `io.github.wboult:es-runner` |
| Official Java client adapter | `io.github.wboult:es-runner-java-client` |
| Gradle shared-cluster core | `io.github.wboult:es-runner-gradle-core` |
| Gradle shared-cluster test helper | `io.github.wboult:es-runner-gradle-test-support` |
| Process-backed Gradle plugin | `io.github.wboult.es-runner.shared-test-clusters` |
| Docker-backed Gradle plugin | `io.github.wboult.es-runner.docker-shared-test-clusters` |

Before the first live release, use the checked-in samples and
[docs/releasing.md](docs/releasing.md). For the artifact/module map, use
[docs/modules.md](docs/modules.md).

## Quick start

The simplest process-backed Elasticsearch path:

```java
ElasticRunner.withServer(builder -> builder
    .version("9.3.1")
    .download(true),
    server -> System.out.println(server.clusterHealth()));
```

If you already have a local ZIP:

```java
Path distroZip = Paths.get("elasticsearch-9.3.1-windows-x86_64.zip");

ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .distroZip(distroZip)
    .workDir(Paths.get(".es"))
    .clusterName("local-es")
    .setting("discovery.type", "single-node")
    .setting("xpack.security.enabled", "false"));

try (ElasticServer server = ElasticRunner.start(config)) {
    System.out.println(server.baseUri());
    System.out.println(server.clusterHealth());
}
```

For a guided setup, use the
[Getting started tutorial](https://wboult.github.io/es-runner/tutorials/getting-started/).

## OpenSearch

OpenSearch is a first-class process-backed path, not just an experiment.

```java
ElasticRunner.withServer(builder -> builder
    .family(DistroFamily.OPENSEARCH)
    .version("3.5.0")
    .download(true),
    server -> System.out.println(server.clusterHealth()));
```

See [docs/run-opensearch.md](docs/run-opensearch.md) or the
[docs-site guide](https://wboult.github.io/es-runner/how-to/run-opensearch/).

## Shared Gradle clusters

ES Runner includes two shared-cluster Gradle plugin backends:

- **Process-backed**
  Uses the real ZIP distro plus a local OS process. This is the primary path,
  and it also supports shared OpenSearch clusters.
- **Docker-backed**
  Uses Testcontainers and a shared container per build when Docker is already
  the standard runtime.

Both backends:

- start lazily only when a bound test task actually forks
- inject connection details through system properties
- use `ElasticGradleTestEnv` in tests
- provide namespace helpers such as `env.index(...)`,
  `env.indexPattern(...)`, `env.template(...)`, and `env.alias(...)`

Pick the backend first:

- [Choose a shared-cluster backend](https://wboult.github.io/es-runner/how-to/choose-gradle-shared-cluster-backend/)
- [Process-backed shared clusters](https://wboult.github.io/es-runner/how-to/gradle-shared-test-clusters/)
- [Docker-backed shared clusters](https://wboult.github.io/es-runner/how-to/docker-shared-test-clusters/)

## Samples and docs

If the README is not enough, these are the best next stops:

- **Single-node tutorials**
  - [Getting started](https://wboult.github.io/es-runner/tutorials/getting-started/)
  - [First single-node server](https://wboult.github.io/es-runner/tutorials/first-server/)
- **Shared Gradle clusters**
  - [Backend choice guide](https://wboult.github.io/es-runner/how-to/choose-gradle-shared-cluster-backend/)
  - [Process-backed shared clusters](https://wboult.github.io/es-runner/how-to/gradle-shared-test-clusters/)
  - [Docker-backed shared clusters](https://wboult.github.io/es-runner/how-to/docker-shared-test-clusters/)
  - [Best practices](https://wboult.github.io/es-runner/how-to/gradle-shared-test-cluster-best-practices/)
- **Operational details**
  - [Troubleshooting](https://wboult.github.io/es-runner/how-to/troubleshooting/)
  - [Performance guidance](https://wboult.github.io/es-runner/explanation/performance/)
  - [Compatibility and support](https://wboult.github.io/es-runner/reference/compatibility/)

Canonical public samples:

- process-backed:
  [`samples/gradle-shared-cluster-multiproject-sample`](samples/gradle-shared-cluster-multiproject-sample)
- Docker-backed Elasticsearch:
  [`samples/docker-shared-cluster-multiproject-sample`](samples/docker-shared-cluster-multiproject-sample)
- Docker-backed OpenSearch:
  [`samples/docker-opensearch-shared-cluster-multiproject-sample`](samples/docker-opensearch-shared-cluster-multiproject-sample)

Experimental embedded runners still exist, but they are intentionally
secondary. See [docs/try-embedded-runners.md](docs/try-embedded-runners.md) and
[docs/embedded-jvm-server.md](docs/embedded-jvm-server.md).

## Module overview

If you are choosing artifacts rather than following one tutorial, use:

- [docs/modules.md](docs/modules.md)
- [docs/try-embedded-runners.md](docs/try-embedded-runners.md)

## Build and test

Examples:

```powershell
$env:ES_DISTRO_ZIP = "C:\path\to\elasticsearch-9.3.1.zip"
.\gradlew.bat test
.\gradlew.bat scala3Test
```

```bash
export ES_DISTRO_ZIP=/path/to/elasticsearch-9.3.1.zip
./gradlew test
./gradlew scala3Test
```

If the system JDK is not compatible, the wrapper uses the pinned JDK 17 in
`.jdks/` via `gradle.properties`.

For contributor workflows, release steps, and focused test slices, use
[CONTRIBUTING.md](CONTRIBUTING.md). For the full local test environment-variable
list, use
[site/src/content/docs/reference/environment.md](site/src/content/docs/reference/environment.md).
