---
title: Build & test matrix
description: CI jobs, test suites, and how to run them locally.
sidebar:
  order: 7
---

This page describes the exact jobs that currently back the compatibility and
support policy.

## CI overview

The repo currently runs on the Gradle `9.2.1` wrapper and uses these CI jobs:

- `test`
- `scala3Test`
- `docker-shared-cluster`
- `plugin-install`
- `windows-smoke`
- `opensearch-process`
- `embedded-jdk17`
- `embedded-jdk21`

## Job details

| Job | JDK | What it verifies |
| --- | --- | --- |
| `test` | `17` | Main Java/Scala/unit/integration coverage, Gradle plugin tests, and Scala/Spark matrix against Elasticsearch `9.3.1` and `8.19.11`. |
| `scala3Test` | `17` | Scala 3 coverage against Elasticsearch `9.3.1`. |
| `docker-shared-cluster` | `17` | Docker/Testcontainers-backed shared Gradle cluster plugin coverage on Linux. |
| `plugin-install` | `17` | Real plugin-install coverage against Elasticsearch `9.3.1` and the published `analysis-icu` plugin. |
| `windows-smoke` | `17` | Process-backed Windows smoke coverage for Elasticsearch `9.3.1`, including startup, README-style usage, startup-failure diagnostics, and Windows process-tree shutdown. |
| `opensearch-process` | `21` | Process-backed OpenSearch smoke tests for `3.5.0` and `2.19.4`, including README-style flows. |
| `embedded-jdk17` | `17` | Experimental embedded Elasticsearch `8.19.11` and OpenSearch `2.19.4`. |
| `embedded-jdk21` | `21` | Experimental embedded Elasticsearch `9.3.1` and OpenSearch `3.5.0`. |

## Main matrix

| Elasticsearch | Scala | Spark |
| --- | --- | --- |
| `9.3.1` | `2.12.21` | `3.5.8` |
| `9.3.1` | `2.13.18` | `3.5.8` |
| `9.3.1` | `2.13.18` | `4.1.1` |
| `8.19.11` | `2.12.21` | `3.5.8` |
| `8.19.11` | `2.13.18` | `3.5.8` |
| `8.19.11` | `2.13.18` | `4.1.1` |

## What runs locally by default

```bash
./gradlew test
./gradlew scala3Test
```

These commands cover the main process-backed path. Embedded and OpenSearch-only
smoke jobs are separate.

The repo also runs a narrower `windows-smoke` job in GitHub Actions so
process-backed lifecycle behavior is continuously verified on Windows instead of
only on Linux.

The Docker-backed shared-cluster plugin is verified in a dedicated Linux CI
lane because that is the environment the Testcontainers-backed functional test
is treated as authoritative for.

## Environment variables

| Variable | Purpose | Example |
| --- | --- | --- |
| `ES_DISTRO_ZIP` | Path to a local ZIP | `/path/to/elasticsearch-9.3.1.zip` |
| `ES_DISTRO_DOWNLOAD` | Allow downloads | `true` |
| `ES_DISTROS_DIR` | Directory for ZIPs | `distros` |
| `ES_DISTRO_BASE_URL` | Mirror base URL | `https://mirror.example.com/elasticsearch/` |
| `ES_VERSION` | Version used by example tests | `9.3.1` |
| `OPENSEARCH_DISTRO_ZIP` | Path to a local OpenSearch ZIP | `/path/to/opensearch-3.5.0.zip` |
| `OPENSEARCH_DISTRO_DOWNLOAD` | Allow OpenSearch downloads | `true` |
| `OPENSEARCH_DISTROS_DIR` | Directory for OpenSearch ZIPs | `distros` |
| `OPENSEARCH_DISTRO_BASE_URL` | OpenSearch mirror base URL | `https://mirror.example.com/opensearch/` |
| `OPENSEARCH_VERSION` | OpenSearch version used by example/smoke tests | `3.5.0` |
| `ES_EMBEDDED_HOME_8` | Extracted Elasticsearch 8 home for embedded tests | `/path/to/elasticsearch-8.19.11` |
| `ES_EMBEDDED_HOME_9` | Extracted Elasticsearch 9 home for embedded tests | `/path/to/elasticsearch-9.3.1` |
| `OPENSEARCH_EMBEDDED_HOME_2` | Extracted OpenSearch 2 home for embedded tests | `/path/to/opensearch-2.19.4` |
| `OPENSEARCH_EMBEDDED_HOME_3` | Extracted OpenSearch 3 home for embedded tests | `/path/to/opensearch-3.5.0` |

If you need downloads locally:

```bash
export ES_DISTRO_DOWNLOAD=true
export ES_VERSION=9.3.1
```

For the OpenSearch smoke tests:

```bash
export OPENSEARCH_DISTRO_DOWNLOAD=true
export OPENSEARCH_VERSION=3.5.0
./gradlew test \
  --tests io.github.wboult.esrunner.OpenSearchRunnerIntegrationTest \
  --tests io.github.wboult.esrunner.ReadmeOpenSearchExampleTest
```

For the experimental embedded smoke tests:

```bash
export ES_EMBEDDED_HOME_8=/path/to/elasticsearch-8.19.11
export ES_EMBEDDED_HOME_9=/path/to/elasticsearch-9.3.1
export OPENSEARCH_EMBEDDED_HOME_2=/path/to/opensearch-2.19.4
export OPENSEARCH_EMBEDDED_HOME_3=/path/to/opensearch-3.5.0
./gradlew :es-runner-embedded-common:test \
  :es-runner-embedded-8:test \
  :es-runner-embedded-9:test \
  :es-runner-embedded-opensearch-2:test \
  :es-runner-embedded-opensearch-3:test
```

## Related

- [Troubleshooting](../../how-to/troubleshooting/)
- [Compatibility matrix](../compatibility/)
