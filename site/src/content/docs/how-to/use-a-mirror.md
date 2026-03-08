---
title: Use a download mirror
description: Point downloads at a local or private mirror.
---

# Use a download mirror

If your environment blocks direct downloads, you can point Elastic Runner at a **mirror**.

## Configure a mirror

```java
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("https://my-mirror.example.com/elasticsearch/")
    .workDir(Paths.get(".es"))
    .clusterName("local-es"));
```

The mirror must contain ZIPs with the same filenames as Elastic’s official artifacts, for example:

```
elasticsearch-9.3.1-linux-x86_64.zip
```

## Verify availability

Elastic Runner performs a download when the ZIP is missing or when `download(true)` is set. Use your mirror’s access logs or a HEAD check to confirm availability.

## Related

- [Download/resolve behavior](../../reference/configuration/#download-and-distros)
- [Troubleshooting downloads](../troubleshooting/)

