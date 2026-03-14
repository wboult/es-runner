---
title: Configuration reference
description: ElasticRunnerConfig fields, defaults, and behavior.
sidebar:
  order: 2
---

This page documents `ElasticRunnerConfig` fields, defaults, and how they're applied.

## Core fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `family` | `DistroFamily` | `ELASTICSEARCH` | Controls archive naming, launcher scripts, config file name, defaults, and default download base URL. |
| `distroZip` | `Path?` | `null` | Explicit ZIP path. If set, `version`/download are ignored. |
| `version` | `String?` | `null` | Version string used to resolve a ZIP when `distroZip` is not set. |
| `distrosDir` | `Path` | `distros` | Folder where ZIPs are stored/downloaded. |
| `download` | `boolean` | `false` | If `true`, download the ZIP even if missing. |
| `downloadBaseUrl` | `String` | Family-specific official artifacts URL | Base URL for downloads/mirrors. |
| `workDir` | `Path` | `.es` | Root directory for extracted distro, logs, and data. |
| `clusterName` | `String` | `local-es` | Written to `cluster.name` unless you override it in settings. |
| `httpPort` | `int` | `0` | `0` means pick a free port in the range. |
| `portRangeStart` | `int` | `9200` | Range used when `httpPort=0`. |
| `portRangeEnd` | `int` | `9300` | Range used when `httpPort=0`. |
| `heap` | `String` | `256m` | Used for the family-specific JVM opts env var (`ES_JAVA_OPTS` or `OPENSEARCH_JAVA_OPTS`). |
| `startupTimeout` | `Duration` | `60s` | Time to wait for readiness. |
| `shutdownTimeout` | `Duration` | `20s` | Time to wait for shutdown. |
| `settings` | `Map<String,String>` | Family-specific single-node defaults | Written to `elasticsearch.yml` or `opensearch.yml`. |
| `plugins` | `List<String>` | `[]` | Plugins installed before start. |
| `quiet` | `boolean` | `false` | If `true`, reduce logging. |

## Download and distros

Resolution order:

1. `distroZip` if provided
2. `version` + `distrosDir`
3. Download if ZIP missing or `download(true)`

You can resolve a ZIP programmatically without starting a server:

```java
Path zip = ElasticRunner.resolveDistroZip(
    ElasticRunnerConfig.from(builder -> builder
        .version("9.3.1")
        .download(true)
        .distrosDir(Paths.get("distros"))
    )
);
```

For OpenSearch:

```java
Path zip = ElasticRunner.resolveDistroZip(
    ElasticRunnerConfig.from(builder -> builder
        .family(DistroFamily.OPENSEARCH)
        .version("3.5.0")
        .download(true)
    )
);
```

## Settings behavior

- `cluster.name` is written if not already present.
- `path.data` and `path.logs` are always set to subfolders inside `workDir`.
- `http.port` is always set based on `httpPort`/range.

Important:

- `builder.setting(key, value)` adds or overrides settings without removing defaults.
- `builder.replaceSettings(map)` replaces all settings, including defaults. Use this only when you want full control.
- `builder.settings(map)` is a legacy alias for full replacement; it also removes defaults.

## Precedence

Settings are applied in this order:

1. Defaults (from `ElasticRunnerConfig.defaults()`)
   For OpenSearch, use `ElasticRunnerConfig.defaults(DistroFamily.OPENSEARCH)`.
2. Builder updates (including `.setting(...)` and `.replaceSettings(...)`)
3. Implicit writes (`cluster.name`, `path.data`, `path.logs`, `http.port`)

## Related

- [API reference](../api/)
- [Troubleshooting](../../how-to/troubleshooting/)
