# Embedded JVM Search Servers (Experimental)

The embedded runner is now split across five Gradle subprojects:

- `es-runner-embedded-common`
- `es-runner-embedded-8`
- `es-runner-embedded-9`
- `es-runner-embedded-opensearch-2`
- `es-runner-embedded-opensearch-3`

The versioned modules are experimental attempts to run Elasticsearch or
OpenSearch inside the current JVM process, without spawning a child process.

> Status: experimentally working on ES `8.19.11`, ES `9.3.1`, OpenSearch
> `2.19.4`, and OpenSearch `3.5.0` for a simple local profile. All embedded
> variants expose the shared `ElasticServerHandle` HTTP/query API and support
> repeated start/stop inside the same JVM by resetting a small amount of
> process-global logging state between runs. Elasticsearch and OpenSearch 3
> still stage distro modules from the extracted home; OpenSearch 2 can use
> published classpath plugins for its minimal transport + analysis path.

## Why run embedded?

- Zero process-spawn overhead once the JVM is warm
- No OS-level process management or PID files
- The same JVM can call ES APIs and inspect internal state directly via
  `node.injector()` rather than going through HTTP alone

## Core idea

Running these servers in-process still needs transport, analysis, and a small
amount of bootstrap infrastructure.

- Elasticsearch 8/9 still rely on standard shipped modules such as
  `analysis-common` and `transport-netty4`, which Elastic does not publish as a
  separate Maven-friendly embedded set.
- OpenSearch 2 publishes the minimal transport and analysis plugins we need.
- OpenSearch 3 publishes analysis support, but still expects the distro
  `transport-netty4` module at runtime.

ES Runner therefore stages an embedded home under the configured `workDir` and
points `path.home` at that staged copy:

```text
workDir/
|-- data/
|-- home/
|   |-- config/
|   |-- lib/
|   |-- modules/
|   |   |-- analysis-common/
|   |   |-- transport-netty4/
|   |   `-- ...
|   `-- plugins/
`-- logs/
```

The bootstrap then:

1. prepares an `Environment`
2. bootstraps minimal logging once per JVM
3. loads the required modules/plugins for that version family
4. starts the embedded node

## Why the versioned modules are split

Keeping one embedded module per major line is cleaner because the startup model
already differs in meaningful ways:

- `es-runner-embedded-8`
  - targets Elasticsearch `8.19.11`
  - uses Java 17
- `es-runner-embedded-9`
  - targets Elasticsearch `9.3.1`
  - uses Java 21
- `es-runner-embedded-opensearch-2`
  - targets OpenSearch `2.19.4`
  - uses Java 17
  - loads published classpath plugins:
    - `org.opensearch.transport.Netty4Plugin`
    - `org.opensearch.analysis.common.CommonAnalysisPlugin`
- `es-runner-embedded-opensearch-3`
  - targets OpenSearch `3.5.0`
  - uses Java 21
  - stages the distro `transport-netty4` module
  - loads published classpath plugins:
    - `org.opensearch.analysis.common.CommonAnalysisModulePlugin`
    - support classpath from `org.opensearch:opensearch-plugin-classloader`

The Elasticsearch runtime strategy is still the same in both ES modules:

- stage a smaller embedded home under `workDir/home`
- load built-in modules from the real extracted distro
- use a bundled profile file to decide which modules are included
- strip `modulename=` from staged descriptors
- reset process-global ES logging state between runs

The current Elasticsearch embedded profile works around that by:

- compiling and running `es-runner-embedded-8` on Java 17
- compiling and running `es-runner-embedded-9` on Java 21
- omitting `x-pack-ml`
  - it still has a native controller and fails in this shared JVM setup
- omitting `x-pack-esql`
  - it depends on `x-pack-ml`
- omitting `x-pack-security`
  - keeps the embedded node unauthenticated and reachable over plain HTTP
- stripping `modulename=` from staged `plugin-descriptor.properties`
  - forces classloader-based loading instead of Java module-layer loading
- resetting specific ES logging globals after shutdown
  - allows repeated embedded start/stop in one test JVM

## Public API shape

Embedded mode now mirrors the external-process runner more closely:

- `EmbeddedElasticServerConfig`
  - builder-backed immutable config
  - `esHome`, `workDir`, `clusterName`, `nodeName`, `httpPort`, port range,
    startup/shutdown timeouts, extra settings, and included module set
- `io.github.wboult.esrunner.embedded.v8.EmbeddedElasticServer`
- `io.github.wboult.esrunner.embedded.v9.EmbeddedElasticServer`
- `io.github.wboult.esrunner.embedded.opensearch.v2.EmbeddedOpenSearchServer`
- `io.github.wboult.esrunner.embedded.opensearch.v3.EmbeddedOpenSearchServer`
- versioned `start(...)` and `withServer(...)` entry points
- a shared `EmbeddedModuleProfile` bundled as module resources
- the shared `ElasticServerHandle` interface

Each versioned runner exposes:

- `profile()`
- `defaultConfig(Path esHome)`
- `start(...)`
- `withServer(...)`

and implements the shared
  `io.github.wboult.esrunner.ElasticServerHandle` interface

## Choosing which built-in modules/plugins load

The embedded runners now let callers override both:

- the built-in distro modules staged into the embedded home
- the classpath plugins loaded directly into the embedded node

Elasticsearch `8`/`9` mainly use bundled module profiles.
OpenSearch `2` mainly uses classpath plugins.
OpenSearch `3` uses a mixed model: staged transport module plus classpath
analysis plugin.

The usual pattern is:

1. start from `defaultConfig(esHome)` or `useBundledProfile(profile())`
2. remove the built-in modules you do not need
3. start the server

Example:

```java
import io.github.wboult.esrunner.embedded.EmbeddedElasticServerConfig;
import io.github.wboult.esrunner.embedded.v9.EmbeddedElasticServer;

