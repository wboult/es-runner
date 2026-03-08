---
title: API stability and deprecations
description: What is stable, what is experimental, and how deprecations work.
---

# API stability and deprecations

ES Runner aims to keep the **public API** stable and predictable.

## What counts as public API

The following are considered stable and supported:

- `ElasticRunner`, `ElasticRunnerConfig`, `ElasticServer`, `ElasticClient`
- The builder DSL and all `withX(...)` methods
- Documented configuration behavior and defaults
- Documented environment variables used by tests/examples

Anything **not documented** (internal helpers, log formats, private fields) can change without notice.

## Deprecation policy

When we need to remove or change a public API:

1. **Deprecate** the API in a minor release.
2. Provide an alternative and migration guidance.
3. Keep the deprecated API for **at least one minor release**.
4. Remove it in the next **major release**.

## What you can expect

- Deprecations are called out in release notes.
- Breaking changes are announced in advance in the changelog.
- Documentation is updated with migration steps.

## Related

- [Release & versioning policy](../../reference/release-versioning/)
- [Changelog](../../reference/changelog/)
