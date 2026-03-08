---
title: File layout
description: Where ES Runner writes data and logs.
---

# File layout

ES Runner writes into `workDir` using the following structure:

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
