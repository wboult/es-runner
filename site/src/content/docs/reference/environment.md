---
title: Environment variables
description: Environment variables used by tests and examples.
---

ES Runner itself does not depend on environment variables at runtime, but tests and examples do.

| Variable | Purpose | Example |
| --- | --- | --- |
| `ES_DISTRO_ZIP` | Use a specific Elasticsearch ZIP | `/path/to/elasticsearch-9.3.1.zip` |
| `ES_DISTRO_DOWNLOAD` | Allow Elasticsearch downloads in tests | `true` |
| `ES_DISTROS_DIR` | Directory containing Elasticsearch ZIPs | `distros` |
| `ES_DISTRO_BASE_URL` | Elasticsearch mirror base URL | `https://mirror.example.com/elasticsearch/` |
| `ES_VERSION` | Elasticsearch version for example tests | `9.3.1` |
| `OPENSEARCH_DISTRO_ZIP` | Use a specific OpenSearch ZIP | `/path/to/opensearch-3.5.0.zip` |
| `OPENSEARCH_DISTRO_DOWNLOAD` | Allow OpenSearch downloads in tests | `true` |
| `OPENSEARCH_DISTROS_DIR` | Directory containing OpenSearch ZIPs | `distros` |
| `OPENSEARCH_DISTRO_BASE_URL` | OpenSearch mirror base URL | `https://mirror.example.com/opensearch/` |

## Related

- [Build/test matrix](../build-test-matrix/)