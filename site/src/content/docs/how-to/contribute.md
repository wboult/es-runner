---
title: Contribute
description: Set up a dev environment and submit changes.
sidebar:
  order: 14
---

We welcome contributions! This guide covers local setup and how to submit a change.

## 1. Clone the repo

```bash
git clone https://github.com/wboult/es-runner.git
cd es-runner
```

You will usually want:

- JDK 17 for the core library, plugin, and most tests
- JDK 21 as well if you want to run the experimental embedded ES 9 / OpenSearch 3 modules
- Node.js for the docs site under `site/`

## 2. Run the smallest useful test slice

Core verification:

```bash
./gradlew test
./gradlew scala3Test
```

Gradle plugin and helper:

```bash
./gradlew :es-runner-gradle-plugin:test :es-runner-gradle-test-support:test
```

One focused class while iterating:

```bash
./gradlew test --tests io.github.wboult.esrunner.ElasticRunnerTest
```

If you need process-backed download tests locally, enable distro download:

```bash
export ES_DISTRO_DOWNLOAD=true
export ES_VERSION=9.3.1
```

This repo also verifies specific Scala/Spark combinations. Use `-PscalaVersion`
and `-PsparkVersion` when you need to reproduce one CI lane locally.

## 3. Update dependency verification only when needed

If you intentionally add or upgrade dependencies, refresh the verification file
in the narrowest scope you can:

```bash
./gradlew --write-verification-metadata sha256 :es-runner-java-client:test
```

Review `gradle/verification-metadata.xml` before committing.

## 4. Build docs when behavior changes

User-facing changes should update both the repo docs in `docs/` and the site
docs in `site/`.

```bash
cd site
npm ci
npm run build
```

## 5. Make changes

- Keep changes focused.
- Add tests for behavior changes.
- Update documentation where relevant.
- Preserve public APIs unless the change is intentional and documented.

## 6. Submit a PR

- Open a pull request on GitHub.
- Wait for all checks to pass and fix failures on the same branch.
- Describe changes and any breaking impact.
- Merge to `main` with a squash merge only.

## Related

- [Build/test matrix](../../reference/build-test-matrix/)
- [Compatibility matrix](../../reference/compatibility/)
- [Release & versioning policy](../../reference/release-versioning/)

