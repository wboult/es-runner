---
title: File layout
description: Where Elastic Runner writes data and logs.
---

# File layout

Elastic Runner writes into `workDir` using the following structure:

```
<workDir>/
  <version>/
    config/
    data/
    logs/
    es.pid
    runner.log
    state.json
```

- `data/` and `logs/` are used for Elasticsearch storage.
- `runner.log` captures the Elasticsearch process output.
- `state.json` stores runtime metadata (PID, ports, base URI).

## Related

- [Configuration reference](../configuration/)
- [Troubleshooting](../../how-to/troubleshooting/)
