---
title: Customize ports and work directories
description: Run multiple servers without port collisions.
sidebar:
  order: 2
---

When running multiple servers, you need **unique ports** and **isolated work directories**.

## Set a fixed HTTP port

```java
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .httpPort(9250)
    .workDir(Paths.get(".es"))
    .clusterName("custom-port")
    .setting("discovery.type", "single-node"));
```

## Rely on OS ephemeral ports

If you're running multiple servers, you can leave `httpPort` as the default `0`.
ES Runner will request a free port from the OS:

```java
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .workDir(Paths.get(".es"))
    .clusterName("ephemeral"));
```

## Isolate work directories

Use separate `workDir` values for each server to avoid shared state:

```java
Path workDir = Files.createTempDirectory("es-runner-");
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .workDir(workDir)
    .clusterName("isolated")
    .setting("discovery.type", "single-node"));
```

## Related

- [Configuration reference](../../reference/configuration/)
- [Run a two-node cluster](../two-node-cluster/)
