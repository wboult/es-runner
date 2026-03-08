# Elastic Runner

The robust, zero-friction local Elasticsearch distribution runner tailored for integration tests and offline tooling. 
Perfect for CI/CD pipelines without Docker-in-Docker capabilities, bare-metal build nodes, or environments where Testcontainers isn't viable.

It launches a real Elasticsearch ZIP distribution in an isolated OS process, auto-allocates HTTP ports safely, ensures process cleanup via JVM shutdown hooks, and provides a polished, strongly-typed Java API for readiness checks and cluster interaction.

Documentation: https://wboult.github.io/elastic-runner/

## Requirements

- JDK 17+
- An Elasticsearch distribution ZIP (for example `elasticsearch-9.2.4.zip`)

## Quick start

```java
Path distroZip = Paths.get("elasticsearch-9.2.4-windows-x86_64.zip");
Path workDir = Paths.get(".es");

ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .distroZip(distroZip)
    .workDir(workDir)
    .clusterName("local-es")
    .setting("xpack.security.enabled", "false")
    .setting("discovery.type", "single-node"));

try (ElasticServer server = ElasticRunner.start(config)) {
    System.out.println(server.baseUri());
    System.out.println(server.clusterHealth());
    System.out.println(server.version());
}
```

## Lifecycle helpers

Use `withServer` for a functional, auto-closing style:

```java
ElasticRunner.withServer(builder -> builder
    .version("9.2.4")
    .download(true), server -> {
        System.out.println(server.clusterHealth());
        System.out.println(server.version());
    });
```

If you need explicit shutdown details:

```java
ElasticServer server = ElasticRunner.start(Paths.get("elasticsearch-9.2.4-windows-x86_64.zip"));
StopResult result = server.stopWithResult();
System.out.println("graceful=" + result.graceful());
System.out.println(server.logTail());
```

## Examples

Java:

```java
Path workDir = Paths.get(".es");
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.2.4")
    .download(true)
    .workDir(workDir)
    .clusterName("example-java"));

try (ElasticServer server = ElasticRunner.start(config)) {
    ElasticClient client = server.client();
    System.out.println(client.clusterHealth());
    System.out.println(client.version());
}
```

Scala:

```scala
import java.nio.file.Paths

val workDir = Paths.get(".es")
val config = ElasticRunnerConfig.from(builder =>
  builder
    .version("9.2.4")
    .download(true)
    .workDir(workDir)
    .clusterName("example-scala")
)

val server = ElasticRunner.start(config)
try {
  val client = server.client()
  println(client.clusterHealth())
  println(client.version())
} finally {
  server.close()
}
```

Kotlin:

```kotlin
import java.nio.file.Paths

val workDir = Paths.get(".es")
val config = ElasticRunnerConfig.from { builder ->
    builder
        .version("9.2.4")
        .download(true)
        .workDir(workDir)
        .clusterName("example-kotlin")
}

ElasticRunner.start(config).use { server ->
    val client = server.client()
    println(client.clusterHealth())
    println(client.version())
}
```

Programmatic download + two-node cluster (Java):

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

String version = "9.2.4";
Path distrosDir = Paths.get("distros");
Path sharedZip = ElasticRunner.resolveDistroZip(
    ElasticRunnerConfig.from(builder -> builder
        .version(version)
        .download(true)
        .distrosDir(distrosDir))
);

String clusterName = "example-cluster";
String masterNodes = "[\"node-0\",\"node-1\"]";
String seedHosts = "[\"127.0.0.1:9300\",\"127.0.0.1:9301\"]";

ElasticServer node0 = startNode(sharedZip, clusterName, "node-0", 9200, 9300, masterNodes, seedHosts);
ElasticServer node1 = startNode(sharedZip, clusterName, "node-1", 9201, 9301, masterNodes, seedHosts);

