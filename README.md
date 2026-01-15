# Elastic Runner

Local Elasticsearch distribution runner intended for tests and offline tooling.
It launches an Elasticsearch ZIP distribution in a separate process, waits for
readiness, and provides a Java API for interaction.

## Requirements

- JDK 17+
- An Elasticsearch distribution ZIP (for example `elasticsearch-9.2.4.zip`)

## Quick start

```java
Path distroZip = Paths.get("distros/elasticsearch-9.2.4-windows-x86_64.zip");
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

## Design

- Functional-ish configuration: immutable config object with `withX` methods,
  plus a builder DSL for Java/Scala/Kotlin.
- External process: uses the official ES distribution, not embedded.
- API surface: simple HTTP helpers plus lifecycle control.

## Official distro downloads

If you only set a version, the runner will look in `distros/` first and then
download from the official Elastic distribution URL:

```
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.2.4")
    .distrosDir(Paths.get("distros"))
    .download(false));
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
