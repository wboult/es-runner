---
title: Performance guidance
description: Startup cost, shared-cluster savings, and how cache and mirror choices change build time.
---

ES Runner is usually fast enough for integration automation, but it is not a
sub-second in-memory fake. The important question is not just "how fast is one
start?" but "which costs do I pay once, and which do I accidentally pay over
and over?"

## What actually costs time

A process-backed start has up to four distinct phases:

1. resolve or download the distro archive into `distrosDir`
2. extract the distro into `workDir/<version>/distro`
3. launch the JVM and let Elasticsearch or OpenSearch bootstrap
4. wait for the HTTP endpoint and cluster health to become usable

Only phases 3 and 4 are unavoidable on every fresh server start. Phases 1 and
2 can often be cached away.

## Cost model

| Scenario | What you pay | What gets reused | Practical result |
| --- | --- | --- | --- |
| First run with remote download | download + extract + bootstrap + readiness | nothing | slowest path |
| Warm local ZIP + fresh `workDir` | extract + bootstrap + readiness | archive only | common local baseline |
| Warm local ZIP + stable `workDir` | bootstrap + readiness | archive and extracted distro | best repeated process-backed start |
| Shared Gradle cluster across suites | one bootstrap + readiness for the whole build | archive, extracted distro, running node | biggest integration-test savings |
| Nearby mirror or pre-seeded cache | smaller or no network cost | archive fetched locally | best cold-path improvement |

## Where ES Runner already caches work

Two built-in caches matter:

- `distrosDir`
  - stores the ZIP or TAR archive
  - avoids repeated downloads when the archive is already present
- `workDir/<version>/distro`
  - stores the extracted home directory
  - avoids repeated extraction when the versioned work directory is reused

That means the cheapest repeated process-backed start is not:

- deleting everything every run

It is:

- keeping a stable `distrosDir`
- keeping a stable `workDir`
- namespacing test data instead of forcing a full redownload/re-extract cycle

## Shared-cluster savings

The Gradle shared test-cluster plugin changes the cost model more than any
other feature in this repo.

Without a shared cluster:

- `4` suite tasks mean roughly `4` Elasticsearch starts

With a shared cluster:

- `4` suite tasks mean `1` Elasticsearch start

So the rough savings formula is:

```text
savings ~= (number of suites - 1) * single-start cost
```

Example:

- if one process-backed start costs about `30s`
- and `4` suites each start their own node
- the startup budget is about `120s`

With one shared build-scoped cluster:

- startup budget is still about `30s`
- saved startup time is about `90s`

That is why the shared-cluster plugin matters most for multi-project builds or
multiple integration-style suites.

## Mirror and cache effects

Archive download time is often larger than people expect because Elasticsearch
and OpenSearch distros are large archives.

A useful rule of thumb is:

```text
cold remote start = archive transfer + extraction + bootstrap + readiness
warm local start = extraction/reuse + bootstrap + readiness
```

Practical implications:

- a stable local archive cache removes internet variability
- a nearby HTTPS mirror makes CI more predictable
- a `file://` mirror or pre-seeded `distrosDir` removes network transfer from
  the critical path entirely

If your archive is a few hundred megabytes, remote bandwidth alone can add tens
of seconds before the node even begins booting.

## Recommended patterns

### Fastest repeatable local process-backed path

- keep `distrosDir` stable
- keep `workDir` stable
- avoid deleting extracted distro homes between runs unless you are debugging a
  broken local state

### Fastest multi-suite Gradle path

- bind all compatible suites to one shared cluster
- use `NamespaceMode.SUITE`
- let the plugin's lazy startup work instead of forcing eager startup tasks

### Fastest cold CI path

- pre-seed or cache `distrosDir`
- use an internal HTTPS mirror or `file://` mirror where possible
- avoid downloading the same archive repeatedly across jobs

## How to measure in your own environment

Use your real build shape, not a micro-benchmark in isolation.

Measure these two cases:

1. per-suite standalone starts
2. one shared Gradle cluster across the same suites

For example, compare the wall-clock time of:

```bash
./gradlew :app:integrationTest :app:smokeTest :search:integrationTest :search:smokeTest
```

with:

- the same suites bound to a shared cluster
- and then the same suites run without that binding

Then compare:

1. a cold run that must download the archive
2. a warm run with a populated `distrosDir`
3. a warm run with both `distrosDir` and `workDir` already populated

Those measurements will tell you far more than a synthetic benchmark number.

## Practical tuning checklist

- Increase heap for indexing-heavy suites.
- Put `workDir` on a fast local disk.
- Reuse `distrosDir` across repeated runs.
- Reuse `workDir` when you want faster repeated starts.
- Use mirrors or pre-seeded archives in CI.
- Use the shared-cluster plugin when multiple suites can share one node.

## Related

- [Use shared Gradle test clusters](../../how-to/gradle-shared-test-clusters/)
- [Shared cluster best practices](../../how-to/gradle-shared-test-cluster-best-practices/)
- [Configuration reference](../../reference/configuration/)
- [Cloud storage mirrors](../../how-to/cloud-storage-mirrors/)
