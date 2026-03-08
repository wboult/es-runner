---
title: Build & test matrix
description: CI jobs, test suites, and how to run them locally.
---

## CI overview

CI runs multiple combinations of Elasticsearch/Scala/Spark versions and includes:

- `test` (Java/Scala unit + integration tests)
- `scala3Test` (Scala 3 tests)

## Test categories

| Category | Task | Notes |
| --- | --- | --- |
| Unit | `test` | Always runs. |
| Integration | `test` | Skips if no distro ZIP and downloads disabled. |
| Scala 3 | `scala3Test` | Runs separate Scala 3 test set. |

## Environment variables

| Variable | Purpose | Example |
| --- | --- | --- |
| `ES_DISTRO_ZIP` | Path to a local ZIP | `/path/to/elasticsearch-9.3.1.zip` |
| `ES_DISTRO_DOWNLOAD` | Allow downloads | `true` |
| `ES_DISTROS_DIR` | Directory for ZIPs | `distros` |
| `ES_DISTRO_BASE_URL` | Mirror base URL | `https://mirror.example.com/elasticsearch/` |
| `ES_VERSION` | Version used by example tests | `9.3.1` |

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

## Related

- [Troubleshooting](../../how-to/troubleshooting/)
- [Compatibility matrix](../compatibility/)

