---
title: Performance guidance
description: Tuning startup time and runtime performance.
---

# Performance guidance

## Heap sizing

Elasticsearch startup and indexing speed are sensitive to heap size. Use:

```java
.heap("1g")
```

For small local tests, `256m` is often enough. For indexing-heavy tests, increase heap.

## Disk and IO

- Use fast local disks for `workDir`.
- Avoid shared network drives for data/logs.

## Startup timeouts

If startup is slow (cold downloads, slow disks), increase the timeout:

```java
.startupTimeout(Duration.ofSeconds(180))
```

## Multiple servers

- Use separate `workDir` per server.
- Use `portRange` to avoid collisions.

## Related

- [Configuration reference](../../reference/configuration/)
- [Troubleshooting](../../how-to/troubleshooting/)

