---
title: Module overview
description: Published artifacts, plugin ids, and when to use each module.
sidebar:
  order: 2
---

ES Runner is split into a small set of published modules plus a separate
experimental embedded area.

## Published modules

| Module | Coordinates / id | Purpose |
| --- | --- | --- |
| Core runner | `io.github.wboult:es-runner` | Process-backed Elasticsearch/OpenSearch runner and the main Java API. |
| Official Java client adapter | `io.github.wboult:es-runner-java-client` | Adapter helpers for the official Elasticsearch Java API Client. |
| Gradle shared-cluster core | `io.github.wboult:es-runner-gradle-core` | Backend-agnostic namespace, metadata, suite-binding, and JVM-argument wiring shared by the Gradle plugins. |
| Gradle test support | `io.github.wboult:es-runner-gradle-test-support` | Test-side helpers such as `ElasticGradleTestEnv` for shared-cluster suites. |
| Process-backed Gradle plugin | plugin id `io.github.wboult.es-runner.shared-test-clusters` | One shared ZIP/process-backed cluster across Gradle suites and projects. |
| Docker-backed Gradle plugin | plugin id `io.github.wboult.es-runner.docker-shared-test-clusters` | One shared Docker/Testcontainers-backed cluster across Gradle suites and projects. |

## Experimental modules

These are intentionally kept separate from the main published path:

- `experimental/embedded/es-runner-embedded-common`
- `experimental/embedded/es-runner-embedded-8`
- `experimental/embedded/es-runner-embedded-9`
- `experimental/embedded/es-runner-embedded-opensearch-2`
- `experimental/embedded/es-runner-embedded-opensearch-3`

They are useful for experimentation, but the supported product path remains the
process-backed runner and the shared-cluster plugins.

## Which one should I start with?

- Want one local process-backed node in tests or tooling:
  use `io.github.wboult:es-runner`
- Want one shared cluster across Gradle suites:
  pick one Gradle plugin backend and add `es-runner-gradle-test-support`
- Want to inspect or depend on the shared-cluster internals:
  `es-runner-gradle-core` is the shared abstraction layer behind both plugins
- Want in-JVM embedded search nodes:
  treat the embedded modules as experimental

## Related

- [API reference](../api/)
- [Configuration reference](../configuration/)
- [Compatibility & support policy](../compatibility/)
