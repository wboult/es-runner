# Choose a Gradle shared-cluster backend

ES Runner now has two Gradle shared-cluster backends:

- the original process-backed plugin, which starts the official ZIP distro in a
  local OS process
- the Docker-backed plugin, which keeps the same suite-binding and namespacing
  model on top of Testcontainers

Use this page to choose the right one quickly.

## Short answer

Use the process-backed plugin when:

- Docker is unavailable, restricted, or simply not part of your normal build
- you want the mainline ES Runner path with the broadest option coverage
- you care about ZIP distros, mirrors, local work dirs, or plugin install
- you want OpenSearch shared clusters today

Use the Docker-backed plugin when:

- your team already standardizes on Docker/Testcontainers
- image/tag configuration is more natural than ZIP distro management
- your CI agents already provide a healthy Docker daemon
- you want the same namespace-aware test helpers without switching test code

## Plugin and sample map

| Backend | Plugin id | Main cluster runtime | Public sample |
| --- | --- | --- | --- |
| Process-backed | `io.github.wboult.es-runner.shared-test-clusters` | local Elasticsearch process from the official ZIP distro | `samples/gradle-shared-cluster-multiproject-sample/` |
| Docker-backed | `io.github.wboult.es-runner.docker-shared-test-clusters` | shared Elasticsearch container via Testcontainers | `samples/docker-shared-cluster-multiproject-sample/` |

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

## Capability differences

| Concern | Process-backed plugin | Docker-backed plugin |
| --- | --- | --- |
| Docker required | no | yes |
| ZIP distro control | yes | no |
| HTTPS / file mirrors | yes | not the main model |
| Plugin install into distro | yes | not first-class yet |
| OpenSearch shared clusters | yes | not yet |
| Shared namespace model | yes | yes |
| Build-scoped reuse across projects/suites | yes | yes |
| Linux CI coverage | yes | yes |
| Windows smoke coverage | yes | plugin module builds, but Docker functional coverage is Linux-first |

## Recommended decision flow

1. If your build cannot depend on Docker, use the process-backed plugin.
2. If your build already depends on Docker and container images are the normal
   operational unit, use the Docker-backed plugin.
3. If you need OpenSearch shared clusters today, use the process-backed plugin.
4. If you need the richest distro-level knobs, use the process-backed plugin.
5. If your goal is only shared suite reuse and Docker is already standard, the
   Docker-backed plugin is the simpler mental model.

## Start from a public sample

For the fastest realistic starting point, copy from one of these public samples
instead of synthesizing your build from scratch:

- process-backed sample:
  `samples/gradle-shared-cluster-multiproject-sample/`
- Docker-backed sample:
  `samples/docker-shared-cluster-multiproject-sample/`

Each sample shows:

- one shared Elasticsearch 9 cluster per build
- multiple subprojects and suite tasks
- a small internal helper module
- suite-level namespace isolation
- namespaced templates, aliases, and indices together in one flow

## Related

- [docs/gradle-shared-test-clusters.md](C:/Dev/elastic-runner/docs/gradle-shared-test-clusters.md)
- [docs/docker-shared-test-clusters.md](C:/Dev/elastic-runner/docs/docker-shared-test-clusters.md)
