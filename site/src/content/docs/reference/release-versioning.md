---
title: Release & versioning policy
description: How versions are assigned and what changes mean.
sidebar:
  order: 8
---

ES Runner follows **Semantic Versioning (SemVer)**:

- **MAJOR**: breaking changes to public API or behavior
- **MINOR**: new features, backward compatible
- **PATCH**: bug fixes and internal improvements

## What is �public API�?

Public API includes:

- `ElasticRunner`, `ElasticRunnerConfig`, `ElasticServer`, `ElasticClient`
- The builder DSL (`ElasticRunnerConfig.defaults().toBuilder()...build()`)
- Documented behavior and defaults

Anything not documented may change without notice.

## Deprecation policy

- Deprecations are announced in release notes.
- Deprecated APIs remain for **at least one minor release**.
- Removals occur in the next **major** release unless otherwise stated.

## Release checklist (maintainers)

- Run full CI + `scala3Test`
- Update changelog and release notes
- Tag the release
- Publish artifacts
- Announce breaking changes and migration notes

## Related

- [Changelog](../changelog/)
- [API stability and deprecations](../../explanation/api-stability/)
- [Compatibility matrix](../compatibility/)

