---
title: Choose a shared-cluster backend
description: Decide between the process-backed and Docker-backed ES Runner Gradle shared-cluster plugins.
sidebar:
  order: 9
---

ES Runner has two Gradle shared-cluster backends:

- the original process-backed plugin, which starts the official ZIP distro in a
  local OS process
- the Docker-backed plugin, which keeps the same suite-binding and namespacing
  model on top of Testcontainers

Use this page to decide quickly, then jump to the right sample and how-to.

## Short answer

Use the process-backed plugin when:

- Docker is unavailable, restricted, or simply not part of your normal build
- you want the mainline ES Runner path with the broadest option coverage
- you care about ZIP distros, mirrors, local work dirs, or plugin install
- you want the richest distro-level knobs or sticky local state between builds

Use the Docker-backed plugin when:

- your team already standardizes on Docker/Testcontainers
- image/tag configuration is more natural than ZIP distro management
- your CI agents already provide a healthy Docker daemon
- you want the same namespace-aware test helpers without switching test code

## What stays the same

Both backends use the same test helper artifact:

- `io.github.wboult:es-runner-gradle-test-support`

Both backends also keep the same test-side naming API:

- `ElasticGradleTestEnv.fromSystemProperties()`
- `env.index(...)`
- `env.indexPattern(...)`
- `env.template(...)`
- `env.alias(...)`

So the main decision is about runtime and build environment, not test-code
shape.

## Plugin and sample map

| Backend | Plugin id | Main cluster runtime | Public sample |
| --- | --- | --- | --- |
| Process-backed | `io.github.wboult.es-runner.shared-test-clusters` | local Elasticsearch process from the official ZIP distro | [Process sample](https://github.com/wboult/es-runner/tree/main/samples/gradle-shared-cluster-multiproject-sample) |
| Docker-backed | `io.github.wboult.es-runner.docker-shared-test-clusters` | shared Elasticsearch or OpenSearch container via Testcontainers | [Docker Elasticsearch sample](https://github.com/wboult/es-runner/tree/main/samples/docker-shared-cluster-multiproject-sample) and [Docker OpenSearch sample](https://github.com/wboult/es-runner/tree/main/samples/docker-opensearch-shared-cluster-multiproject-sample) |

## Capability differences

| Concern | Process-backed plugin | Docker-backed plugin |
| --- | --- | --- |
| Docker required | no | yes |
| ZIP distro control | yes | no |
| HTTPS / file mirrors | yes | not the main model |
| Plugin install into distro | yes | not first-class yet |
| OpenSearch shared clusters | yes | yes |
| Shared namespace model | yes | yes |
| Build-scoped reuse across projects/suites | yes | yes |
| Linux CI coverage | yes | yes |
| Windows smoke coverage | yes | plugin module builds, but Docker functional coverage is Linux-first |

## Recommended decision flow

1. If your build cannot depend on Docker, use the process-backed plugin.
2. If your build already depends on Docker and container images are the normal
   operational unit, use the Docker-backed plugin.
3. If you need the richest distro-level knobs, use the process-backed plugin.
4. If your goal is only shared suite reuse and Docker is already standard, the
   Docker-backed plugin is the simpler mental model.

## Recommended reading order

1. Copy the public sample that matches your backend.
2. Read the backend-specific how-to page while wiring your root build.
3. Come back to this page only if you are deciding whether the other backend is
   a better fit.

## Start from a public sample

For the fastest realistic starting point, copy from one of these public samples
instead of synthesizing your build from scratch:

- [Process-backed multi-project sample](https://github.com/wboult/es-runner/tree/main/samples/gradle-shared-cluster-multiproject-sample)
- [Docker-backed Elasticsearch multi-project sample](https://github.com/wboult/es-runner/tree/main/samples/docker-shared-cluster-multiproject-sample)
- [Docker-backed OpenSearch multi-project sample](https://github.com/wboult/es-runner/tree/main/samples/docker-opensearch-shared-cluster-multiproject-sample)

Each sample shows:

- one shared search cluster per build
- multiple subprojects and suite tasks
- a small internal helper module
- suite-level namespace isolation
- namespaced templates, aliases, and indices together in one flow

## Related

- [Use shared Gradle test clusters](../gradle-shared-test-clusters/)
- [Use Docker shared test clusters](../docker-shared-test-clusters/)
