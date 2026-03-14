---
title: Architecture & lifecycle
description: How ES Runner starts and manages Elasticsearch.
---

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

`ElasticServer` is the public facade for a running process-backed node. It exposes:

- `close()` for shutdown
- `logTail()` for diagnostics
- `client()` for HTTP access

Internally, process lifecycle work now lives in `ElasticProcessRuntime`. That keeps
`ElasticServer` focused on the public facade while startup stays in `ElasticRunner`
and HTTP helpers stay in `ElasticClient`.

## Related

- [File layout](../../reference/file-layout/)
- [Configuration reference](../../reference/configuration/)
