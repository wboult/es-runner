---
title: First single-node server
description: Start a single-node Elasticsearch server using a version string.
---

# First single-node server

This tutorial uses a **version string** so Elastic Runner downloads the ZIP for you.

## 1. Choose a version

Pick an Elasticsearch version supported by your environment. Example: `9.2.4`.

## 2. Start a single node

```java
import java.nio.file.Files;
import java.nio.file.Path;

String version = "9.2.4";
Path workDir = Files.createTempDirectory("es-runner-");

ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version(version)
    .download(true)
    .workDir(workDir)
    .clusterName("single-node")
    .setting("discovery.type", "single-node"));

try (ElasticServer server = ElasticRunner.start(config)) {
    System.out.println(server.baseUri());
    System.out.println(server.version());
}
```

## 3. Understand what happened

- `download(true)` ensures the ZIP is present and up to date.
- `workDir` is where data/logs are written.
- `discovery.type=single-node` disables clustering and is safe for local use.

## Next steps

- [Run a two-node cluster](../../how-to/two-node-cluster/)
- [Configuration reference](../../reference/configuration/)
