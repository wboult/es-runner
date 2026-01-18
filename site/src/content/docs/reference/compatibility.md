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
| Elasticsearch | 9.2.4, 9.2.3, 8.9.10 |

## Scala / Spark (CI matrix)

| Scala | Spark | Status |
| --- | --- | --- |
| 2.12.21 | 3.5.3 | tested |
| 2.13.18 | 3.5.3 | tested |
| 2.13.18 | 4.0.0 | tested |
| 2.13.18 | 4.1.1 | tested |

## Notes

- When running Scala 3 tests, CI uses Scala 3.x libraries.
- If you rely on Spark integrations, pin Scala/Spark versions as shown above.

## Related

- [Build/test matrix](../build-test-matrix/)
- [Release & versioning policy](../release-versioning/)
