---
title: Getting started
description: Install prerequisites and start your first Elasticsearch server.
---

# Getting started

This tutorial walks you through installing the prerequisites and starting your first server with Elastic Runner.

## Prerequisites

- **JDK 17+** available on your PATH
- An Elasticsearch distribution ZIP, or the ability to download one

## 1. Add the dependency

Add Elastic Runner to your build (example for Gradle):

```groovy
dependencies {
    testImplementation "com.elastic:elastic-runner:0.1.0"
}
```

## 2. Start a server

The simplest form is to point at a ZIP and start a server:

```java
import java.nio.file.Path;
import java.nio.file.Paths;

Path distroZip = Paths.get("elasticsearch-9.3.1-linux-x86_64.zip");
Path workDir = Paths.get(".es");

ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .distroZip(distroZip)
    .workDir(workDir)
    .clusterName("local-es")
    .setting("xpack.security.enabled", "false")
    .setting("discovery.type", "single-node"));

try (ElasticServer server = ElasticRunner.start(config)) {
    System.out.println(server.baseUri());
    System.out.println(server.clusterHealth().status());
}
```

## 3. Verify health

If `clusterHealth().status()` returns a valid status string (e.g. `"green"`, `"yellow"`), the server is up.

## 4. Shutdown

Closing the server stops the process and releases resources:

```java
server.close();
```

## Next steps

- [First single-node server](../first-server/)
- [Run a two-node cluster](../../how-to/two-node-cluster/)

