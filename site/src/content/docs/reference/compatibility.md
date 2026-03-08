---
title: Compatibility matrix
description: Supported and tested versions.
---

# Compatibility matrix

This matrix reflects what is **actively tested in CI**. Other combinations may work but are not guaranteed.

## Core

| Component | Supported / Tested |
| --- | --- |
| JDK | 17 (required) |
| OS | Ubuntu (CI). Windows/macOS should work with a compatible ZIP and JDK. |
| Elasticsearch | 9.3.1, 8.19.11 |

## Scala / Spark (CI matrix)

| Scala | Spark | Status |
| --- | --- | --- |
| 2.12.21 | 3.5.3 | tested |
| 2.13.18 | 3.5.3 | tested |
| 2.13.18 | 4.0.0 | tested |
| 2.13.18 | 4.1.1 | tested |

## Notes

- When running Scala 3 tests, CI uses Scala 3.x libraries.
- CI intentionally tracks only the latest supported 9.x patch and latest supported 8.x patch to avoid matrix explosion.
- If you rely on Spark integrations, pin Scala/Spark versions as shown above.

## Related

- [Build/test matrix](../build-test-matrix/)
- [Release & versioning policy](../release-versioning/)
