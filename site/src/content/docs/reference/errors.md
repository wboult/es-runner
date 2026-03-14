---
title: Error messages
description: Common errors and what they mean.
sidebar:
  order: 4
---

This reference lists common `ElasticRunnerException` messages and likely causes.
Most failures now also have a more specific exception type and
`ElasticRunnerException.kind()` value, so callers can distinguish download,
resolution, plugin install, port binding, and startup-timeout failures without
matching on message text. Startup failures also write:

```text
<workDir>/<version>/logs/startup-diagnostics.txt
```

That file captures the resolved archive path, resolved download URI when
downloads were used, sanitized config, exit code when available, a recent log
tail, and common remediation hints.

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

**Exception type**: `DistroDownloadException`

**Fix**:
- Verify the version exists.
- Confirm your mirror's base URL and filename.
- Increase `downloadTimeout` if the archive source is slow.

## `Timed out waiting for Elasticsearch`

**Meaning**: Startup exceeded `startupTimeout`.

**Exception type**: `StartupTimeoutException`

**Fix**:
- Increase `startupTimeout`.
- Check disk IO and heap size.
- Inspect `startup-diagnostics.txt`, `runner.log`, or `server.logTail()` for clues.

## `Elasticsearch process exited early`

**Meaning**: Process died during startup.

**Exception type**: `ProcessStartException`

**Fix**:
- Inspect `startup-diagnostics.txt` first, then `server.logTail()`.
- Check config errors, port collisions, or permissions.

## `Timed out waiting for HTTP port bind`

**Meaning**: Elasticsearch did not finish binding its HTTP listener before `startupTimeout`.

**Exception type**: `PortBindingException`

**Fix**:
- Increase `startupTimeout`.
- Use a wider `.portRange(start, end)` if the current range is saturated.
- Inspect `startup-diagnostics.txt` or `runner.log` to confirm which address the node tried to publish.

## Where to look for logs

ES Runner writes logs to:

```text
<workDir>/<version>/logs/runner.log
<workDir>/<version>/logs/startup-diagnostics.txt
```

Use `server.logTail()` to see recent lines quickly when a server handle exists.

## Related

- [Troubleshooting](../../how-to/troubleshooting/)
- [Configuration reference](../configuration/)
