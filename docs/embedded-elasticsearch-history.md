# History of Embedded Elasticsearch and Its Deprecation

In the early days of Elasticsearch (ES 1.x and 2.x), it was possible to run
Elasticsearch in "embedded" mode. A Java application could start an ES node
inside the same JVM using classes like `NodeBuilder`:

```java
Node node = nodeBuilder().settings(...).node();
Client client = node.client();
```

This made integration tests and light-weight usage convenient. Over time,
Elastic discouraged and then removed embedded usage. By ES 5.x it was
deprecated, and by ES 6.x and 7.x it was effectively unsupported. The old
`TransportClient` was also deprecated and removed in favor of the REST client.

## Why embedded Elasticsearch was removed

The short version: safety, security, and stability.

Running ES in-process bypassed important safety checks and led to unpredictable
behavior. In a 2016 Elastic blog post, the team stated Elasticsearch should run
as a standalone server, not embedded. Key concerns included:

- Embedded nodes can disable the Java Security Manager sandbox.
- Jar-hell checks can be skipped, increasing classpath risk.
- Bootstrap checks (system settings validation) can be bypassed.
- Embedded usage is outside Elastic's supported test matrix.

Elastic decided they would not accept pull requests to support the embedded
use case. Community responses from Elastic engineers reinforced this stance:
embedded Elasticsearch is unsafe and not supported for production.

## Modern alternatives for testing and CI

With embedded mode gone, developers use external ES processes that are started
and managed by tools, not by embedding ES in the JVM.

### 1. Docker and Testcontainers (preferred)

Testcontainers (MIT) manages Elasticsearch Docker images for tests:

```java
@Container
ElasticsearchContainer esContainer =
    new ElasticsearchContainer(
        "docker.elastic.co/elasticsearch/elasticsearch:9.2.4");
```

Pros:
- Real ES distribution, production-like behavior
- Easy lifecycle management and logs
- Works in CI

Cons:
- Requires Docker
- Some startup overhead

### 2. Download and run a local ES distribution (no Docker)

Tools that download a ZIP or TAR and run ES as a separate process:

- **Allegro embedded-elasticsearch (Apache 2.0)**: downloads and runs ES
  out-of-process. Supports ES 1.x to 6.x, but archived and not updated for 7+.
- **Elasticsearch Maven Plugin (Apache 2.0)**:
  `alexcojocaru/elasticsearch-maven-plugin` downloads and runs ES during Maven
  integration tests. It supports ES 5.x through 8.x, and disables security by
  default for tests in newer versions.

Pros:
- No Docker dependency
- Uses official ES binaries
- Good for CI or offline environments

Cons:
- Tied to build tooling (Maven plugin) unless you write custom code
- Startup cost is higher than in-JVM mocks

### 3. In-JVM cluster runners (unsupported by Elastic)

Community tools try to recreate embedded behavior by running nodes in-process:

- **Codelibs Elasticsearch Cluster Runner (Apache 2.0)**: runs ES nodes in the
  JVM (up to ES 7.x). Useful for tests but not supported by Elastic.
- **OpenSearch Runner (Apache 2.0)**: similar, but for OpenSearch.

Pros:
- No external process to manage

Cons:
- Not supported for ES 8.x or 9.x
- Same risks as embedded mode (classpath, security, stability)

## Recommended approach for ES 9.x testing

1. **Use Testcontainers** when Docker is available.
2. **Use a download-and-run approach** when Docker is not available, such as
   the Elasticsearch Maven Plugin or a custom ProcessBuilder wrapper.
3. **Avoid in-JVM embedding** for ES 9.x. It is unsupported and fragile.

## Notes on licensing

- Testcontainers: MIT
- Allegro embedded-elasticsearch: Apache 2.0 (archived)
- Elasticsearch Maven Plugin: Apache 2.0
- Codelibs runners: Apache 2.0
- Elasticsearch 9.x: Elastic License (not Apache 2.0)

## Sources (high level)

- Elastic 2016 blog on deprecating embedded usage
- Elastic team forum posts confirming embedded nodes are unsupported
- Testcontainers and Elastic recommendations for integration tests
- Allegro embedded-elasticsearch and the Elasticsearch Maven Plugin docs
