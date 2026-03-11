# Performance guidance

ES Runner performance is mostly about avoiding repeated work.

## What costs time

A process-backed start can include:

1. archive download into `distrosDir`
2. archive extraction into `workDir/<version>/distro`
3. JVM + Elasticsearch/OpenSearch bootstrap
4. readiness wait

Only bootstrap and readiness are mandatory on every fresh server start. The
archive and extracted distro can often be reused.

## Cost model

| Scenario | Cost paid | Reused |
| --- | --- | --- |
| Cold remote start | download + extract + bootstrap + readiness | nothing |
| Warm local ZIP + fresh `workDir` | extract + bootstrap + readiness | archive |
| Warm local ZIP + stable `workDir` | bootstrap + readiness | archive + extracted distro |
| Shared Gradle cluster | one startup per build | archive + extracted distro + running node |

## Shared-cluster savings

If one standalone start costs `30s` and you have `4` suite tasks:

- separate starts: about `120s`
- one shared cluster: about `30s`
- saved startup time: about `90s`

Rough rule:

```text
savings ~= (suite count - 1) * single-start cost
```

## Mirror and cache effects

Cold remote starts pay network transfer before the node can even boot. Warm
local starts do not.

The most useful levers are:

- stable `distrosDir`
- stable `workDir`
- nearby HTTPS mirror
- `file://` mirror or pre-seeded cache in CI

## Recommended approach

- Reuse `distrosDir` across repeated runs.
- Reuse `workDir` when you want faster repeated starts.
- Use shared Gradle clusters for multi-project or multi-suite builds.
- Use mirrors or pre-seeded archives in CI.

## Measure in your environment

Compare:

1. a cold run that downloads the archive
2. a warm run with a populated `distrosDir`
3. a warm run with both `distrosDir` and `workDir` already populated
4. per-suite starts vs one shared Gradle cluster

That gives more useful results than a synthetic micro-benchmark.

## Related

- [docs/gradle-shared-test-clusters.md](docs/gradle-shared-test-clusters.md)
- [docs/gradle-shared-test-cluster-best-practices.md](docs/gradle-shared-test-cluster-best-practices.md)
- [docs/cloud-storage-mirrors.md](docs/cloud-storage-mirrors.md)
