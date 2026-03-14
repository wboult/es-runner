---
title: Security & support policy
description: Security defaults, reporting, and support windows.
sidebar:
  order: 4
---

## Security defaults

ES Runner disables security by default for local convenience:

- `xpack.security.enabled = false`

If you need security features (auth, TLS), enable them via settings and provide required configuration files.

## Vulnerability reporting

Report security issues privately. See `SECURITY.md` for contact details and response expectations.

## Support window

- The primary supported path is the process-backed runner.
- Core published modules target Java 17 bytecode.
- CI validates a fixed set of Elasticsearch/OpenSearch/JDK combinations; see
  the compatibility policy for the exact current lines.
- Experimental embedded runners are intentionally outside the normal support
  contract even though they have smoke coverage in CI.
- Older patch lines may work, but once CI moves to a newer latest patch in a
  major line they are no longer considered supported.

## Related

- [Compatibility matrix](../../reference/compatibility/)
- [Release & versioning policy](../../reference/release-versioning/)
