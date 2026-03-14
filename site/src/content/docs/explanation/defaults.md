---
title: Defaults and safety
description: Why defaults are chosen and how to override them.
sidebar:
  order: 2
---

ES Runner ships with conservative defaults to make local use safe and predictable.

## Default settings

- `discovery.type = single-node`
- `xpack.security.enabled = false`
- `heap = 256m`

These defaults make local startup easier and avoid cluster-formation pitfalls. They are not suitable for production.

## Overriding defaults

- Use `.setting(key, value)` to override while keeping defaults.
- Use `.replaceSettings(map)` to replace all settings (and remove defaults).

## Example

```java
ElasticRunnerConfig config = ElasticRunnerConfig.defaults()
    .toBuilder()
    .setting("xpack.security.enabled", "true")
    .setting("discovery.type", "single-node")
    .build();
```

## Related

- [Configuration reference](../../reference/configuration/)
- [Run a two-node cluster](../../how-to/two-node-cluster/)