Path esHome = Paths.get("distros/embedded/elasticsearch-9.3.1");

EmbeddedElasticServerConfig slim = EmbeddedElasticServer.defaultConfig(esHome)
    .toBuilder()
    .removeModules(Set.of(
        "kibana",
        "repository-s3",
        "repository-gcs",
        "repository-azure",
        "repository-url"))
    .build();

try (EmbeddedElasticServer server = EmbeddedElasticServer.start(slim)) {
    server.createIndex("docs");
}
```

Builder helpers now include:

- `includeModule(...)`
- `includeModules(...)`
- `removeModule(...)`
- `removeModules(...)`
- `clearIncludedModules()`
- `includeClasspathPlugin(...)`
- `includeClasspathPlugins(...)`
- `removeClasspathPlugin(...)`
- `removeClasspathPlugins(...)`
- `clearClasspathPlugins()`
- `useBundledProfile(...)`

That means callers can use the same convenience methods that exist on the
process-backed `ElasticServer`, such as:

- `baseUri()`
- `client()`
- `waitForYellow(...)`
- `createIndex(...)`
- `indexDocument(...)`
- `refresh(...)`
- `search(...)`
- `countValue(...)`

Example:

```java
import io.github.wboult.esrunner.embedded.EmbeddedElasticServerConfig;
import io.github.wboult.esrunner.embedded.v9.EmbeddedElasticServer;

Path esHome = Paths.get("distros/embedded/elasticsearch-9.3.1");

EmbeddedElasticServerConfig config = EmbeddedElasticServer.defaultConfig(esHome)
    .toBuilder()
    .workDir(Paths.get(".es-embedded"))
    .clusterName("embedded-local")
    .portRangeStart(9410)
    .portRangeEnd(9420)
    .build();

try (EmbeddedElasticServer server = EmbeddedElasticServer.start(config)) {
    server.createIndex("docs");
    server.indexDocument("docs", "1", "{\"title\":\"hello\"}");
    server.refresh("docs");
    long count = server.countValue("docs");
    System.out.println(count);
}
```

## What is verified

Against real extracted Windows distros for Elasticsearch `8.19.11`,
Elasticsearch `9.3.1`, OpenSearch `2.19.4`, and OpenSearch `3.5.0`, the current
tests cover:

- config-driven startup
- shared `ElasticServerHandle` surface
- create index
- index document
- refresh
- count documents
- match query using the standard analyzer
- repeated embedded start/stop in one JVM
- light concurrent indexing and searching
- trimming the bundled module selection and still booting/searching successfully
- trimming the OpenSearch classpath plugin selection and still booting/searching
  successfully

## Running the tests

### Prerequisites

1. Download and extract the matching Elasticsearch and/or OpenSearch distro.
2. Point the matching `*_EMBEDDED_HOME_*` variables at the extracted roots.
3. Ensure Java 17 is available for ES `8` and OpenSearch `2`.
4. Ensure Java 21 is available for ES `9` and OpenSearch `3`.

Examples:

```sh
export ES_EMBEDDED_HOME_8=/path/to/elasticsearch-8.19.11
export ES_EMBEDDED_HOME_9=/path/to/elasticsearch-9.3.1
export OPENSEARCH_EMBEDDED_HOME_2=/path/to/opensearch-2.19.4
export OPENSEARCH_EMBEDDED_HOME_3=/path/to/opensearch-3.5.0
./gradlew :es-runner-embedded-8:test
./gradlew :es-runner-embedded-9:test
./gradlew :es-runner-embedded-opensearch-2:test
./gradlew :es-runner-embedded-opensearch-3:test
```

```powershell
$env:ES_EMBEDDED_HOME_8 = "C:\path\to\elasticsearch-8.19.11"
$env:ES_EMBEDDED_HOME_9 = "C:\path\to\elasticsearch-9.3.1"
$env:OPENSEARCH_EMBEDDED_HOME_2 = "C:\path\to\opensearch-2.19.4"
$env:OPENSEARCH_EMBEDDED_HOME_3 = "C:\path\to\opensearch-3.5.0"
.\gradlew.bat :es-runner-embedded-8:test
.\gradlew.bat :es-runner-embedded-9:test
.\gradlew.bat :es-runner-embedded-opensearch-2:test
.\gradlew.bat :es-runner-embedded-opensearch-3:test
```

Tests are skipped, not failed, when the matching `ES_EMBEDDED_HOME_*` variable is not set, so the
normal CI build is not affected.

## JVM flags required

All embedded modules still use internal Java APIs that require explicit
`--add-opens` grants. These are set in:

- `es-runner-embedded-8/build.gradle`
- `es-runner-embedded-9/build.gradle`
- `es-runner-embedded-opensearch-2/build.gradle`
- `es-runner-embedded-opensearch-3/build.gradle`

The earlier `-Des.distribution.type=integ_test_zip` attempt turned out to be
wrong for a real extracted distribution. The current build does not set that
flag.

## Architecture

```text
es-runner-embedded-common/
|-- EmbeddedElasticServerConfig.java
|-- EmbeddedHome.java
`-- EmbeddedModuleProfile.java

es-runner-embedded-8/
|-- EmbeddedElasticServer.java
|-- EmbeddedNode.java
`-- embedded-profile.properties

