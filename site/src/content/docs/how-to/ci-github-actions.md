---
title: GitHub Actions CI example
description: Run ES Runner in CI using GitHub Actions.
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

## Notes

- Use `ES_DISTRO_DOWNLOAD=true` to allow downloads when the ZIP is missing.
- Cache the `distros` directory to speed up subsequent builds.

## Related

- [Build/test matrix](../../reference/build-test-matrix/)
- [Troubleshooting](../troubleshooting/)

