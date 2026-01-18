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

Pre-downloaded distro reused for multiple servers (Java):

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

Path sharedDistroZip = Paths.get("elasticsearch-9.2.4-linux-x86_64.zip"); // pre-downloaded once

List<ElasticServer> servers = new ArrayList<>();
List<Path> tempDirs = new ArrayList<>();

for (int i = 0; i < 2; i++) {
    Path tempDir = Files.createTempDirectory("es-runner-" + i);
    tempDirs.add(tempDir);
    Path isolatedZip = tempDir.resolve(sharedDistroZip.getFileName());
    Files.copy(sharedDistroZip, isolatedZip);

    ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
        .distroZip(isolatedZip)
        .workDir(tempDir.resolve("work"))
        .clusterName("example-multi-" + i)
        .setting("discovery.type", "single-node"));

    servers.add(ElasticRunner.start(config));
}

try {
    for (ElasticServer server : servers) {
        System.out.println(server.baseUri());
    }
} finally {
    for (ElasticServer server : servers) {
        server.close();
    }
    for (Path tempDir : tempDirs) {
        tempDir.toFile().deleteOnExit();
    }
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
