# Run OpenSearch

ES Runner supports OpenSearch in the main process-backed path, not just in the
experimental embedded modules.

Current verified process-backed OpenSearch lines:

- `3.5.0`
- `2.19.4`

## Quick start

```java
import io.github.wboult.esrunner.DistroFamily;
import io.github.wboult.esrunner.ElasticRunner;

ElasticRunner.withServer(builder -> builder
        .family(DistroFamily.OPENSEARCH)
        .version("3.5.0")
        .download(true),
    server -> {
        System.out.println(server.baseUri());
        System.out.println(server.clusterHealth());
    });
```

If you already have a local ZIP:

```java
ElasticRunner.withServer(builder -> builder
        .family(DistroFamily.OPENSEARCH)
        .distroZip(Path.of("opensearch-3.5.0-linux-x64.tar.gz"))
        .workDir(Path.of(".opensearch")),
    server -> System.out.println(server.version()));
```

## Realistic indexing flow

OpenSearch supports the same template-plus-bulk workflow as Elasticsearch. A
small realistic flow looks like this:

```java
ElasticRunner.withServer(builder -> builder
        .family(DistroFamily.OPENSEARCH)
        .version("3.5.0")
        .download(true),
    server -> {
        server.putIndexTemplate("orders-template", """
                {
                  "index_patterns": ["orders-*"],
                  "template": {
                    "settings": {
                      "index": {
                        "number_of_replicas": 0
                      }
                    },
                    "mappings": {
                      "properties": {
                        "region": { "type": "keyword" },
                        "status": { "type": "keyword" },
                        "description": { "type": "text" }
                      }
                    },
                    "aliases": {
                      "orders-read": {}
                    }
                  }
                }
                """);
        server.createIndex("orders-2026-03");
        server.bulk("""
                {"index":{"_index":"orders-2026-03","_id":"o-100"}}
                {"region":"eu","status":"shipped","description":"overnight bike delivery"}
                {"index":{"_index":"orders-2026-03","_id":"o-101"}}
                {"region":"us","status":"pending","description":"standard helmet delivery"}
                """);
        server.refresh("orders-2026-03");

        System.out.println(server.search("orders-read", """
                {
                  "query": {
                    "match": {
                      "description": "overnight"
                    }
                  }
                }
                """));
    });
```

## Defaults

Use `ElasticRunnerConfig.defaults(DistroFamily.OPENSEARCH)` when you want the
OpenSearch family defaults:

- OpenSearch download base URL
- `opensearch.yml`
- OpenSearch launcher/plugin script names
- `OPENSEARCH_PATH_CONF`
- `OPENSEARCH_JAVA_OPTS`
- local unauthenticated single-node defaults, including
  `plugins.security.disabled=true`

## Mirrors

OpenSearch supports the same process-backed mirror flow as Elasticsearch:

- official OpenSearch downloads
- internal `https://` mirrors
- `file://` mirrors
- pre-signed HTTPS URLs for private cloud object storage

See [docs/cloud-storage-mirrors.md](docs/cloud-storage-mirrors.md) for mirror
setup.

## CI coverage

OpenSearch process-backed smoke tests now run in CI for the latest supported:

- OpenSearch `3.x`
- OpenSearch `2.x`

The smoke coverage verifies:

- process startup
- root ping / health
- template creation
- concrete index creation from that template
- bulk ingest, refresh, and count
- match, term, and aggregation queries
- README-style example flow

## Related

- [README.md](README.md)
- [site/src/content/docs/how-to/run-opensearch.md](site/src/content/docs/how-to/run-opensearch.md)
