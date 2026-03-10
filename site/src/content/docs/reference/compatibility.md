---
title: Compatibility matrix
description: Supported and tested versions.
---

This matrix reflects what is actively verified. Other combinations may work but are not guaranteed.

## Core

| Component | Supported / Tested |
| --- | --- |
| JDK | 17 (required) |
| OS | Ubuntu (CI). Windows/macOS should work with a compatible ZIP and JDK. |
| Elasticsearch | 9.3.1, 8.19.11 (CI matrix) |
| OpenSearch | 3.5.0, 2.19.4 (CI process smoke) |

## Scala / Spark (CI matrix)

| Scala | Spark | Status |
| --- | --- | --- |
| 2.12.21 | 3.5.8 | tested |
| 2.13.18 | 3.5.8 | tested |
| 2.13.18 | 4.1.1 | tested |

## Notes

- When running Scala 3 tests, CI uses Scala 3.x libraries.
- CI intentionally tracks only the latest supported Elasticsearch 9.x patch and latest supported Elasticsearch 8.x patch to avoid matrix explosion.
- Embedded Elasticsearch 8/9 and embedded OpenSearch 2/3 smoke coverage now runs in CI.
- OpenSearch process-backed startup and CRUD/search smoke coverage now runs in CI for the latest supported 3.x and 2.x lines.
- If you rely on Spark integrations, pin Scala/Spark versions as shown above.

## Related

- [Build/test matrix](../build-test-matrix/)
- [Release & versioning policy](../release-versioning/)
