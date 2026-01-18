---
title: Contribute
description: Set up a dev environment and submit changes.
---

# Contribute

We welcome contributions! This guide covers local setup and how to submit a change.

## 1. Clone the repo

```bash
git clone https://github.com/wboult/elastic-runner.git
cd elastic-runner
```

## 2. Run tests

```bash
./gradlew test
```

If you need integration tests, set:

```bash
export ES_DISTRO_DOWNLOAD=true
export ES_VERSION=9.2.4
```

## 3. Make changes

- Keep changes focused.
- Add tests for behavior changes.
- Update documentation where relevant.

## 4. Submit a PR

- Open a pull request on GitHub.
- Ensure all checks pass.
- Describe changes and any breaking impact.

## Related

- [Build/test matrix](../../reference/build-test-matrix/)
- [Release & versioning policy](../../reference/release-versioning/)
