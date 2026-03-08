---
title: Architecture & lifecycle
description: How ES Runner starts and manages Elasticsearch.
---

# Architecture & lifecycle

ES Runner starts **official Elasticsearch distributions** as an external process. It does not embed Elasticsearch.

## Startup flow

1. Resolve or download a ZIP
2. Extract into a versioned `workDir`
3. Write `elasticsearch.yml`
4. Install plugins (optional)
5. Start the process and stream logs
6. Poll the HTTP endpoint until ready

## Why external process?

- Matches production behavior more closely
- Avoids classpath and security conflicts
- Keeps the runner lightweight

## Lifecycle control

`ElasticServer` wraps the process and exposes:

- `close()` for shutdown
- `logTail()` for diagnostics
- `client()` for HTTP access

## Related

- [File layout](../../reference/file-layout/)
- [Configuration reference](../../reference/configuration/)
