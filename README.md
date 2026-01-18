# Elastic Runner

Local Elasticsearch distribution runner intended for tests and offline tooling.
It launches an Elasticsearch ZIP distribution in a separate process, waits for
readiness, and provides a Java API for interaction.

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

- Functional-ish configuration: immutable config object with `withX` methods,
  plus a builder DSL for Java/Scala/Kotlin.
- External process: uses the official ES distribution, not embedded.
- API surface: simple HTTP helpers plus lifecycle control.

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
