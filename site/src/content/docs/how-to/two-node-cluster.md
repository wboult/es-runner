---
title: Run a two-node cluster
description: Download once and start two nodes that form a cluster.
---

# Run a two-node cluster

This guide shows how to **download one ZIP programmatically** and start **two nodes** that form a cluster. Each node gets its own working directory to avoid shared state.

## 1. Resolve the distro ZIP

```java
String version = "9.2.4";
Path distrosDir = Paths.get("distros");

Path sharedZip = ElasticRunner.resolveDistroZip(
    ElasticRunnerConfig.from(builder -> builder
        .version(version)
        .download(true)
        .distrosDir(distrosDir))
);
```

## 2. Start two nodes with shared cluster settings

```java
String clusterName = "example-cluster";
String masterNodes = "[\"node-0\",\"node-1\"]";
String seedHosts = "[\"127.0.0.1:9300\",\"127.0.0.1:9301\"]";

ElasticServer node0 = startNode(sharedZip, clusterName, "node-0", 9200, 9300, masterNodes, seedHosts);
ElasticServer node1 = startNode(sharedZip, clusterName, "node-1", 9201, 9301, masterNodes, seedHosts);

try {
    System.out.println(node0.client().clusterHealth().status());
} finally {
    node1.close();
    node0.close();
}

static ElasticServer startNode(Path sharedZip,
                               String clusterName,
                               String nodeName,
                               int httpPort,
                               int transportPort,
                               String masterNodes,
                               String seedHosts) throws IOException {
    Path tempDir = Files.createTempDirectory("es-" + nodeName + "-");
    Path isolatedZip = tempDir.resolve(sharedZip.getFileName());
    Files.copy(sharedZip, isolatedZip);

    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("xpack.security.enabled", "false");
    settings.put("node.name", nodeName);
    settings.put("transport.port", Integer.toString(transportPort));
    settings.put("cluster.initial_master_nodes", masterNodes);
    settings.put("discovery.seed_hosts", seedHosts);

    ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
        .distroZip(isolatedZip)
        .workDir(tempDir.resolve("work"))
        .clusterName(clusterName)
        .httpPort(httpPort)
        .settings(settings));

    return ElasticRunner.start(config);
}
```

## Notes

- **Separate work directories** prevent data/log collisions.
- `cluster.initial_master_nodes` is only needed for first cluster bootstrap.
- `discovery.seed_hosts` must include transport ports for all nodes.

## Related

- [Configuration reference](../../reference/configuration/)
- [Troubleshooting](../troubleshooting/)
