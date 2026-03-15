# Docker/Testcontainers Shared-Cluster Plugin Plan

## Background

The process-backed plugin is the right default: no Docker prerequisite, works on
locked-down CI, direct control over distros/plugins/logs. But there is a valid
second use case for teams already standardised on Docker/Testcontainers, where
container lifecycle is more familiar than ZIP/process lifecycle.

The opportunity is not "replace the current plugin with Testcontainers." It is:
keep process-backed as primary, add Docker-backed as an alternative that reuses
the same higher-level model (shared cluster, namespace isolation, suite binding,
connection injection).

**Benchmark evidence reinforces this.** A side-by-side benchmark showed Docker
workload performance is roughly 3x slower than native process (16.5 s vs 5.2 s
for identical index/search operations on ES 9.3.1), with the gap coming from
container virtualisation overhead on every ES API call. The process backend will
remain the faster and recommended path for most users.

What Testcontainers already provides:
- single-node defaults, security/TLS handling, container customisation hooks,
  standard container lifecycle management.

What it does not provide (and this plugin would add):
- one build-scoped shared cluster across suites/projects
- namespaced indices/templates/aliases
- Gradle suite binding
- test JVM connection injection
- a consistent shared-cluster developer workflow

---

## Recommended Shape

**Two distinct plugins, shared core.** Not one plugin with two modes.

The DSL shapes are fundamentally different: `version`/`distroZip`/`mirrors`/
`plugins`/`workDir` vs `image`/`envVars`/`mountHooks`/`reuseFlags`. A single
plugin with a `mode` property would need to validate invalid combinations
("you set `image` but mode is `process`"), which is messy and confusing.

---

## What Is Backend-Agnostic vs Backend-Specific

Looking at the actual code, the split is clean.

### Backend-agnostic (shareable)

| Class                              | Role                                                        |
|------------------------------------|-------------------------------------------------------------|
| `ElasticClusterMetadata`           | Record: baseUri, httpPort, clusterName                      |
| `ElasticNamespace`                 | Derives namespace prefix from buildId + project + task + mode |
| `NamespaceMode`                    | Enum: SUITE or PROJECT                                      |
| `ElasticSuiteBinding`             | DSL model: one suite -> one cluster, plus namespace mode    |
| `ElasticSuiteBindings`            | Fluent container wrapper: `matchingName()`, `register()`    |
| `ElasticClusterJvmArgumentProvider`| Injects 7 system properties into test JVM                   |
| `ElasticGradleTestEnv`            | Test-side: reads system props, creates ElasticClient, namespaces resources |

### Backend-specific (not shareable)

| Class                              | Role                                                        |
|------------------------------------|-------------------------------------------------------------|
| `ElasticClusterSpec`               | 16 Gradle Properties, all process-specific                  |
| `ElasticClusterService`           | BuildService wrapping ElasticServer lifecycle                |
| `ElasticSharedTestClustersPlugin` | Plugin entry point, registers service + wires tasks          |

---

## Backend SPI

Looking at how `ElasticClusterJvmArgumentProvider` actually consumes the service,
the SPI is extremely thin. It calls `service.get().metadata()` to get connection
info, and `close()` happens via BuildService disposal. The entire contract:

```java
public interface SharedClusterBackend extends BuildService<...>, AutoCloseable {
    /** Lazy-start trigger. First call boots the cluster; subsequent calls return cached info. */
    ElasticClusterMetadata metadata();
}
```

"Start" is implicit in `metadata()` (lazy initialisation). "Stop" is implicit in
`close()` (build service disposal). Diagnostics are an implementation concern
(logging), not an SPI method. Keeping the SPI this thin prevents the abstraction
from leaking backend details.

---

## Module Structure

