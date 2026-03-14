---
title: GitHub Actions CI example
description: Run ES Runner in CI using GitHub Actions.
sidebar:
  order: 9
---

This example runs tests and enables distro downloads in GitHub Actions.

```yaml
name: CI

on:
  push:
  pull_request:

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
          cache: gradle

      - name: Run tests
        env:
          ES_VERSION: "9.3.1"
          ES_DISTROS_DIR: distros
          ES_DISTRO_DOWNLOAD: "true"
        run: ./gradlew test
```

## Cache the distribution

Adding a cache step avoids re-downloading the ~500 MB ZIP on every run:

```yaml
      - uses: actions/cache@v4
        with:
          path: distros
          key: es-distro-${{ env.ES_VERSION }}

      - name: Run tests
        env:
          ES_VERSION: "9.3.1"
          ES_DISTROS_DIR: distros
          ES_DISTRO_DOWNLOAD: "true"
        run: ./gradlew test
```

## Shared Gradle clusters in CI

If your project uses the shared-cluster Gradle plugin, the workflow is the
same — `ES_DISTRO_DOWNLOAD=true` lets the plugin download the distribution,
and the shared cluster starts automatically when a bound suite task runs:

```yaml
name: CI

on:
  push:
  pull_request:

jobs:
  integration:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
          cache: gradle

      - uses: actions/cache@v4
        with:
          path: .gradle/elasticsearch/distros
          key: es-distro-9.3.1

      - name: Run integration and smoke tests
        env:
          ES_DISTRO_DOWNLOAD: "true"
        run: ./gradlew check
```

The plugin handles startup, readiness polling, and namespace injection.
Test suites in any subproject bound via `useCluster(...)` share one
Elasticsearch process for the entire build.

## Notes

- Use `ES_DISTRO_DOWNLOAD=true` to allow downloads when the ZIP is missing.
- Cache the distros directory to speed up subsequent builds.
- A full Linux matrix is still the main compatibility signal, but keeping a
  small `windows-latest` smoke lane for the process-backed path is worthwhile
  if you claim Windows support.

## Related

- [Use shared Gradle test clusters](../gradle-shared-test-clusters/)
- [Build/test matrix](../../reference/build-test-matrix/)
- [Troubleshooting](../troubleshooting/)

