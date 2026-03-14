---
title: API reference
description: Public entry points and the main Java API.
sidebar:
  order: 1
---

This reference lists the public entry points you are expected to start with
first. The library has a small surface area on purpose: start a node, get a
server handle, and use the attached client or convenience helpers.

## Recommended path

For most users, the intended flow is:

1. build a config with `ElasticRunnerConfig.from(builder -> ...)`
2. start a node with `ElasticRunner.start(...)` or `ElasticRunner.withServer(...)`
3. use `ElasticServer` for lifecycle plus common convenience operations
4. drop to `ElasticClient` when you want direct HTTP-style access

## ElasticRunner

`ElasticRunner` is the top-level entry point.

Start methods:

- `start(Path distroZip)`
- `start(String version)`
- `start(ElasticRunnerConfig config)`
- `start(Consumer<ElasticRunnerConfig.Builder> builder)`

Helpers:

- `withServer(...)` variants for auto-closing usage
- `resolveDistroZip(ElasticRunnerConfig config)` to resolve/download a ZIP
- `start(UnaryOperator<ElasticRunnerConfig>)` when you want to start from
  defaults and transform them functionally

## ElasticRunnerConfig

`ElasticRunnerConfig` is immutable. Use:

- `ElasticRunnerConfig.defaults()` for defaults
- `ElasticRunnerConfig.from(builder -> ...)` for the builder DSL
- `toBuilder()` when you want to start from an existing config and tweak it

See [Configuration reference](../configuration/) for the full list of fields.

## ElasticServer

`ElasticServer` represents a running Elasticsearch process. It provides:

- `baseUri()`, `httpPort()`, `clusterName()`
- `client()` for an `ElasticClient`
- `clusterHealth()`, `version()`, `nodesInfo()` and other convenience calls
- `logTail()` to inspect the last lines of the server log
- `stopWithResult()` when you need shutdown outcome details
- `state()` for an operational snapshot that can be written or inspected

`ElasticServer` implements `AutoCloseable` and should be closed when done.

## ElasticClient

`ElasticClient` is a minimal HTTP wrapper around one cluster base URI. It
provides helpers such as:

- `clusterHealth()`
- `createIndex(...)`, `indexDocument(...)`, `search(...)`
- `nodesInfo()` and `nodesStats()`
- `request(...)` and `requestNdjson(...)` for direct endpoint access

## Gradle test support

If you use the shared Gradle test cluster plugin, test code should normally
start with `ElasticGradleTestEnv.fromSystemProperties()`. That gives you:

- the injected cluster base URI
- a ready-made `ElasticClient`
- namespaced logical resource helpers such as `index("orders")`

See [Use shared Gradle test clusters](../../how-to/gradle-shared-test-clusters/)
for the full workflow.

## Related

- [Configuration reference](../configuration/)
- [Error messages](../errors/)
- [Compatibility matrix](../compatibility/)
