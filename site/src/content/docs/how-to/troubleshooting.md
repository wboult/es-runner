---
title: Troubleshooting
description: Diagnose common failures and recover quickly.
sidebar:
  order: 12
---

This guide helps you resolve common issues when starting Elasticsearch or
OpenSearch with ES Runner.

## Start here first

When a build fails, work through these in order:

1. Read the exception type and message.
2. Open `startup-diagnostics.txt` if startup itself failed.
3. Read `runner.log` or `server.logTail()` for the recent process output.
4. If the cluster started but test assertions still fail, check refresh,
   namespacing, and reused work-dir state before changing timeouts.

For Docker-backed shared-cluster failures, there is no
`startup-diagnostics.txt` file. Use the exception message as the primary
diagnostic artifact because it now includes image, Docker/Testcontainers
environment hints, container id/name/ports, and a recent log tail.

## Distro ZIP not found

**Symptom**: `ElasticRunnerException: Distro archive does not exist`

**Fix**:
- Provide an explicit ZIP path with `.distroZip(...)`, or
- Enable downloads with `.download(true)` and ensure network access, or
- Set `ES_DISTRO_DOWNLOAD=true` / `OPENSEARCH_DISTRO_DOWNLOAD=true` in CI.

## Download fails

**Symptom**: `Failed to download distro: ... (HTTP 404)`

**Fix**:
- Verify the version exists.
- Confirm your `downloadBaseUrl` points to a mirror with the correct filename.
- Check the resolved download URI in `startup-diagnostics.txt`.

## Port collisions

**Symptom**: The node fails to start and logs show `BindException`.

**Fix**:
- Use `.httpPort(0)` (default) and a safe range with `.portRange(9200, 9300)`.
- Ensure each server gets its own `workDir`.
- Inspect `startup-diagnostics.txt` to see the resolved port settings.

## Process exits early

**Symptom**: `Elasticsearch process exited early` or `OpenSearch process
exited early`.

**Fix**:
- Inspect the `startup-diagnostics.txt` path printed in the exception first.
- Inspect `server.logTail()` for the root cause when you still have a server
  handle.
- Check JVM heap settings (`heap`) and disk permissions.

## Startup times out

**Symptom**: `Timed out waiting for Elasticsearch`, `Timed out waiting for
OpenSearch`, or `Timed out waiting for HTTP port bind`.

**Fix**:
- Open `<workDir>/<version>/logs/startup-diagnostics.txt`.
- Check the resolved archive path and download URI in the diagnostics output.
- Increase `startupTimeout` or `heap` if the node is starting slowly.
- Widen the HTTP port range if the machine may already have other local nodes.

## Docker shared cluster fails to start

**Symptom**: the Docker/Testcontainers-backed shared-cluster plugin fails before
any test code runs.

**Fix**:
- Start with the exception message itself. It now includes:
  - configured cluster name
  - image reference
  - startup timeout
  - Docker/Testcontainers environment hints
  - container id/name/ports when available
  - a recent container log tail
- If the message says Docker environment detection failed, confirm the Gradle
  process can reach the Docker daemon and socket.
- If the message mentions manifest, image pull, or not found errors, verify the
  configured image exists and the registry is reachable.
- If the message says the health probe failed, inspect the inline log tail for
  Elasticsearch bootstrap or security-setting issues.
- If the container starts but never becomes healthy on slower machines, raise
  `startupTimeoutMillis`.

## Shared cluster started, but tests still fail

If the Gradle shared-cluster plugin started Elasticsearch and your suite still
fails, the problem is usually not cluster startup. It is usually one of:

- test data is not visible yet because the suite did not refresh
- template ids and template `index_patterns` were namespaced differently
- the suite is reading stale local state from a reused `workDir`
- the cluster reached yellow health, but your test assumptions still were not true

For the Docker-backed shared-cluster plugin, stale state from previous Gradle
invocations is not normally the issue because it intentionally starts a fresh
container for each build.

### Zero hits after indexing

**Symptoms**:

- indexing calls succeed
- `count` or `search` returns `0`
- aliases or template-backed indices look empty even though documents were written

**Checks**:

- call `refresh(...)` after indexing and before deterministic assertions
- make sure the test is writing to the same concrete index or alias it is querying
- if using the Gradle helper, derive names from the same `ElasticGradleTestEnv`
  instance inside the suite

`waitForYellow()` only means the cluster is allocated enough to serve requests.
It does not make newly indexed documents searchable. Refresh is still your
test-side visibility boundary.

### Template id vs `index_patterns`

This is the most common shared-cluster namespacing mistake.

Use:

- `env.template("orders-template")` for the template id
- `env.indexPattern("orders")` inside template `index_patterns`
- `env.index("orders")` or `env.alias("orders-read")` for concrete resources

Do not use `env.index("orders-*")` for a template pattern. Concrete helpers are
for one physical resource id. `indexPattern(...)` exists specifically for
wildcard matching.

### Reused local state from `workDir`

Shared clusters intentionally reuse one build-scoped node. Reused local runs
can also reuse the same `workDir`, which is fast but can preserve:

- old indices
- old templates
- old aliases
- previously downloaded distros and extracted state

If a failure only reproduces locally after several runs:

- inspect the configured `workDir`
- delete that cluster's work directory and rerun
- keep logical resource names stable and namespaced instead of inventing
  ad-hoc random physical names

For Gradle shared clusters, an explicit root-level `workDir` is still the right
default. Just remember that a stable path trades faster repeated starts for
more persistent local state.

### Yellow health is necessary, not sufficient

The shared-cluster plugin waits for yellow health before it injects connection
details into the test JVM. That protects the first test contact with a fresh
single-node cluster, but it does not guarantee that:

- your templates have been installed
- your suite has created the indices it expects
- indexed documents have been refreshed
- another suite did not leave incompatible cluster-wide state behind

Treat yellow health as "the node is up and usable", not "my test data is ready."

### Where to look for logs and state

For process-backed runs, inspect:

```text
<workDir>/<version>/logs/runner.log
<workDir>/<version>/logs/startup-diagnostics.txt
```

Also useful:

- `server.logTail()` when you still have a server handle
- the configured `workDir` when stale state is suspected
- the shared-cluster sample and best-practices guide when the failure is about
  namespacing rather than process startup

## Cluster won't form

**Symptom**: Two nodes start but remain separate.

**Fix**:
- Ensure `cluster.name` matches across nodes.
- Use valid `discovery.seed_hosts` with **transport ports**.
- Provide `cluster.initial_master_nodes` on first boot.

## Related

- [Error messages reference](../../reference/errors/)
- [Configuration reference](../../reference/configuration/)
- [Use shared Gradle test clusters](../gradle-shared-test-clusters/)
- [Shared cluster best practices](../gradle-shared-test-cluster-best-practices/)
