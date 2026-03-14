---
title: Compatibility & support policy
description: What is verified in CI, what is supported, and what is still experimental.
---

This page defines what ES Runner means by `verified`, `supported`, and
`experimental`.

## Support terms

- `Verified in CI`: this exact combination runs in GitHub Actions on every PR
  or push to `main`.
- `Supported`: regressions in the verified primary paths are in scope for fixes
  and documentation. Nearby combinations may work, but they are not promised.
- `Experimental`: useful and tested, but not part of the main support contract.
  Expect tighter version constraints and more implementation churn.

## Primary supported paths

| Area | Status | Notes |
| --- | --- | --- |
| Process-backed Elasticsearch runner | supported | This is the main product path for ES Runner. |
| Process-backed OpenSearch runner | supported | Same API surface as Elasticsearch, with `DistroFamily.OPENSEARCH`. |
| Official Java API Client adapter | supported | Optional adapter module on top of the process-backed runner. |
| Gradle shared test clusters plugin | supported, incubating API | Intended for build-scoped shared single-node Elasticsearch suites. |
| Embedded Elasticsearch/OpenSearch runners | experimental | Kept secondary to the process-backed path. |

## Verified lines

| Area | Verified lines | CI coverage | Notes |
| --- | --- | --- | --- |
| Gradle | `9.2.1` wrapper | all jobs | Wrapper version used by this repo and plugin functional tests. |
| JVM target | Java `17` bytecode | build/test jobs | Core published modules target Java 17. |
| Elasticsearch | `9.3.1`, `8.19.11` | full `test` matrix on JDK 17, plus Windows smoke on `9.3.1` | Main verified process-backed lines. |
| OpenSearch | `3.5.0`, `2.19.4` | `opensearch-process` smoke job on JDK 21 | Verified process-backed smoke path. |
| Scala | `2.12.21`, `2.13.18`, `3.x` | `test` + `scala3Test` | Scala 3 is verified separately. |
| Spark | `3.5.8`, `4.1.1` | `test` matrix | Verified with the Scala combinations listed below. |

## Verified Scala / Spark matrix

| Scala | Spark | CI status |
| --- | --- | --- |
| `2.12.21` | `3.5.8` | verified |
| `2.13.18` | `3.5.8` | verified |
| `2.13.18` | `4.1.1` | verified |
| `3.x` | example/test coverage only | verified via `scala3Test`, not a Spark matrix |

## Experimental but CI-verified

| Area | Verified lines | CI coverage | Notes |
| --- | --- | --- | --- |
| Embedded Elasticsearch | `8.19.11`, `9.3.1` | `embedded-jdk17` and `embedded-jdk21` | Experimental in-JVM runners under `experimental/embedded/`. |
| Embedded OpenSearch | `2.19.4`, `3.5.0` | `embedded-jdk17` and `embedded-jdk21` | Experimental in-JVM runners under `experimental/embedded/`. |

## What is not promised

- Older Elasticsearch or OpenSearch patch lines are not kept in CI once this
  repo moves to a newer latest patch in that major line.
- Multi-node orchestration is possible, but the main support focus is still the
  single-node local automation path.
- Embedded runners are not part of the normal support contract even though they
  have CI smoke coverage.
- Non-wrapper Gradle versions may work, but `9.2.1` is the only line currently
  verified in this repo.

## Practical guidance

- If you want the lowest-risk path, use the process-backed runner on JDK 17+
  with the verified Elasticsearch or OpenSearch lines above.
- If you use the Gradle shared-cluster plugin, treat it as build-scoped
  infrastructure for integration suites, not as a general-purpose cluster
  manager.
- If you rely on Spark integrations, pin Scala/Spark versions exactly as shown
  above.
- If you choose the embedded modules, do it knowingly as an experimental path
  with narrower guarantees.

## Related

- [Build/test matrix](../build-test-matrix/)
- [Security & support policy](../../explanation/security-support/)
- [Release & versioning policy](../release-versioning/)
