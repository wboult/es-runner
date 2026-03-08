# Embedded JVM Elasticsearch Server (Experimental)

The `es-runner-embedded` subproject is an **experimental** attempt to run an
Elasticsearch node inside the current JVM process, without spawning a child
process.

> **Status:** exploratory / work-in-progress.  Verified target: ES 8.1.1.
> Later versions changed `PluginsService` in ways that break this approach —
> see [History and breakage](#history-and-breakage) below.

## Why run embedded?

- Zero process-spawn overhead — the node starts in milliseconds once the JVM
  is warm
- No OS-level process management, no PID files, no port-binding races
- The same JVM can call ES APIs and inspect internal state directly via
  `node.injector()` (Guice) rather than going through HTTP

## The unpublished-modules problem

Running ES in-process requires its standard modules (at a minimum
`analysis-common` for basic text analysis and `transport-netty4` for HTTP).
Elastic **does not publish** module jars like `analysis-common` to Maven
Central or their own Maven repository (`artifacts.elastic.co/maven/`).

### Solution: filesystem-modules approach

`Node(Environment)` — the public single-argument constructor — uses
`PluginsService` to load modules straight from the filesystem at startup.
If you point `path.home` at a real, extracted Elasticsearch distribution the
node loads every module in `esHome/modules/` automatically, including
`analysis-common`, `transport-netty4`, and `x-pack-core`, without any
classpath hacks.

```
esHome/
├── bin/
├── config/         ← ES reads its default config from here
├── lib/            ← ES server jars (loaded by PluginsService parent loader)
├── modules/
│   ├── analysis-common/   ← CommonAnalysisPlugin, loaded at runtime
│   ├── transport-netty4/  ← Netty4Plugin, loaded at runtime
│   ├── x-pack-core/
│   └── …
└── plugins/        ← third-party plugins, if any
```

### Alternative: classpath-plugins approach

`Node(Environment, Collection<Class<? extends Plugin>>, boolean)` — the
protected constructor used by Elasticsearch's own test framework
(`ESSingleNodeTestCase`) — lets you inject plugin classes directly from the
classpath.  This avoids the need for an extracted distribution but requires
every module class to be on the JVM classpath.  Because `analysis-common` is
not published to any Maven repository this approach is hard to use without
extracting the jar from a distribution zip first.

`EmbeddedNode` keeps a second constructor for future experimentation with this
approach.

## Running the tests

### Prerequisites

1. Download and extract an Elasticsearch **8.1.1** distribution:

   ```sh
   # Download
   curl -O https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-8.1.1-linux-x86_64.tar.gz
   # Extract
   tar xzf elasticsearch-8.1.1-linux-x86_64.tar.gz
   ```

2. Point `ES_EMBEDDED_HOME` at the extracted root:

   ```sh
   export ES_EMBEDDED_HOME=/path/to/elasticsearch-8.1.1
   ```

### Run

```sh
./gradlew :es-runner-embedded:test
```

Tests are **skipped** (not failed) when `ES_EMBEDDED_HOME` is not set, so
the normal CI build is not affected.

## JVM flags required

ES 8.x uses internal Java APIs that require explicit `--add-opens` grants on
Java 17.  These are set in `es-runner-embedded/build.gradle`'s test task and
mirror the flags in `config/jvm.options` inside the ES distribution.

Additionally, `-Des.distribution.type=integ_test_zip` is set to suppress
certain production-mode bootstrap checks (the same trick used by
`ESSingleNodeTestCase`).

## History and breakage

### What worked until ES 8.1.1

The filesystem-modules approach (plus appropriate `--add-opens` and security
settings) produces a functional embedded node on ES ≤ 8.1.1.  Standard
analysis, HTTP, transport, and basic CRUD all work.

### What broke after 8.1.1

In ES **8.2**, Elasticsearch began a multi-release migration of
`PluginsService` toward a stricter model:

| Change | Impact on embedding |
|---|---|
| `PluginsService` refactored to load plugins via `ExtendedPluginsClassLoader` | Module ClassLoader hierarchy becomes stricter; shared JVM classpath can conflict |
| Plugin descriptor validation tightened | Modules loaded from the filesystem may fail validation when the parent ClassLoader already has ES classes |
| `Node` public constructor marked as deprecated / behaviour changed | Classpath isolation assumptions break |
| `stable-plugin-api` SDK introduced (8.7+) | New plugin model incompatible with the classpath approach |

Investigating whether a patched approach (e.g. custom `ClassLoader` delegation,
alternative settings, or a shim plugin descriptor) can restore embedded
operation on ES 8.2+ is the next step on this branch.

## Architecture of this subproject

```
es-runner-embedded/
└── src/
    ├── main/java/io/github/wboult/esrunner/embedded/
    │   ├── EmbeddedNode.java           — package-private Node subclass
    │   └── EmbeddedElasticServer.java  — public lifecycle API (AutoCloseable)
    └── test/java/io/github/wboult/esrunner/embedded/
        └── EmbeddedElasticServerTest.java
```

### `EmbeddedNode`

Package-private subclass of `org.elasticsearch.node.Node`.  Its only purpose
is to expose the protected two-argument constructor without making the entire
`Node` hierarchy part of the public API.

### `EmbeddedElasticServer`

Public `AutoCloseable` that:

1. Builds a `Settings` object with safe embedded defaults
2. Calls `InternalSettingsPreparer.prepareEnvironment(...)` to produce the
   `Environment` that Node expects
3. Constructs an `EmbeddedNode` and calls `start()`
4. Retrieves the actual HTTP port from `HttpServerTransport.boundAddress()`
5. Exposes `baseUri()` and `httpPort()` so callers can send requests

On `close()` it calls `node.close()` which triggers ES's normal graceful
shutdown sequence.

## Known issues / next steps

- [ ] Verify startup succeeds on ES 8.1.1 (first run will reveal any missing
  `--add-opens` or unexpected bootstrap failures)
- [ ] Handle `SecurityManager` installation gracefully (ES 8.x installs one
  by default; Java 17 allows it but logs a deprecation warning)
- [ ] Investigate ES 8.2+ breakage and whether a custom `ClassLoader` approach
  can work
- [ ] Explore whether the `es-test-framework` (`org.elasticsearch.test:framework`)
  approach (`ESSingleNodeTestCase` subclassing) is still viable on later versions
- [ ] Consider a helper that auto-downloads and extracts the 8.1.1 distro
  (reusing `DistroDownloader` from the main project)
