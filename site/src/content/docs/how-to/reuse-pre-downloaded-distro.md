---
title: Reuse a pre-downloaded distro
description: Download once, copy into per-node temp folders, and start multiple nodes.
---

# Reuse a pre-downloaded distro

When you want multiple nodes (or repeated test runs) without re-downloading,
resolve the distro ZIP once into a cache directory, then copy it into isolated
temp folders for each node.

## 1) Pre-download into a cache location

```java
import java.nio.file.Path;
import java.nio.file.Paths;

Path cacheDir = Paths.get(".cache", "elastic-runner");
String version = "9.2.4";

Path cachedZip = ElasticRunner.resolveDistroZip(
    ElasticRunnerConfig.from(builder -> builder
        .version(version)
        .download(true)
        .distrosDir(cacheDir))
);
```

## 2) Copy the ZIP into per-node temp directories

Each node should have its own working directory and its own ZIP copy to avoid
sharing data, logs, or extracted files.

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

static Path isolateZip(Path cachedZip, String nodeName) throws IOException {
    Path tempDir = Files.createTempDirectory("es-" + nodeName + "-");
    Path isolatedZip = tempDir.resolve(cachedZip.getFileName());
    Files.copy(cachedZip, isolatedZip);
    return isolatedZip;
}
```

## 3) Start multiple clustered nodes

```java
import java.util.LinkedHashMap;
import java.util.Map;

String clusterName = "example-cluster";
String masterNodes = "[\"node-0\",\"node-1\"]";
String seedHosts = "[\"127.0.0.1:9300\",\"127.0.0.1:9301\"]";

ElasticServer node0 = startNode(cachedZip, clusterName, "node-0", 9200, 9300, masterNodes, seedHosts);
ElasticServer node1 = startNode(cachedZip, clusterName, "node-1", 9201, 9301, masterNodes, seedHosts);

try {
    System.out.println(node0.client().clusterHealth().status());
} finally {
    node1.close();
    node0.close();
}

static ElasticServer startNode(Path cachedZip,
                               String clusterName,
                               String nodeName,
                               int httpPort,
                               int transportPort,
                               String masterNodes,
                               String seedHosts) throws IOException {
    Path isolatedZip = isolateZip(cachedZip, nodeName);

    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("xpack.security.enabled", "false");
    settings.put("node.name", nodeName);
    settings.put("transport.port", Integer.toString(transportPort));
    settings.put("cluster.initial_master_nodes", masterNodes);
    settings.put("discovery.seed_hosts", seedHosts);

    ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
        .distroZip(isolatedZip)
        .workDir(isolatedZip.getParent().resolve("work"))
        .clusterName(clusterName)
        .httpPort(httpPort)
        .settings(settings));

    return ElasticRunner.start(config);
}
```

## Notes

- The cache directory holds the original ZIP and stays read-only.
- Each node works from a temp directory, so data/logs are isolated.
- This pattern also works for repeating test runs without re-downloading.
