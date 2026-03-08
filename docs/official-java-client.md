# Official Java API Client

ES Runner keeps its built-in `ElasticClient` intentionally small. If you want
the standard typed Java client for Elasticsearch APIs, use the optional
`es-runner-java-client` adapter module.

## Artifact

Published coordinate:

- `io.github.wboult:es-runner-java-client`

Inside this repo:

- `project(":es-runner-java-client")`

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

## What it gives you

- a managed wrapper around Elastic's official Java API Client
- access to the underlying low-level REST client if you need it
- one place to customize auth headers, request config, sockets, and mapper
- clean shutdown of the transport when your test or tool finishes

## Basic usage

```java
import io.github.wboult.esrunner.ElasticRunner;
import io.github.wboult.esrunner.ElasticServer;
import io.github.wboult.esrunner.javaclient.ElasticJavaClients;
import io.github.wboult.esrunner.javaclient.ManagedElasticsearchClient;
import java.nio.file.Paths;

try (ElasticServer server = ElasticRunner.start(Paths.get("elasticsearch-9.3.1-windows-x86_64.zip"));
     ManagedElasticsearchClient managed = ElasticJavaClients.create(server)) {
    var info = managed.client().info();
    System.out.println(info.clusterName());
    System.out.println(info.version().number());
}
```

## Customizing the client

`ElasticJavaClients.create(...)` accepts a builder callback for
`ElasticJavaClientConfig`.

The most useful hook is `customizeRestClientBuilder(...)`, which lets you
configure the underlying official low-level REST client builder.

Example with headers:

```java
import io.github.wboult.esrunner.javaclient.ElasticJavaClients;
import io.github.wboult.esrunner.javaclient.ManagedElasticsearchClient;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

try (ManagedElasticsearchClient managed = ElasticJavaClients.create(server, builder -> builder
        .customizeRestClientBuilder(restClient -> restClient.setDefaultHeaders(new Header[] {
            new BasicHeader("Authorization", "ApiKey test-key"),
            new BasicHeader("X-Request-Source", "es-runner")
        })))) {
    System.out.println(managed.client().info().version().number());
}
```

You can also replace the JSON mapper or apply transport options:

```java
import co.elastic.clients.json.jackson.JacksonJsonpMapper;

ManagedElasticsearchClient managed = ElasticJavaClients.create(server.baseUri(), builder -> builder
    .mapper(new JacksonJsonpMapper()));
```

## API shape

- `ElasticJavaClients.create(server)`
- `ElasticJavaClients.create(server, builder -> ...)`
- `ElasticJavaClients.create(baseUri)`
- `ElasticJavaClients.create(baseUri, builder -> ...)`

The returned `ManagedElasticsearchClient` exposes:

- `client()` for the official typed `ElasticsearchClient`
- `restClient()` for the low-level REST client
- `transport()` for the Java API Client transport

## When to use this instead of `ElasticClient`

Use `ElasticClient` when:

- you only need a few raw HTTP-style operations
- you want minimal dependencies
- you want a tiny API surface for tests

Use `es-runner-java-client` when:

- you want the standard typed Java client APIs
- you already use the official client elsewhere
- you need richer Elasticsearch API coverage than the built-in helper should own
