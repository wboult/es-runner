---
title: Use shared Gradle test clusters
description: Reuse one Elasticsearch process across Gradle projects and suites with suite-level namespaces.
---

# Use shared Gradle test clusters

Elastic Runner includes a Gradle plugin for build-scoped shared Elasticsearch
clusters.

Use it when you want:

- one Elasticsearch process per build instead of per test JVM
- multiple Gradle projects to share the same cluster
- suite-level namespaces so parallel suites do not collide

## Apply the plugin in the root build

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
```

## Define the suites in subprojects

```groovy
subprojects {
    apply plugin: 'java'

    dependencies {
        testImplementation "com.elastic:elastic-runner-gradle-test-support:${project.version}"
    }

    testing {
        suites {
            register("integrationTest", org.gradle.api.plugins.jvm.JvmTestSuite) {
                useJUnitJupiter()
            }
        }
    }
}
```

If you are using this from the Elastic Runner source tree itself, depend on
`project(":elastic-runner-gradle-test-support")` instead of published
coordinates.

## Use the injected test environment

```java
import com.elastic.runner.ElasticClient;
import com.elastic.runner.gradle.testsupport.ElasticGradleTestEnv;

ElasticGradleTestEnv env = ElasticGradleTestEnv.fromSystemProperties();
ElasticClient client = env.client();

String orders = env.index("orders");
client.createIndex(orders);
client.indexDocument(orders, "1", "{\"status\":\"new\"}");
client.refresh(orders);
```

The helper prefixes logical resource names with the suite namespace.

## What gets injected

Bound test tasks receive these system properties:

- `elastic.runner.baseUri`
- `elastic.runner.httpPort`
- `elastic.runner.clusterName`
- `elastic.runner.buildId`
- `elastic.runner.suiteId`
- `elastic.runner.namespace`
- `elastic.runner.resourcePrefix`

## Namespace modes

- `SUITE`: one namespace per suite task. Recommended.
- `PROJECT`: one namespace per project.

With `SUITE`, tests within the same suite can share state, but parallel suites
stay isolated from each other.

## Configure mirrors and private distros

Cluster definitions accept the same distro settings as the core library:

- `distroZip`
- `version`
- `download`
- `downloadBaseUrl`
- `distrosDir`

That means you can point shared Gradle clusters at:

- official Elastic downloads
- `file://` mirrors
- `s3://`, `gs://`, or `az://` mirrors
- signed HTTPS URLs

See [Cloud storage mirrors](../cloud-storage-mirrors/) for access setup.

## Related

- [Gradle shared cluster plugin design](../../explanation/gradle-shared-cluster-plugin-design/)
- [Configuration reference](../../reference/configuration/)
