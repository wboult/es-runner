# ES Runner

[![CI](https://github.com/wboult/es-runner/actions/workflows/ci-main.yml/badge.svg)](https://github.com/wboult/es-runner/actions/workflows/ci-main.yml)
[![Docs](https://github.com/wboult/es-runner/actions/workflows/docs.yml/badge.svg)](https://github.com/wboult/es-runner/actions/workflows/docs.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/license/mit/)

ES Runner launches a real Elasticsearch or OpenSearch ZIP distribution in an
isolated OS process and gives tests or offline tooling a small, direct Java API
for starting, stopping, and talking to the cluster.

It is aimed at environments where you want a real search node but do not want
to depend on Docker, Testcontainers, or an already-running shared cluster.

Documentation: <https://wboult.github.io/es-runner/>
Contributor setup: [CONTRIBUTING.md](CONTRIBUTING.md)

## Table of contents

- [Why ES Runner instead of Testcontainers or custom scripts?](#why-es-runner-instead-of-testcontainers-or-custom-scripts)
- [Install and release status](#install-and-release-status)
- [Why use it](#why-use-it)
- [Start with the path that matches your use case](#start-with-the-path-that-matches-your-use-case)
- [When not to use it](#when-not-to-use-it)
- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Starting from a local ZIP](#starting-from-a-local-zip)
- [Realistic indexing flow](#realistic-indexing-flow)
- [Main API shape](#main-api-shape)
- [Startup failure diagnostics](#startup-failure-diagnostics)
- [Official Java API Client](#official-java-api-client)
- [Plugin install](#plugin-install)
- [Mirrors and private distros](#mirrors-and-private-distros)
- [Process-backed OpenSearch](#process-backed-opensearch)
- [Shared Gradle test clusters](#shared-gradle-test-clusters)
- [Experimental embedded servers](#experimental-embedded-servers)

## Why ES Runner instead of Testcontainers or custom scripts?

Use ES Runner when you want a real search node but Docker is the wrong fit:

- bare-metal or locked-down CI agents where Docker is unavailable or unwanted
- local automation that should stay close to the official ZIP distribution
- multi-project Gradle builds that should share one build-scoped node instead
  of paying repeated startup cost per suite
- process-level realism without maintaining your own download, extract, config,
  health-check, and shutdown scripts

Compared with common alternatives:

- **Testcontainers** is stronger when Docker is already standard and you want
  container-level isolation as the default
- **hand-rolled scripts** are fine for one build, but ES Runner packages the
  distro lifecycle, cleanup, diagnostics, namespacing, and Gradle sharing into
  one maintained library
- **embedded JVM servers** can be useful for experiments, but ES Runner treats
  separate-process startup as the stable default because it is closer to how
  Elasticsearch and OpenSearch are meant to run

## Install and release status

The release workflow, signing, Maven metadata, and Gradle plugin publication
setup are all in the repo now. Until the first live tagged release is cut, the
normal ways to consume ES Runner are:

- a composite build for the Gradle plugin
- a local Maven repo populated with the release dry-run tasks

The publishable coordinates and plugin id are:

- `io.github.wboult:es-runner`
- `io.github.wboult:es-runner-java-client`
- `io.github.wboult:es-runner-gradle-test-support`
- plugin id `io.github.wboult.es-runner.shared-test-clusters`

Core library, once published:

```groovy
testImplementation("io.github.wboult:es-runner:<version>")
```

Optional official Java client adapter:

```groovy
testImplementation("io.github.wboult:es-runner-java-client:<version>")
```

Gradle shared-cluster plugin, once published:

```groovy
plugins {
    id("io.github.wboult.es-runner.shared-test-clusters") version "<version>"
}

dependencies {
    testImplementation("io.github.wboult:es-runner-gradle-test-support:<version>")
}
```

Before the first live release, use the checked-in sample and release dry-run
flow described in [docs/releasing.md](docs/releasing.md) and
[docs/gradle-shared-test-clusters.md](docs/gradle-shared-test-clusters.md).

## Why use it

- runs the official Elasticsearch or OpenSearch distribution, not an embedded fork
- works on bare-metal CI agents and restricted environments
- keeps Elasticsearch in a separate process, so your test JVM stays clean
- supports downloading distros from official URLs, HTTPS mirrors, and local file paths
- includes an incubating Gradle plugin for build-scoped shared test clusters

## Start with the path that matches your use case

- **One JVM test suite or offline tool**:
  start with the [First single-node server](https://wboult.github.io/es-runner/tutorials/first-server/)
  tutorial and the [getting started](https://wboult.github.io/es-runner/tutorials/getting-started/)
  walkthrough
- **A Gradle build with multiple suites or subprojects**:
  use [docs/gradle-shared-test-clusters.md](docs/gradle-shared-test-clusters.md)
  for one shared cluster plus per-suite namespaces
- **OpenSearch instead of Elasticsearch**:
  switch the distro family and follow the same process-backed flow documented in
  the [compatibility](https://wboult.github.io/es-runner/reference/compatibility/)
  and [configuration](https://wboult.github.io/es-runner/reference/configuration/)
  docs
- **Mirror, cache, or CI tuning**:
  use [docs/performance.md](docs/performance.md) and
  [docs/cloud-storage-mirrors.md](docs/cloud-storage-mirrors.md)

## When not to use it

ES Runner is a good fit when process startup cost is acceptable or when a
single build-scoped shared node is enough.

Use something else when:

- you already standardize on Docker/Testcontainers
- you need full container-level isolation per test
- you need production topology simulation beyond what a small local cluster can
  reasonably provide

See [docs/performance.md](docs/performance.md) for the startup cost model,
shared-cluster savings, and mirror/cache guidance.

## Requirements

- JDK 17+
- an Elasticsearch/OpenSearch distribution ZIP, or a version plus `download(true)`

Latest verified process-backed lines and platforms:

- Elasticsearch `9.3.1` and `8.19.11` in the main Linux matrix
- OpenSearch `3.5.0` and `2.19.4` in CI smoke tests
- Windows process-backed smoke coverage for Elasticsearch `9.3.1`

See [site/src/content/docs/reference/compatibility.md](site/src/content/docs/reference/compatibility.md)
for the exact support policy, verified JDK/Gradle lines, and what is still
experimental.

## Quick start

The simplest way to start — ES Runner downloads the distribution for you:

```java
ElasticRunner.withServer(builder -> builder
    .version("9.3.1")
    .download(true),
    server -> System.out.println(server.clusterHealth()));
```

For OpenSearch, select the distro family:

```java
ElasticRunner.withServer(builder -> builder
    .family(DistroFamily.OPENSEARCH)
    .version("3.5.0")
    .download(true),
    server -> System.out.println(server.clusterHealth()));
```

## Starting from a local ZIP

If you already have a distribution ZIP:

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

## Realistic indexing flow

For something closer to a real automation or integration-test harness, create a
template, let it shape a concrete index, bulk seed a few documents, then query
through an alias:

```java
String ordersTemplate = """
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
                "customer": { "type": "keyword" },
                "region": { "type": "keyword" },
                "status": { "type": "keyword" },
                "description": { "type": "text" },
                "total": { "type": "double" }
              }
            },
            "aliases": {
              "orders-read": {}
            }
          }
        }
        """;
String shippedOrdersByRegionQuery = """
        {
          "query": {
            "term": {
              "status": "shipped"
            }
          },
          "aggs": {
            "orders_by_region": {
              "terms": {
                "field": "region"
              }
            }
          }
        }
        """;

ElasticRunner.withServer(builder -> builder
        .version("9.3.1")
        .download(true),
    server -> {
        server.putIndexTemplate("orders-template", ordersTemplate);
        server.createIndex("orders-2026-03");
        String firstOrder = """
                {
                  "customer": "acme",
                  "region": "eu",
                  "status": "shipped",
                  "description": "overnight bike delivery",
                  "total": 120.5
                }
                """;
        String secondOrder = """
                {
                  "customer": "acme",
                  "region": "eu",
                  "status": "pending",
                  "description": "standard helmet delivery",
                  "total": 45.0
                }
                """;
        String thirdOrder = """
                {
                  "customer": "globex",
                  "region": "us",
                  "status": "shipped",
                  "description": "overnight gloves delivery",
                  "total": 75.25
                }
                """;
        server.bulkIndexDocuments("orders-2026-03", List.of(firstOrder, secondOrder, thirdOrder));
        server.refresh("orders-2026-03");

        System.out.println(server.search("orders-read", shippedOrdersByRegionQuery));
    });
```

## Main API shape

The intended easy path is:

- `ElasticRunner.start(...)` or `ElasticRunner.withServer(...)`
- `ElasticRunnerConfig.from(builder -> ...)`
- `ElasticServer` for lifecycle plus convenience methods
- `ElasticClient` for direct HTTP-oriented operations
- `es-runner-java-client` when you want the official Elasticsearch Java API Client

`ElasticRunnerConfig` now also owns the default timeout model for the
process-backed path:

- `startupTimeout` / `shutdownTimeout` for lifecycle
- `requestTimeout` / `bulkTimeout` for the attached `ElasticClient`
- `downloadTimeout` for distro downloads

### Which artifact contains what

| Artifact | Key classes | Use when |
|----------|------------|----------|
| `io.github.wboult:es-runner` | `ElasticRunner`, `ElasticRunnerConfig`, `ElasticServer`, `ElasticClient` | Always — this is the core library |
| `io.github.wboult:es-runner-java-client` | `ElasticJavaClients`, `ManagedElasticsearchClient` | You want Elastic's official typed Java API Client |
| `io.github.wboult:es-runner-gradle-test-support` | `ElasticGradleTestEnv` | Your tests run inside the shared Gradle cluster plugin |
| Plugin id `io.github.wboult.es-runner.shared-test-clusters` | `ElasticTestClustersExtension`, `ElasticClusterService` | You want build-scoped shared clusters in Gradle |

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

## Startup failure diagnostics

When startup fails, ES Runner writes a diagnostics file alongside the normal
runner log:

```text
<workDir>/<version>/logs/startup-diagnostics.txt
```

The thrown `ElasticRunnerException` includes the resolved archive path, the
resolved download URI when version-based resolution was used, the process exit
code when available, a recent log tail, and the path to that diagnostics file.

The diagnostics file also captures the effective runner config, redacts common
secret-like setting values, and adds common remediation hints for startup
failures.

## Official Java API Client

If you want the standard typed Java client instead of the small built-in
HTTP wrapper, use the optional adapter artifact:

- `io.github.wboult:es-runner-java-client`

Gradle:

```groovy
testImplementation("io.github.wboult:es-runner-java-client:0.1.0")
```

Maven:

```xml
<dependency>
  <groupId>io.github.wboult</groupId>
  <artifactId>es-runner-java-client</artifactId>
  <version>0.1.0</version>
</dependency>
```

Example:

```java
import io.github.wboult.esrunner.ElasticRunner;
import io.github.wboult.esrunner.ElasticServer;
import io.github.wboult.esrunner.javaclient.ElasticJavaClients;
import io.github.wboult.esrunner.javaclient.ManagedElasticsearchClient;

try (ElasticServer server = ElasticRunner.start(Paths.get("elasticsearch-9.3.1-windows-x86_64.zip"));
     ManagedElasticsearchClient managed = ElasticJavaClients.create(server)) {
    String clusterName = managed.client().info().clusterName();
    System.out.println(clusterName);
}
```

You can customize the underlying official client setup for auth, headers,
timeouts, or other REST client builder options:

```java
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

try (ManagedElasticsearchClient managed = ElasticJavaClients.create(server, builder -> builder
        .customizeRestClientBuilder(restClient -> restClient.setDefaultHeaders(new Header[] {
            new BasicHeader("Authorization", "ApiKey test-key")
        })))) {
    System.out.println(managed.client().info().version().number());
}
```

See [docs/official-java-client.md](docs/official-java-client.md) for the full
usage notes.

## Plugin install

ES Runner can install Elasticsearch plugins before startup:

```java
ElasticRunner.withServer(builder -> builder
        .version("9.3.1")
        .download(true)
        .plugin("analysis-icu"),
    server -> System.out.println(server.get("/_nodes/plugins")));
```

See [site/src/content/docs/how-to/install-plugins.md](site/src/content/docs/how-to/install-plugins.md)
for a real analyzer example and the current plugin-install CI coverage.

## Mirrors and private distros

`downloadBaseUrl(...)` supports:

- `https://mirror.example.com/elasticsearch/`
- `https://mirror.example.com/opensearch/`
- `file:///srv/elasticsearch-mirror/`
- `file:///srv/opensearch-mirror/`

Examples:

```java
ElasticRunnerConfig mirrorConfig = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("https://internal-mirror.example.com/elasticsearch/"));

ElasticRunnerConfig openSearchMirrorConfig = ElasticRunnerConfig.from(builder -> builder
    .family(DistroFamily.OPENSEARCH)
    .version("3.5.0")
    .download(true)
    .downloadBaseUrl("https://internal-mirror.example.com/opensearch/"));

ElasticRunnerConfig fileMirrorConfig = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("file:///srv/mirrors/elasticsearch/"));
```

For private S3, GCS, or Azure Blob buckets, generate a pre-signed / SAS HTTPS
URL and use it directly. These work with the built-in `https://` downloader.
See [docs/cloud-storage-mirrors.md](docs/cloud-storage-mirrors.md) for
per-provider instructions, and
[docs/cloud-storage-extension-plan.md](docs/cloud-storage-extension-plan.md)
for the planned native SDK-based extension modules.

## Process-backed OpenSearch

OpenSearch is a first-class part of the process-backed runner now, not just an
embedded experiment.

- current verified OpenSearch lines: `3.5.0` and `2.19.4`
- process-backed OpenSearch smoke tests run in CI
- OpenSearch uses the same API surface as Elasticsearch, with
  `DistroFamily.OPENSEARCH` selecting the family-specific defaults

Quick example:

```java
String ordersTemplate = """
        {
          "index_patterns": ["orders-*"],
          "template": {
            "settings": {
              "index": {
                "number_of_replicas": 0
              }
            },
            "aliases": {
              "orders-read": {}
            }
          }
        }
        """;
String overnightQuery = """
        {
          "query": {
            "match": {
              "description": "overnight"
            }
          }
        }
        """;

ElasticRunner.withServer(builder -> builder
        .family(DistroFamily.OPENSEARCH)
        .version("3.5.0")
        .download(true),
    server -> {
        server.putIndexTemplate("orders-template", ordersTemplate);
        server.createIndex("orders-2026-03");
        String shippedOrder = """
                {
                  "status": "shipped",
                  "region": "eu",
                  "description": "overnight bike delivery"
                }
                """;
        String pendingOrder = """
                {
                  "status": "pending",
                  "region": "eu",
                  "description": "standard helmet delivery"
                }
                """;
        server.bulkIndexDocuments("orders-2026-03", List.of(shippedOrder, pendingOrder));
        server.refresh("orders-2026-03");
        System.out.println(server.search("orders-read", overnightQuery));
    });
```

See [docs/run-opensearch.md](docs/run-opensearch.md) for the process-backed
OpenSearch guide.

## Gradle shared test clusters

ES Runner includes an incubating Gradle plugin for build-scoped shared
Elasticsearch clusters. It starts one node per cluster definition, reuses it
across projects/suites in the build, only boots it when a bound test task
actually forks, waits for yellow cluster health before handing tests the
connection details, and injects a per-suite namespace so parallel suites do
not collide.

Current source-tree plugin id:

- `io.github.wboult.es-runner.shared-test-clusters`

Current source-tree helper artifact:

- `io.github.wboult:es-runner-gradle-test-support`

This is an independent OSS library and is not affiliated with Elastic. The code
uses the owner-controlled `io.github.wboult` namespace for packages,
coordinates, and Gradle plugin ids.

Current setup status:

- the plugin already works in this repo and in composite builds
- the release workflow now wires Maven Central and Gradle Plugin Portal publication
- live publication still requires repo secrets plus a claimed Central namespace
- a checked-in published-artifact-style automation harness sample lives in
  `samples/gradle-shared-cluster-automation-harness/`

Before publication, the easiest way to try it is a composite build. In your
consumer `settings.gradle`:

```groovy
pluginManagement {
    includeBuild("../es-runner")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

Then apply the plugin in the root build:

```groovy
plugins {
    id 'io.github.wboult.es-runner.shared-test-clusters'
}

elasticTestClusters {
    clusters {
        register("integration") {
            version.set("9.3.1")
            download.set(true)
            clusterName.set("shared-it")
            quiet.set(true)
            startupTimeoutMillis.set(180_000L)
        }
    }

    suites {
        matchingName("integrationTest") {
            useCluster("integration")
            namespaceMode.set(io.github.wboult.esrunner.gradle.NamespaceMode.SUITE)
        }
        matchingName("smokeTest") {
            useCluster("integration")
            namespaceMode.set(io.github.wboult.esrunner.gradle.NamespaceMode.SUITE)
        }
    }
}
```

Subprojects define suites normally. All matching suite tasks reuse the same
shared ES9 distro/process for the build:

```groovy
subprojects {
    apply plugin: 'java'

    testing {
        suites {
            withType(org.gradle.api.plugins.jvm.JvmTestSuite).configureEach {
                useJUnitJupiter()
                dependencies {
                    implementation("io.github.wboult:es-runner-gradle-test-support:${version}")
                }
            }

            register("integrationTest", org.gradle.api.plugins.jvm.JvmTestSuite)
            register("smokeTest", org.gradle.api.plugins.jvm.JvmTestSuite)
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
String newOrder = """
        {
          "status": "new"
        }
        """;
client.indexDocument(ordersIndex, newOrder);
client.refresh(ordersIndex);
```

Namespace behavior:

- tests inside one suite task can share state
- different suite tasks get different physical names by default
- the same logical `orders` name becomes different actual indices such as:
  - `erabc123_app_integrationtest-orders`
  - `erabc123_app_smoketest-orders`
  - `erabc123_search_integrationtest-orders`
  - `erabc123_search_smoketest-orders`

That lets multiple projects and suites share one node without data collisions
or stale test data leaking across suites.

There is also a realistic multi-project automation harness sample in
[`samples/gradle-shared-cluster-automation-harness`](samples/gradle-shared-cluster-automation-harness)
that consumes the plugin/helper through normal coordinates. It includes a
small internal support subproject, seeded order fixtures, and a negative-path
suite that proves raw non-namespaced access fails. Before publication, point it
at a local Maven repo with `-PesRunnerRepositoryUrl=...`; after publication,
it can run with just `-PesRunnerVersion=...`.

Shared test-cluster defaults also disable Elasticsearch disk-threshold
allocation checks, which avoids single-node local builds getting stuck red on
machines with low free-disk percentage.

See [docs/gradle-shared-test-clusters.md](docs/gradle-shared-test-clusters.md)
for the usage guide,
[docs/gradle-shared-test-cluster-best-practices.md](docs/gradle-shared-test-cluster-best-practices.md)
for namespacing, cleanup, and suite design, and
[docs/gradle-shared-cluster-plugin-design.md](docs/gradle-shared-cluster-plugin-design.md)
for the design rationale.

## Experimental embedded runners

Experimental in-JVM runners still exist, but they are intentionally secondary
to the main process-backed path.

- they live under `experimental/embedded/`
- they target simple local HTTP/index/search scenarios
- they are documented separately in [docs/embedded-jvm-server.md](docs/embedded-jvm-server.md)

Current experimental modules:

- `es-runner-embedded-8` for Elasticsearch `8.19.11`
- `es-runner-embedded-9` for Elasticsearch `9.3.1`
- `es-runner-embedded-opensearch-2` for OpenSearch `2.19.4`
- `es-runner-embedded-opensearch-3` for OpenSearch `3.5.0`

## More examples

Examples and deeper guides live in:

- [docs/](docs/)
- [site/src/content/docs/](site/src/content/docs/)

Useful starting points:

- [docs/gradle-shared-test-clusters.md](docs/gradle-shared-test-clusters.md)
- [docs/gradle-shared-test-cluster-best-practices.md](docs/gradle-shared-test-cluster-best-practices.md)
- [docs/official-java-client.md](docs/official-java-client.md)
- [docs/run-opensearch.md](docs/run-opensearch.md)
- [docs/cloud-storage-mirrors.md](docs/cloud-storage-mirrors.md)
- [site/src/content/docs/tutorials/getting-started.md](site/src/content/docs/tutorials/getting-started.md)
- [site/src/content/docs/reference/api.md](site/src/content/docs/reference/api.md)

## Build and test

Unit tests always run. Process-backed integration tests require a distro ZIP or
download configuration. Embedded integration tests require extracted distro
homes.

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

Experimental embedded runners live under `experimental/embedded/`.
The `es-runner-embedded-9` and `es-runner-embedded-opensearch-3` modules
additionally require a Java 21 toolchain.

Embedded examples:

```powershell
$env:ES_EMBEDDED_HOME_8 = "C:\path\to\elasticsearch-8.19.11"
$env:ES_EMBEDDED_HOME_9 = "C:\path\to\elasticsearch-9.3.1"
$env:OPENSEARCH_EMBEDDED_HOME_2 = "C:\path\to\opensearch-2.19.4"
$env:OPENSEARCH_EMBEDDED_HOME_3 = "C:\path\to\opensearch-3.5.0"
.\gradlew.bat :es-runner-embedded-8:test :es-runner-embedded-9:test
.\gradlew.bat :es-runner-embedded-opensearch-2:test :es-runner-embedded-opensearch-3:test
```

## Public release readiness

This repo already has strong testing and docs, but a few items still need to be
finished before a first public Maven Central release:

- configure the repo secrets required by `.github/workflows/release.yml`
- claim the `io.github.wboult` Central namespace and create the first user token
- exercise the first live tag release end to end

See [docs/public-release-readiness.md](docs/public-release-readiness.md) for the
repo-specific checklist, and [docs/releasing.md](docs/releasing.md) for the
reproducible publication steps.

