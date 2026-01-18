---
title: Error messages
description: Common errors and what they mean.
---

# Error messages

This reference lists common `ElasticRunnerException` messages and likely causes.

## `Distro archive does not exist`

**Meaning**: The ZIP path does not exist.

**Fix**: Provide a valid `distroZip`, or set `version` + `download(true)`.

## `Distro archive is not a file`

**Meaning**: The path exists but is not a file.

**Fix**: Point to the ZIP file directly.

## `Failed to download distro`

**Meaning**: Download returned non-2xx status.

**Fix**: Check version availability or `downloadBaseUrl`.

## `Timed out waiting for Elasticsearch`

**Meaning**: Startup exceeded `startupTimeout`.

**Fix**: Increase timeout, inspect logs, verify ports and heap.

## `Elasticsearch process exited early`

**Meaning**: Process died during startup.

**Fix**: Read `server.logTail()` for root cause; check permissions and heap.

## Related

- [Troubleshooting](../../how-to/troubleshooting/)
- [Configuration reference](../configuration/)