try {
    System.out.println(node0.client().clusterHealth());
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

## Design

- **Builder configuration**: Pure builder architecture designed for maximum IDE discoverability and API clarity, with built-in Kotlin/Scala DSLs.
- **External process**: Launches the official ES distribution in a separate, fully-isolated process (not embedded), preventing classpath pollution.
- **API surface**: Intuitive HTTP helpers, strongly-typed responses, and reliable lifecycle control using JVM shutdown hooks to prevent orphaned processes.

## Gradle Plugin Integration (Incubating)

For projects using Gradle, we provide a zero-config test setup plugin:

```kotlin
plugins {
    id("com.elastic.runner") version "0.1.0"
}
```

This plugin automatically handles downloading Elasticsearch, managing the process lifecycle around your tests, and exposing the cluster URI to your `Test` tasks via system properties.

## Official distro downloads

If you only set a version, the runner will download from the official Elastic
distribution URL:

```
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.2.4")
    .download(true));
```

Use `download(true)` to force a re-download even if the archive is present.
You can override the base URL with `downloadBaseUrl(...)` if you use a mirror.

Supported `downloadBaseUrl(...)` forms:

- `https://mirror.example.com/elasticsearch/`
- `file:///mnt/elasticsearch-mirror/`
- `s3://my-bucket/elasticsearch/`
- `gs://my-bucket/elasticsearch/`
- `az://myaccount/mycontainer/elasticsearch/`

Common production mirror options:

- Plain `https://` mirrors, including Artifactory/Nexus/proxy endpoints
- Shared filesystem mirrors via `file://`
- Signed HTTPS container URLs, such as Azure Blob SAS container URLs
- Cloud storage URIs backed by installed CLIs for private buckets/containers

Examples:

```java
ElasticRunnerConfig s3Config = ElasticRunnerConfig.from(builder -> builder
    .version("9.2.4")
    .download(true)
    .downloadBaseUrl("s3://elastic-mirror/elasticsearch/"));

ElasticRunnerConfig gcsConfig = ElasticRunnerConfig.from(builder -> builder
    .version("9.2.4")
    .download(true)
    .downloadBaseUrl("gs://elastic-mirror/elasticsearch/"));

ElasticRunnerConfig azureConfig = ElasticRunnerConfig.from(builder -> builder
    .version("9.2.4")
    .download(true)
    .downloadBaseUrl("az://myaccount/releases/elasticsearch/"));

ElasticRunnerConfig fileMirrorConfig = ElasticRunnerConfig.from(builder -> builder
    .version("9.2.4")
    .download(true)
    .downloadBaseUrl("file:///srv/mirrors/elasticsearch/"));
```

### Access configuration

`https://` and `file://` downloads do not need extra tooling. For private cloud
storage mirrors:

- `s3://`: install the AWS CLI (`aws`). Elastic Runner uses `aws s3 cp`.
  Configure access with standard AWS settings such as `AWS_PROFILE`,
  `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`,
  `AWS_SESSION_TOKEN`, or an attached IAM role.
- `gs://`: install Google Cloud CLI (`gcloud`) or `gsutil`. Elastic Runner
  tries `gcloud storage cp` first, then `gsutil cp`. Authenticate with
  `gcloud auth login` for user credentials or
  `gcloud auth activate-service-account --key-file=...` for service accounts.
- `az://`: install `azcopy` (preferred) or Azure CLI (`az`). Elastic Runner
  tries `azcopy copy` first, then `az storage blob download`.
  Use `azcopy login`, `azcopy login --identity`, or `az login` for RBAC-based
  access. Azure CLI fallback also works with
  `AZURE_STORAGE_CONNECTION_STRING`, `AZURE_STORAGE_KEY`, or
  `AZURE_STORAGE_SAS_TOKEN`.

If you already have an HTTPS blob/container URL with a shared SAS query, use
that directly with `downloadBaseUrl(...)`; the runner preserves the query when
it appends the distro filename.

## Gradle shared test clusters

Elastic Runner also includes a Gradle plugin for build-scoped shared
Elasticsearch test clusters:

- plugin id: `com.elastic.runner.shared-test-clusters`
- companion test helper artifact: `com.elastic:elastic-runner-gradle-test-support`

The intended model is:

- define a small number of clusters once in the root build
- bind suites such as `integrationTest` to those clusters
- reuse one Elasticsearch process across projects during the build
- inject a per-suite namespace so parallel suites do not collide

Root build example:

```groovy
plugins {
    id 'com.elastic.runner.shared-test-clusters'
}

elasticTestClusters {
    clusters {
        register("integration") {
            version.set("9.2.4")
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

subprojects {
    apply plugin: 'java'

    testing {
        suites {
            register("integrationTest", org.gradle.api.plugins.jvm.JvmTestSuite) {
                useJUnitJupiter()
            }
        }
    }
}
```

Test-side helper example:

```java
ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
ElasticClient client = env.client();
String ordersIndex = env.index("orders");

client.createIndex(ordersIndex);
client.indexDocument(ordersIndex, "1", "{\"status\":\"new\"}");
client.refresh(ordersIndex);
```

Injected system properties:

- `elastic.runner.baseUri`
- `elastic.runner.httpPort`
- `elastic.runner.clusterName`
- `elastic.runner.buildId`
- `elastic.runner.suiteId`
- `elastic.runner.namespace`
- `elastic.runner.resourcePrefix`

See [docs/gradle-shared-test-clusters.md](docs/gradle-shared-test-clusters.md)
for the usage guide and
[docs/gradle-shared-cluster-plugin-design.md](docs/gradle-shared-cluster-plugin-design.md)
for the design notes.

## Tests

Unit tests always run. Integration tests require:

```
ES_DISTRO_ZIP=C:\path\to\elasticsearch-9.2.4.zip
```

Run tests with the Gradle wrapper:

```
.\gradlew test
```

If the system JDK is not compatible, the wrapper uses the pinned JDK 17 in
`.jdks/` via `gradle.properties`.
