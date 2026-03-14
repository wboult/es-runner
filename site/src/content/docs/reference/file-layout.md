---
title: File layout
description: Where ES Runner writes data and logs.
sidebar:
  order: 5
---

ES Runner writes into `workDir` using the following structure:

```text
<workDir>/
  <version>/
    config/
    data/
    logs/
      runner.log
      startup-diagnostics.txt
    es.pid
    state.json
```

- `data/` and `logs/` are used for node storage.
- `runner.log` captures the process output.
- `startup-diagnostics.txt` is written on startup failure and captures resolved
  distro info, sanitized config, exit code, recent log output, and remediation
  hints.
- `state.json` stores runtime metadata (PID, ports, base URI).

## Related

- [Configuration reference](../configuration/)
- [Troubleshooting](../../how-to/troubleshooting/)
