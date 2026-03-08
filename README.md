# Elastic Runner

Elastic Runner launches a real Elasticsearch ZIP distribution in an isolated OS
process and gives tests or offline tooling a small, direct Java API for
starting, stopping, and talking to the cluster.

It is aimed at environments where you want a real Elasticsearch node but do not
want to depend on Docker, Testcontainers, or an already-running shared cluster.

Documentation: <https://wboult.github.io/elastic-runner/>

## Why use it

- runs the official Elasticsearch distribution, not an embedded fork
- works on bare-metal CI agents and restricted environments
- keeps Elasticsearch in a separate process, so your test JVM stays clean
- supports downloading distros from official URLs, local mirrors, or common
  cloud storage backends
- includes an incubating Gradle plugin for build-scoped shared test clusters

## When not to use it

Elastic Runner is a good fit when process startup cost is acceptable or when a
single build-scoped shared node is enough.

Use something else when:

- you already standardize on Docker/Testcontainers
- you need full container-level isolation per test
- you need production topology simulation beyond what a small local cluster can
  reasonably provide

## Requirements

- JDK 17+
- an Elasticsearch distribution ZIP, or a version plus `download(true)`
- for cloud mirrors:
  - `aws` for `s3://`
  - `gcloud` or `gsutil` for `gs://`
  - `azcopy` or `az` for `az://`

Latest CI-tested Elasticsearch lines:

- `9.3.1`
- `8.19.11`

## Quick start

```java
Path distroZip = Paths.get("elasticsearch-9.3.1-windows-x86_64.zip");
Path workDir = Paths.get(".es");

ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .distroZip(distroZip)
    .workDir(workDir)
    .clusterName("local-es")
    .setting("discovery.type", "single-node")
    .setting("xpack.security.enabled", "false"));

try (ElasticServer server = ElasticRunner.start(config)) {
    System.out.println(server.baseUri());
    System.out.println(server.clusterHealth());
    System.out.println(server.version());
}
```

## Simplest download flow

If you want Elastic Runner to fetch the ZIP for you:

```java
ElasticRunner.withServer(builder -> builder
    .version("9.3.1")
    .download(true),
    server -> System.out.println(server.clusterHealth()));
```

## Main API shape

The intended easy path is:

- `ElasticRunner.start(...)` or `ElasticRunner.withServer(...)`
- `ElasticRunnerConfig.from(builder -> ...)`
- `ElasticServer` for lifecycle plus convenience methods
- `ElasticClient` for direct HTTP-oriented operations

If you need explicit shutdown details:

```java
ElasticServer server = ElasticRunner.start(Paths.get("elasticsearch-9.3.1-windows-x86_64.zip"));
try {
    System.out.println(server.baseUri());
} finally {
    StopResult result = server.stopWithResult();
    System.out.println("graceful=" + result.graceful());
    System.out.println(server.logTail());
}
```

## Mirrors and private distros

`downloadBaseUrl(...)` supports:

- `https://mirror.example.com/elasticsearch/`
- `file:///srv/elasticsearch-mirror/`
- `s3://my-bucket/elasticsearch/`
- `gs://my-bucket/elasticsearch/`
- `az://myaccount/mycontainer/elasticsearch/`

Example:

```java
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("gs://elastic-mirror/elasticsearch/"));
```

See [docs/cloud-storage-mirrors.md](docs/cloud-storage-mirrors.md) for auth and
CLI setup.

## Gradle shared test clusters

Elastic Runner includes an incubating Gradle plugin for build-scoped shared
Elasticsearch clusters. It starts one node per cluster definition, reuses it
across projects/suites in the build, and injects a per-suite namespace so
parallel suites do not collide.

Current source-tree plugin id:

- `com.elastic.runner.shared-test-clusters`

Current source-tree helper artifact:

- `com.elastic:elastic-runner-gradle-test-support`

These identifiers are still provisional for the first public release. The repo
is using them internally today, but the published namespace/plugin id should
move to an owner-controlled namespace before Maven Central or Plugin Portal
publication.

Root build example:

```groovy
plugins {
    id 'com.elastic.runner.shared-test-clusters'
}

elasticTestClusters {
    clusters {
        register("integration") {
            version.set("9.3.1")
            download.set(true)
            clusterName.set("shared-it")
            quiet.set(true)
        }
    }

    suites {
        matchingName("integrationTest") {
            useCluster("integration")
            namespaceMode.set(com.elastic.runner.gradle.NamespaceMode.SUITE)
        }
    }
}
```

Test-side usage:

```java
ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
ElasticClient client = env.client();
String ordersIndex = env.index("orders");

client.createIndex(ordersIndex);
client.indexDocument(ordersIndex, "1", "{\"status\":\"new\"}");
client.refresh(ordersIndex);
```

See [docs/gradle-shared-test-clusters.md](docs/gradle-shared-test-clusters.md)
for the usage guide and
[docs/gradle-shared-cluster-plugin-design.md](docs/gradle-shared-cluster-plugin-design.md)
for the design rationale.

## More examples

Examples and deeper guides live in:

- [docs/](docs/)
- [site/src/content/docs/](site/src/content/docs/)

Useful starting points:

- [docs/gradle-shared-test-clusters.md](docs/gradle-shared-test-clusters.md)
- [docs/cloud-storage-mirrors.md](docs/cloud-storage-mirrors.md)
- [site/src/content/docs/tutorials/getting-started.md](site/src/content/docs/tutorials/getting-started.md)
- [site/src/content/docs/reference/api.md](site/src/content/docs/reference/api.md)

## Build and test

Unit tests always run. Integration tests require a distro ZIP or download
configuration.

Examples:

```powershell
$env:ES_DISTRO_ZIP = "C:\path\to\elasticsearch-9.3.1.zip"
.\gradlew.bat test
.\gradlew.bat scala3Test
```

```bash
export ES_DISTRO_ZIP=/path/to/elasticsearch-9.3.1.zip
./gradlew test
./gradlew scala3Test
```

If the system JDK is not compatible, the wrapper uses the pinned JDK 17 in
`.jdks/` via `gradle.properties`.

## Public release readiness

This repo already has strong testing and docs, but a few items still need to be
finished before a first public Maven Central release:

- choose an owner-controlled namespace and plugin id
- add a top-level `LICENSE`
- wire signed publication and release automation
- finish the public API/Javadoc polish pass

See [docs/public-release-readiness.md](docs/public-release-readiness.md) for the
repo-specific checklist.
