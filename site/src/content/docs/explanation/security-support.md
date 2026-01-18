---
title: Security & support policy
description: Security defaults, reporting, and support windows.
---

# Security & support policy

## Security defaults

Elastic Runner disables security by default for local convenience:

- `xpack.security.enabled = false`

If you need security features (auth, TLS), enable them via settings and provide required configuration files.

## Vulnerability reporting

Report security issues privately. See `SECURITY.md` for contact details and response expectations.

## Support window

- JDK 17 is required.
- CI validates against a fixed set of Elasticsearch versions; see the compatibility matrix.
- Older versions may work but are not guaranteed.

## Related

- [Compatibility matrix](../../reference/compatibility/)
- [Release & versioning policy](../../reference/release-versioning/)
