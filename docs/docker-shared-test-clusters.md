# Docker shared test clusters

ES Runner now also ships a separate Gradle plugin for teams that already
standardize on Docker and want the same shared-cluster/namespacing model on top
of Testcontainers.

This is a sibling backend to the process-backed shared-cluster plugin, not a
replacement for it.

If you are deciding between the two backends, start with
[docs/choose-gradle-shared-cluster-backend.md](C:/Dev/elastic-runner/docs/choose-gradle-shared-cluster-backend.md).

## When to use it

Use the Docker-backed plugin when:

- your build agents already have Docker
- your team already thinks in image/tag terms instead of ZIP distros
- you want one shared Elasticsearch container per build across multiple suites
  and subprojects

Use the process-backed plugin when:

- Docker is unavailable or undesirable
- you want tighter control over distro ZIPs, mirrors, work dirs, or plugin
  installs
- you want the mainline ES Runner path with the broadest docs and coverage

## Plugin pieces

- Gradle plugin id: `io.github.wboult.es-runner.docker-shared-test-clusters`
- test helper artifact: `io.github.wboult:es-runner-gradle-test-support`

The helper API stays the same:

- `ElasticGradleTestEnv.fromSystemProperties()`
- `env.index(...)`
- `env.indexPattern(...)`
- `env.template(...)`
- `env.alias(...)`

So test code can usually stay unchanged while the backend changes.

## Canonical public sample

A checked-in public sample now lives at:

- `samples/docker-shared-cluster-multiproject-sample/`

It mirrors the structure of the process-backed sample, but swaps the shared
cluster runtime to Docker/Testcontainers while keeping the same namespace-aware
test-side API.

## Minimal root configuration

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

By default the Docker plugin uses the official Elasticsearch image for the
chosen version:

```text
docker.elastic.co/elasticsearch/elasticsearch:<version>
```

and sets these container defaults for local test builds:

- `discovery.type=single-node`
- `xpack.security.enabled=false`
- `cluster.routing.allocation.disk.threshold_enabled=false`
- `ES_JAVA_OPTS=-Xms256m -Xmx256m`

## Subproject test suites

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

client.putIndexTemplate(ordersTemplate, """
        {
          "index_patterns": ["%s"]
        }
        """.formatted(ordersPattern));
client.createIndex(orders);
client.indexDocument(orders, """
        {
          "status": "new"
        }
        """);
client.refresh(orders);
```

## Current scope

This Docker-backed plugin currently targets:

- shared single-node Elasticsearch clusters
- build-scoped reuse across projects and suites
- the same namespace model as the process-backed plugin

It does not yet try to mirror every process-backed option one for one.
OpenSearch Docker support is also intentionally out of scope for this first
version.

## Fresh-state semantics

The Docker-backed plugin reuses one Elasticsearch container across all bound
projects and suites within a single Gradle build.

Across separate Gradle invocations, it intentionally starts fresh again:

- one build invocation: shared container reused across bound suites/projects
- next build invocation: new container, no carried-over cluster state

That means this backend is optimized for build-scoped sharing, not persistent
local cluster reuse across reruns. If you want sticky local state between runs,
the process-backed shared-cluster plugin is the better fit today.

## Verification model

The Docker-backed functional test is treated as Linux CI coverage first.
Local Windows development can still build the plugin module, but the real
container-backed TestKit flow is verified in the dedicated Ubuntu CI lane.

## Startup failure diagnostics

The Docker-backed plugin does not write a process-style
`startup-diagnostics.txt` file because there is no extracted distro work
directory to inspect. Instead, startup failures now include an inline diagnostic
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

- [docs/gradle-shared-test-clusters.md](C:/Dev/elastic-runner/docs/gradle-shared-test-clusters.md)
- [docs/docker-shared-cluster-plan.md](C:/Dev/elastic-runner/docs/docker-shared-cluster-plan.md)
