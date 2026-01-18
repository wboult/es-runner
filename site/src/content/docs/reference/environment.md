---
title: Environment variables
description: Environment variables used by tests and examples.
---

# Environment variables

Elastic Runner itself does not depend on environment variables at runtime, but tests and examples do.

| Variable | Purpose | Example |
| --- | --- | --- |
| `ES_DISTRO_ZIP` | Use a specific ZIP | `/path/to/elasticsearch-9.2.4.zip` |
| `ES_DISTRO_DOWNLOAD` | Allow downloads in tests | `true` |
| `ES_DISTROS_DIR` | Directory containing ZIPs | `distros` |
| `ES_DISTRO_BASE_URL` | Download mirror base URL | `https://mirror.example.com/elasticsearch/` |
| `ES_VERSION` | Version for example tests | `9.2.4` |

## Related

- [Build/test matrix](../build-test-matrix/)
