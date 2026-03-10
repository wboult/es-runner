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
- index, refresh, count, and match query
- README-style example flow

## Related

- [README.md](README.md)
- [site/src/content/docs/how-to/run-opensearch.md](site/src/content/docs/how-to/run-opensearch.md)
