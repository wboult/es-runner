---
title: Official Java Client
description: Use ES Runner with Elastic's official Java API Client.
---

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

## Customize auth, headers, or timeouts

Use the config builder hook to customize the underlying official low-level REST
client builder.

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

`ManagedElasticsearchClient` exposes:

- `client()` for the official typed `ElasticsearchClient`
- `restClient()` for the low-level REST client
- `transport()` for the Java API Client transport

## Choose between clients

Use the built-in `ElasticClient` when you want minimal dependencies and only a
small set of raw HTTP-style operations.

Use `es-runner-java-client` when you want the standard typed Java client or
broader Elasticsearch API coverage.