| Module                                | Artifact ID                       | Contents                                                                   |
|---------------------------------------|-----------------------------------|----------------------------------------------------------------------------|
| `elastic-runner`                      | `es-runner`                       | Core library (unchanged)                                                   |
| `elastic-runner-gradle-core`          | `es-runner-gradle-core`           | **NEW** — shared abstractions: Metadata, Namespace, NamespaceMode, SuiteBinding, SuiteBindings, JvmArgumentProvider, backend SPI interface |
| `elastic-runner-gradle-plugin`        | `es-runner-gradle-plugin`         | Process-backed plugin (depends on gradle-core + elastic-runner)            |
| `elastic-runner-gradle-plugin-docker` | `es-runner-gradle-plugin-docker`  | **NEW** — Docker-backed plugin (depends on gradle-core + Testcontainers, does NOT depend on elastic-runner core) |
| `elastic-runner-gradle-test-support`  | `es-runner-gradle-test-support`   | Test-side helpers (unchanged, depends on elastic-runner for ElasticClient)  |

**Key dependency principle:** The Docker plugin does NOT pull in the
`elastic-runner` core library. It only needs `gradle-core` (for the shared model)
and Testcontainers (for the backend). Test code uses `ElasticClient` via
`elastic-runner-gradle-test-support`, which is backend-agnostic — it just reads
system properties.

**Plugin IDs:**
- Process: `io.github.wboult.es-runner.shared-test-clusters` (unchanged)
- Docker: `io.github.wboult.es-runner.docker-shared-test-clusters`

---

## Extension/Spec Design

This is the hardest part of the extraction. The current
`ElasticTestClustersExtension` has
`getClusters(): NamedDomainObjectContainer<ElasticClusterSpec>`, which is typed
to the concrete process-specific spec. You cannot reuse this extension for
Docker because the spec type is different.

**Recommended approach:** Each plugin creates its own extension class with its
own spec type, but shares the suite-binding logic.

```
ProcessPlugin -> ProcessExtension -> NamedDomainObjectContainer<ProcessClusterSpec>
                                  -> ElasticSuiteBindings (shared)

DockerPlugin  -> DockerExtension  -> NamedDomainObjectContainer<DockerClusterSpec>
                                  -> ElasticSuiteBindings (shared)
```

`ElasticSuiteBindings` / `ElasticSuiteBinding` move into the shared core. Each
plugin's extension owns its cluster specs but delegates to the shared
suite-binding model. `ElasticClusterJvmArgumentProvider` stays shared — it just
needs a `Provider<SharedClusterBackend>` and does not care which backend.

---

## Phases

### Phase 1 — Extract shared core + SPI

- Create `elastic-runner-gradle-core` subproject.
- Move into it: `ElasticClusterMetadata`, `ElasticNamespace`, `NamespaceMode`,
  `ElasticSuiteBinding`, `ElasticSuiteBindings`,
  `ElasticClusterJvmArgumentProvider`.
- Define the `SharedClusterBackend` SPI interface (just `metadata()` +
  `AutoCloseable`).
- Add unit tests for namespace calculation.

**Goal:** A real module with real tests, not speculative abstraction.

### Phase 2 — Migrate process-backed plugin onto shared core

- `elastic-runner-gradle-plugin` gains a dependency on `gradle-core`.
- `ElasticClusterService` implements `SharedClusterBackend`.
- `ElasticClusterSpec` stays in the process plugin (it is process-specific).
- `ElasticSharedTestClustersPlugin` uses `ElasticClusterJvmArgumentProvider`
  from the shared core.
- All existing functional tests must still pass identically.

**Goal:** Prove the shared layer works by running existing tests against it.
Zero DSL changes for consumers.

### Phase 3 — Docker-backed plugin MVP

- New `elastic-runner-gradle-plugin-docker` subproject.
- `DockerClusterSpec` — Gradle Properties for Docker-specific config:
  - `image: Property<String>` (default:
    `docker.elastic.co/elasticsearch/elasticsearch:{version}`)
  - `version: Property<String>` (convenience, used to derive default image)
  - `envVars: MapProperty<String, String>` (default: discovery.type=single-node,
    security off, 256 m heap)
  - `startupTimeoutMillis: Property<Long>`
  - `clusterName: Property<String>`
- `DockerClusterService` — BuildService wrapping `ElasticsearchContainer`
  lifecycle.
- `DockerSharedTestClustersPlugin` — plugin entry point, same pattern as
  process plugin.
- Functional tests using GradleTestKit (require Docker in test environment).

