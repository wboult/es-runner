---
title: Install plugins
description: Install Elasticsearch plugins before startup.
sidebar:
  order: 3
---

ES Runner can install plugins before it starts Elasticsearch. Plugins are
installed into the extracted distro.

## Install a single plugin

```java
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .plugin("analysis-icu")
    .workDir(Paths.get(".es"))
    .clusterName("plugins"));

try (ElasticServer server = ElasticRunner.start(config)) {
    System.out.println(server.get("/_nodes/plugins"));
}
```

## Install multiple plugins

```java
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .plugins(List.of("analysis-icu", "analysis-phonetic"))
    .workDir(Paths.get(".es"))
    .clusterName("plugins"));
```

## Realistic plugin flow

If you want to prove the plugin is actually usable, create an index that uses a
plugin-provided analyzer or filter:

```java
ElasticRunner.withServer(builder -> builder
        .version("9.3.1")
        .download(true)
        .plugin("analysis-icu"),
    server -> {
        String icuDocsIndex = """
                {
                  "settings": {
                    "index": {
                      "number_of_replicas": 0
                    },
                    "analysis": {
                      "analyzer": {
                        "folding": {
                          "tokenizer": "standard",
                          "filter": ["lowercase", "icu_folding"]
                        }
                      }
                    }
                  },
                  "mappings": {
                    "properties": {
                      "title": {
                        "type": "text",
                        "analyzer": "folding"
                      }
                    }
                  }
                }
                """);
        String document = """
                {
                  "title": "Caf\u00e9 Cr\u00e8me"
                }
                """;
        String searchQuery = """
                {
                  "query": {
                    "match": {
                      "title": "cafe creme"
                    }
                  }
                }
                """;
        server.createIndex("icu-docs", icuDocsIndex);
        server.indexDocument("icu-docs", document);
        server.refresh("icu-docs");
        System.out.println(server.search("icu-docs", searchQuery));
    });
```

## Notes

- Plugin installation is performed **before** Elasticsearch starts.
- Failures surface as `ElasticRunnerException` with a plugin log file path.
- CI runs a real plugin-install integration test against the published
  `analysis-icu` plugin on Elasticsearch `9.3.1`.

## Related

- [Troubleshooting](../troubleshooting/)

