# Cloud Storage Extension Plan

## Background

An earlier version of `elastic-runner` downloaded Elasticsearch distributions
from S3, GCS, and Azure Blob Storage by shelling out to external CLIs (`aws`,
`gcloud`, `gsutil`, `azcopy`, `az`). This was removed because:

- **Fragile runtime dependency.** CLI presence, version, and PATH configuration
  vary across machines, CI environments, and containers. The library cannot
  control or even detect these differences at compile time.
- **No programmatic credential management.** Credentials must be baked into
  the CLI environment rather than passed through the normal Java credential
  chain (instance metadata, environment variables, credential files, SDKs).
- **Untestable.** CLI-based download paths can only be exercised with live
  external processes; there is no seam for unit testing.
- **Invisible transitive dependencies.** CLI tools are not declared in the
  build file, so dependency management tools (Dependabot, Renovate) cannot
  track or update them.
- **Process hygiene.** Spawning subprocesses from a test-scoped library
  complicates cancellation, signal propagation, and timeout handling.

## Planned design

### Core extension point

`DistroDownloader` in the core library will expose a `DistroResolver` SPI
(Service Provider Interface). Third-party or first-party extension modules
register implementations via `java.util.ServiceLoader`.

```java
// com.elastic.runner.spi.DistroResolver  (in elastic-runner core)
public interface DistroResolver {

    /**
     * URI schemes this resolver handles (lower-case), e.g. {@code "s3"}.
     */
    Set<String> schemes();

    /**
     * Download the object at {@code uri} to the file {@code target}.
     * <p>
     * The caller guarantees that {@code target}'s parent directory exists.
     * Atomicity (write-then-rename) is the caller's responsibility; this
     * method should write directly to {@code target}.
     *
     * @throws IOException if the download fails
     */
    void download(URI uri, Path target) throws IOException;
}
```

`DistroDownloader.download()` will discover resolvers at startup:

```java
private static final Map<String, DistroResolver> RESOLVERS =
        ServiceLoader.load(DistroResolver.class).stream()
                .map(ServiceLoader.Provider::get)
                .flatMap(r -> r.schemes().stream().map(s -> Map.entry(s, r)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
```

Unrecognised schemes still throw `ElasticRunnerException` with a clear message.

### Planned extension modules

| Module | Maven artifact | SDK | Schemes |
|--------|---------------|-----|---------|
| `elastic-runner-s3` | `com.elastic:elastic-runner-s3` | `software.amazon.awssdk:s3` | `s3://` |
| `elastic-runner-gcs` | `com.elastic:elastic-runner-gcs` | `com.google.cloud:google-cloud-storage` | `gs://` |
| `elastic-runner-azure` | `com.elastic:elastic-runner-azure` | `com.azure:azure-storage-blob` | `az://` |

Each module declares the core library as a `compileOnly` dependency, so it
does not pull cloud SDK jars into projects that don't need them.

### Example: S3 module sketch

```java
// In elastic-runner-s3
// META-INF/services/com.elastic.runner.spi.DistroResolver
//   → com.elastic.runner.s3.S3DistroResolver

public final class S3DistroResolver implements DistroResolver {

    @Override
    public Set<String> schemes() {
        return Set.of("s3");
    }

    @Override
    public void download(URI uri, Path target) throws IOException {
        // Uses DefaultCredentialsProvider: env vars, ~/.aws/credentials,
        // EC2/ECS instance metadata, EKS IRSA — no CLI required.
        try (S3Client s3 = S3Client.create()) {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(uri.getHost())
                    .key(uri.getPath().replaceFirst("^/", ""))
                    .build();
            s3.getObject(request, target);
        } catch (SdkException e) {
            throw new IOException("S3 download failed: " + uri, e);
        }
    }
}
```

### Gradle plugin integration

The Gradle plugin (`ElasticClusterSpec`) already exposes `downloadBaseUrl` as
a plain string. No changes are needed to the plugin DSL; users add the
extension module as a dependency alongside the plugin and the `ServiceLoader`
picks it up automatically.

```groovy
// build.gradle
dependencies {
    elasticCluster("com.elastic:elastic-runner-s3:0.2.0")
}

elasticTestClusters {
    clusters {
        register("main") {
            version = "9.3.1"
            download = true
            downloadBaseUrl = "s3://my-artifact-bucket/elasticsearch/"
        }
    }
}
```

## URI conventions per provider

### S3 — `s3://`

```
s3://<bucket>/<key-prefix>/
```

Example:
```
s3://elastic-artifacts/snapshots/elasticsearch/
```

The resolver appends the distro filename (e.g.
`elasticsearch-9.3.1-linux-x86_64.tar.gz`) to the prefix.

### GCS — `gs://`

```
gs://<bucket>/<object-prefix>/
```

Example:
```
gs://elastic-artifacts/snapshots/elasticsearch/
```

### Azure Blob — `az://`

```
az://<account>/<container>/<blob-prefix>/
```

Example:
```
az://myaccount/artifacts/elasticsearch/
```

The resolver translates this to the Azure Blob SDK's account/container/blob
triple. Authentication uses `DefaultAzureCredentialBuilder` (managed identity,
environment variables, `az login`, etc.).

## Workarounds until modules are available

All three providers support HTTPS pre-signed / SAS URLs. See
[cloud-storage-mirrors.md](cloud-storage-mirrors.md) for instructions on
generating them.
