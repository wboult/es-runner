---
title: Customize ports and work directories
description: Run multiple servers without port collisions.
---

# Customize ports and work directories

When running multiple servers, you need **unique ports** and **isolated work directories**.

## Set a fixed HTTP port

```java
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.2.4")
    .download(true)
    .httpPort(9250)
    .workDir(Paths.get(".es"))
    .clusterName("custom-port")
    .setting("discovery.type", "single-node"));
```

## Use a port range

If you’re running multiple servers, use a port range and let Elastic Runner pick a free port:

```java
ElasticRunnerConfig config = ElasticRunnerConfig.defaults()
    .withVersion("9.2.4")
    .withDownload(true)
    .withPortRange(9200, 9300)
    .withWorkDir(Paths.get(".es"))
    .withClusterName("range");
```

## Isolate work directories

Use separate `workDir` values for each server to avoid shared state:

```java
Path workDir = Files.createTempDirectory("es-runner-");
ElasticRunnerConfig config = ElasticRunnerConfig.defaults()
    .withVersion("9.2.4")
    .withDownload(true)
    .withWorkDir(workDir)
    .withClusterName("isolated")
    .withSetting("discovery.type", "single-node");
```

## Related

- [Configuration reference](../../reference/configuration/)
- [Run a two-node cluster](../two-node-cluster/)
