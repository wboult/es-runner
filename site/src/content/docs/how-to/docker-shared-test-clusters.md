---
title: Use Docker shared test clusters
description: Reuse one Docker-backed Elasticsearch cluster across Gradle projects and suites.
sidebar:
  order: 11
---

ES Runner also includes a separate Docker/Testcontainers-backed shared-cluster
plugin for Gradle builds that already standardize on Docker.

This is a sibling backend to the process-backed shared-cluster plugin. It keeps
the same namespace helpers and injected test environment while switching the
cluster runtime from ZIP/processes to a shared Elasticsearch container.

If you are deciding between the two backends, start with
[Choose a shared-cluster backend](../choose-gradle-shared-cluster-backend/).

## When to use it

Use the Docker-backed plugin when:

- Docker is already a normal build dependency in your environment
- your team prefers image/tag configuration over ZIP distro management
- you want one shared Elasticsearch container per build across multiple suites
  and subprojects

Stay with the process-backed plugin when Docker is the wrong operational fit or
when you want the mainline ES Runner path with the broadest docs and options.

## Plugin pieces

- plugin id: `io.github.wboult.es-runner.docker-shared-test-clusters`
- test helper artifact: `io.github.wboult:es-runner-gradle-test-support`

The test-side API remains the same:

- `ElasticGradleTestEnv.fromSystemProperties()`
- `env.index(...)`
- `env.indexPattern(...)`
- `env.template(...)`
- `env.alias(...)`

## Canonical public sample

A checked-in public sample now lives at:

- `samples/docker-shared-cluster-multiproject-sample/`

It mirrors the structure of the process-backed sample, but swaps the shared
cluster runtime to Docker/Testcontainers while keeping the same namespace-aware
test-side API.

## Root build configuration

```groovy
plugins {
    id 'io.github.wboult.es-runner.docker-shared-test-clusters'
}

dockerElasticTestClusters {
    clusters {
        register("integration") {
            version.set("9.3.1")
            clusterName.set("shared-es-docker")
            startupTimeoutMillis.set(240_000L)
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

By default the plugin uses:

```text
docker.elastic.co/elasticsearch/elasticsearch:<version>
```

and applies local single-node defaults:

- `discovery.type=single-node`
- `xpack.security.enabled=false`
- `cluster.routing.allocation.disk.threshold_enabled=false`
- `ES_JAVA_OPTS=-Xms256m -Xmx256m`

## Subproject suites

```groovy
subprojects {
    apply plugin: 'java'

    testing {
        suites {
            withType(org.gradle.api.plugins.jvm.JvmTestSuite).configureEach {
                useJUnitJupiter()
                dependencies {
                    implementation("io.github.wboult:es-runner-gradle-test-support:${project.version}")
                }
            }

            register("integrationTest", org.gradle.api.plugins.jvm.JvmTestSuite)
            register("smokeTest", org.gradle.api.plugins.jvm.JvmTestSuite)
        }
    }
}
```

## Test-side usage

```java
ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
ElasticClient client = env.client();

String orders = env.index("orders");
String ordersPattern = env.indexPattern("orders");
String ordersTemplate = env.template("orders-template");
String newOrder = """
        {
          "status": "new"
        }
        """;

client.putIndexTemplate(ordersTemplate, """
        {
          "index_patterns": ["%s"]
        }
        """.formatted(ordersPattern));
client.createIndex(orders);
client.indexDocument(orders, newOrder);
client.refresh(orders);
```

## Scope and current limits

This first Docker-backed version currently focuses on:

- shared single-node Elasticsearch clusters
- build-scoped reuse across multiple suites and subprojects
- the same namespace model as the process-backed plugin

It does not yet aim for full process-backed option parity, and OpenSearch
Docker support is intentionally not included yet.

## Verification model

The real end-to-end Docker-backed TestKit flow is verified in a dedicated Linux
CI lane. Local Windows development can still build the module, but the
container-backed functional test is intentionally treated as Linux-first
coverage.

## Startup failure diagnostics

The Docker-backed plugin does not write a process-style
`startup-diagnostics.txt` file because there is no extracted distro work
directory to inspect. Instead, startup failures include an inline diagnostic
block in the exception output with:

- the cluster definition name and configured Elasticsearch cluster name
- the exact image reference
- startup timeout
- Docker/Testcontainers environment hints such as `DOCKER_HOST`
- container id, container name, host, and mapped ports when available
- sanitized container env vars
- a recent container log tail
- remediation hints for common cases such as daemon connectivity, image pull
  failures, and health-probe timeouts

So for Docker-backed failures, start with the exception message itself before
looking elsewhere.

## Related

- [Use shared Gradle test clusters](../gradle-shared-test-clusters/)
- [Troubleshooting](../troubleshooting/)
- [Build & test matrix](../../reference/build-test-matrix/)