es-runner-embedded-9/
|-- EmbeddedElasticServer.java
|-- EmbeddedNode.java
`-- embedded-profile.properties

es-runner-embedded-opensearch-2/
|-- EmbeddedOpenSearchServer.java
`-- EmbeddedNode.java

es-runner-embedded-opensearch-3/
|-- EmbeddedOpenSearchServer.java
`-- EmbeddedNode.java
```

### `EmbeddedModuleProfile`

Loads the bundled embedded profile resource for one versioned module. The
Elasticsearch profile currently declares:

- the Elasticsearch version line
- the required Java version
- the exact built-in module list to stage from the distro

### `EmbeddedNode`

Small wrapper around the relevant public/protected node constructor for that
versioned module.

### `EmbeddedHome`

Stages an embedded home from an extracted distribution.

- Elasticsearch `8`/`9`: copies the exact built-in module list from the bundled
  profile resource and strips `modulename=` from staged module descriptors
- OpenSearch `2`: copies config/lib and leaves module/plugin loading to the
  classpath plugin set
- OpenSearch `3`: copies config/lib and stages the required distro transport
  module

### `EmbeddedElasticServerConfig`

Immutable embedded config that mirrors the main runner's style, while replacing
process-specific fields with embedded-specific ones:

- `esHome` instead of ZIP/download settings
- `workDir` with derived `data/`, `logs/`, and staged `home/`
- cluster name, node name, port selection, timeouts, and arbitrary settings
- embedded-only included module control
- embedded-only classpath plugin control

### `EmbeddedElasticServer`

Public `AutoCloseable`/`ElasticServerHandle` that:

1. resets process-global logging state for a clean embedded start
2. stages the embedded home for that version family
3. builds settings with safe embedded defaults
4. prepares the runtime environment
5. bootstraps minimal logging
6. starts the embedded node
7. exposes the shared HTTP/query surface through `ElasticServerHandle`

On `close()` it stops the node, waits for close, and resets the process-global
logging state again.

Like the external-process runner, the configured work directory is retained.
That matters on Windows because module jars can stay locked briefly after JVM
shutdown, so eager staged-home deletion is not reliable. The next embedded
start for the same `workDir` restages `workDir/home` before boot.

## Known issues / next steps

- [x] Verify startup succeeds on ES 8.19.11 for a simple embedded profile
- [x] Verify startup succeeds on ES 9.3.1 for a simple embedded profile
- [x] Verify startup succeeds on OpenSearch 2.19.4 for a simple embedded profile
- [x] Verify startup succeeds on OpenSearch 3.5.0 for a simple embedded profile
- [x] Verify repeated embedded start/stop works in one JVM
- [ ] Trim the staged module set further so the embedded home is smaller and
  faster to prepare
- [ ] Decide whether OpenSearch 3 transport can be loaded without staging the
  distro `transport-netty4` module
- [ ] Work out whether `x-pack-security` can be disabled cleanly without
  removing the module entirely
- [ ] Work out whether `x-pack-ml` can be disabled cleanly without removing the
  module entirely
- [ ] Decide whether the descriptor-rewrite approach is robust enough for wider
  use, or whether a custom classloader/bootstrap layer would be cleaner
- [ ] Explore whether the `org.elasticsearch.test:framework` approach can still
  be adapted for future 9.x lines
