---
title: Build & test matrix
description: CI jobs, test suites, and how to run them locally.
---

## CI overview

CI runs multiple combinations of Elasticsearch/Scala/Spark versions and includes:

- `test` (Java/Scala unit + integration tests)
- `scala3Test` (Scala 3 tests)
- `opensearch-process` (latest OpenSearch 3.x and 2.x smoke tests)
- `embedded-jdk17` / `embedded-jdk21` (experimental embedded smoke tests)

## Test categories

| Category | Task | Notes |
| --- | --- | --- |
| Unit | `test` | Always runs. |
| Integration | `test` | Skips if no distro ZIP and downloads disabled. |
| Scala 3 | `scala3Test` | Runs separate Scala 3 test set. |
| OpenSearch smoke | `test --tests io.github.wboult.esrunner.OpenSearchRunnerIntegrationTest --tests io.github.wboult.esrunner.ReadmeOpenSearchExampleTest` | Runs latest supported OpenSearch 3.x and 2.x process-backed smoke tests. |
| Embedded smoke | `:es-runner-embedded-*` | Experimental embedded runners across ES/OpenSearch majors. |

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

## Local runs

```bash
./gradlew test
./gradlew scala3Test
```

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

## Related

- [Troubleshooting](../../how-to/troubleshooting/)
- [Compatibility matrix](../compatibility/)
