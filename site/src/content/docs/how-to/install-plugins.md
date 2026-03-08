---
title: Install plugins
description: Install Elasticsearch plugins before startup.
---

# Install plugins

Elastic Runner can install plugins before it starts Elasticsearch. Plugins are installed into the extracted distro.

## Install a single plugin

```java
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.2.4")
    .download(true)
    .plugin("analysis-icu")
    .workDir(Paths.get(".es"))
    .clusterName("plugins"));

try (ElasticServer server = ElasticRunner.start(config)) {
    System.out.println(server.clusterHealth().status());
}
```

## Install multiple plugins

```java
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.2.4")
    .download(true)
    .plugins(List.of("analysis-icu", "analysis-phonetic"))
    .workDir(Paths.get(".es"))
    .clusterName("plugins"));
```

## Notes

- Plugin installation is performed **before** Elasticsearch starts.
- Failures surface as `ElasticRunnerException` with a plugin log file path.

## Related

- [Troubleshooting](../troubleshooting/)
