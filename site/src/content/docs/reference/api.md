---
title: API reference
description: Public entry points and the main Java API.
---

# API reference

This reference lists the main entry points for the Elastic Runner API.

## ElasticRunner

**Start methods**:

- `start(Path distroZip)`
- `start(String version)`
- `start(ElasticRunnerConfig config)`
- `start(Consumer<ElasticRunnerConfig.Builder> builder)`

**Helpers**:

- `withServer(...)` variants for auto-closing usage
- `resolveDistroZip(ElasticRunnerConfig config)` to resolve/download a ZIP

## ElasticRunnerConfig

`ElasticRunnerConfig` is immutable. Use:

- `ElasticRunnerConfig.defaults()` for defaults
- `ElasticRunnerConfig.from(builder -> ...)` for the builder DSL
- `withX(...)` methods for functional updates

See [Configuration reference](../configuration/) for the full list of fields.

## ElasticServer

`ElasticServer` represents a running process and provides:

- `baseUri()`, `httpPort()`, `clusterName()`
- `client()` for an `ElasticClient`
- `clusterHealth()`, `version()`, `nodesInfo()` and other convenience calls
- `logTail()` to inspect the last lines of the server log

`ElasticServer` implements `AutoCloseable` and should be closed when done.

## ElasticClient

A minimal HTTP wrapper that provides helpers such as:

- `clusterHealth()`
- `createIndex(...)`, `indexDocument(...)`, `search(...)`
- `nodesInfo()` and `nodesStats()`

## Related

- [Configuration reference](../configuration/)
- [Error messages](../errors/)