**Explicit non-goals for Phase 3:**
- Plugin install parity (Docker uses pre-built images).
- Config symmetry (Docker config is intentionally different).
- Container reuse across builds (Testcontainers `withReuse()` — defer).
- OpenSearch support (defer to Phase 6).

### Phase 4 — Docs and example

- Add a "choosing your backend" how-to page:
  - When to use process-backed (default, no Docker needed, faster workload).
  - When to use Docker-backed (team already on Docker, image-based
    customisation).
- One canonical Gradle example for each backend.
- Note: test code is identical between backends (same `ElasticGradleTestEnv`
  API).

### Phase 5 — CI smoke coverage

- One Linux-only Docker-backed smoke lane.
- Keep it narrow — just verify the Docker plugin starts a container and injects
  properties.
- Do not add it to the main matrix (would require Docker in all CI runners).

### Phase 6 — Evaluate OpenSearch Docker support

- The core library already supports OpenSearch.
- Add `DockerClusterSpec.family` property (ELASTICSEARCH | OPENSEARCH).
- Different default image per family.
- Only after the Elasticsearch Docker path is stable.

---

## Fresh-State Semantics per Backend

- **Process-backed:** Current work-dir/state cleanup model (delete + recreate
  per build service lifecycle).
- **Docker-backed:** Fresh container per build service lifecycle (container
  created on first `metadata()` call, destroyed on `close()`). Testcontainers
  `withReuse(true)` is a future opt-in, not Phase 3.

---

## Parity Boundaries

### Initial parity target
- Shared cluster across multiple suites/projects.
- Namespace-safe resource helpers.
- Connection injection.
- Sensible diagnostics.

### Explicit non-goals for the first version
- Full plugin-install parity.
- Exact config symmetry.
- Identical startup/failure semantics.

---

## Risk: Testcontainers Version Compatibility

Docker Engine 29.x (shipped with Docker Desktop 4.52+) breaks Testcontainers
1.x. Testcontainers 2.0+ is required. The Docker plugin should:

- Depend on Testcontainers 2.0+ from the start.
- Document the minimum Docker Engine version.
- Include Docker connectivity diagnostics in startup failure messages
  (image/tag, container ID, mapped ports, recent container logs, daemon
  connectivity errors).

---

## Diagnostics (Docker Backend)

Docker-backed diagnostics should include from the start:
- Image and tag.
- Container ID and name.
- Mapped ports.
- Recent container logs on failure.
- Docker daemon/connectivity failure details.

**Goal:** Keep the usability bar close to the process-backed path.

---

## Success Criteria

1. A downstream Gradle build can use the Docker plugin to share one
   Elasticsearch container across multiple suites/projects.
2. Test code uses the same `ElasticGradleTestEnv` API with no changes.
3. Namespace helpers (`index()`, `template()`, `alias()`, etc.) work
   identically.
4. The docs clearly explain when to use which backend, with one complete
   example each.
5. The process-backed plugin remains cleaner after the shared-core extraction
   (fewer classes, clearer responsibilities).
6. The Docker plugin's functional tests prove: shared container across suites,
   correct namespacing, no container start when no bound test task runs,
   fresh-state between builds.
7. `elastic-runner-gradle-test-support` module is unchanged — zero
   modifications needed, proving the abstraction boundary is in the right
   place.

---

## Phase Summary

| Phase | Deliverable                             | Validates                                           |
|-------|-----------------------------------------|-----------------------------------------------------|
| 1     | `elastic-runner-gradle-core` module     | Shared abstractions compile and have tests           |
| 2     | Process plugin migrated onto core       | Existing functional tests pass, zero DSL changes     |
| 3     | `elastic-runner-gradle-plugin-docker` MVP | Docker backend works end-to-end                    |
| 4     | Docs: "choosing your backend" page      | New users can wire it up from docs alone             |
| 5     | CI: one Docker smoke lane               | Validates in CI without exploding the matrix         |
| 6     | OpenSearch Docker                       | Extends model to second distro family                |

---

## Recommendation

If this becomes real work, the right first step is not "build the Docker
plugin." It is:

1. Extract the shared core cleanly.
2. Re-home the existing process plugin onto it.
3. Then add the Docker backend.

That gives you a better architecture even if the Docker path later proves not
worth shipping.
