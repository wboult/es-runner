---
title: Error messages
description: Common errors and what they mean.
---

# Error messages

This reference lists common `ElasticRunnerException` messages and likely causes.

## `Distro archive does not exist`

**Meaning**: The ZIP path does not exist.

**Fix**:
- Provide a valid `distroZip`, or
- Set `version` + `download(true)`.

## `Distro archive is not a file`

**Meaning**: The path exists but is not a file.

**Fix**: Point to the ZIP file directly.

## `Failed to download distro`

**Meaning**: Download returned non-2xx status.

**Fix**:
- Verify the version exists.
- Confirm your mirror’s base URL and filename.

## `Timed out waiting for Elasticsearch`

**Meaning**: Startup exceeded `startupTimeout`.

**Fix**:
- Increase `startupTimeout`.
- Check disk IO and heap size.
- Inspect `runner.log` or `server.logTail()` for clues.

## `Elasticsearch process exited early`

**Meaning**: Process died during startup.

**Fix**:
- Inspect `server.logTail()`.
- Check config errors, port collisions, or permissions.

## `No free port found in range`

**Meaning**: No available HTTP port was found in the configured range.

**Fix**:
- Expand the range with `.portRange(start, end)`.
- Stop other processes using those ports.

## Where to look for logs

Elastic Runner writes logs to:

```
<workDir>/<version>/logs/runner.log
```

Use `server.logTail()` to see recent lines quickly.

## Related

- [Troubleshooting](../../how-to/troubleshooting/)
- [Configuration reference](../configuration/)
