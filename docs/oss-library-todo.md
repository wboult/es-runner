# OSS Library Quality TODO (Elastic Runner)

This checklist tracks the "top 10" qualities for a useful OSS library and the
work needed to cover each point in this repo. All items are intended to be
completed within the current PR branch.

## 1) Clear scope and value

- [x] State the purpose and non-goals in the README (`README.md`).
- [x] Explain why this is a runner (not embedded ES) in docs (`site/src/content/docs/explanation/architecture.md`).
- [x] Document defaults and design choices (`site/src/content/docs/explanation/defaults.md`).
- [x] Provide a short elevator pitch on the docs landing page (`site/src/content/docs/index.mdx`).

## 2) Fast onboarding

- [x] List requirements (JDK + distro ZIP) in README.
- [x] Add a minimal Java quick start in README.
- [x] Add a "getting started" tutorial (`site/src/content/docs/tutorials/getting-started.md`).
- [x] Add a "first server" tutorial (`site/src/content/docs/tutorials/first-server.md`).

## 3) Real-world usage examples

- [x] Java example in README.
- [x] Scala example in README.
- [x] Kotlin example in README.
- [x] Lifecycle helper example (`withServer`) in README.
- [x] Explicit shutdown example in README.
- [x] Two-node cluster example (README + how-to) (`site/src/content/docs/how-to/two-node-cluster.md`).
- [x] Mirror download example (`site/src/content/docs/how-to/use-a-mirror.md`).
- [x] Plugin install example (`site/src/content/docs/how-to/install-plugins.md`).
- [x] Custom ports example (`site/src/content/docs/how-to/custom-ports.md`).
- [x] CI usage example (`site/src/content/docs/how-to/ci-github-actions.md`).
- [x] Troubleshooting walkthrough (`site/src/content/docs/how-to/troubleshooting.md`).
- [x] Pre-download once and run multiple nodes (`site/src/content/docs/how-to/reuse-pre-downloaded-distro.md`).

## 4) API reference and configuration detail

- [x] Public API overview (`site/src/content/docs/reference/api.md`).
- [x] Config field reference with defaults (`site/src/content/docs/reference/configuration.md`).
- [x] Environment variables reference (`site/src/content/docs/reference/environment.md`).
- [x] File layout reference (`site/src/content/docs/reference/file-layout.md`).
- [x] Error reference list (`site/src/content/docs/reference/errors.md`).

## 5) Behavioral clarity (lifecycle, readiness, errors)

- [x] Describe readiness and startup timeouts (`site/src/content/docs/reference/errors.md`).
- [x] Explain shutdown behavior and timeouts (`site/src/content/docs/reference/errors.md`).
- [x] Explain defaults and automatic settings (`site/src/content/docs/explanation/defaults.md`).
- [x] Performance considerations and limits (`site/src/content/docs/explanation/performance.md`).

## 6) Compatibility and support

- [x] Supported ES versions and notes (`site/src/content/docs/reference/compatibility.md`).
- [x] Build and test matrix (`site/src/content/docs/reference/build-test-matrix.md`).
- [x] Support and security expectations (`site/src/content/docs/explanation/security-support.md`).
- [x] Release versioning expectations (`site/src/content/docs/reference/release-versioning.md`).

## 7) Quality and testing

- [x] Unit tests run by default (`README.md`).
- [x] Integration tests gated by `ES_DISTRO_ZIP` (`README.md`).
- [x] README Java examples covered via tests (`src/test/java/...`).
- [x] README Kotlin examples covered via tests (`src/test/kotlin/...`).
- [x] Dependency verification metadata updated (`gradle/verification-metadata.xml`).

## 8) API stability and change communication

- [x] Document API stability levels (`site/src/content/docs/explanation/api-stability.md`).
- [x] Provide a changelog entry point (`CHANGELOG.md` + `site/src/content/docs/reference/changelog.md`).

## 9) Contribution and maintenance

- [x] Contributing guide (`CONTRIBUTING.md`).
- [x] Security policy (`SECURITY.md`).
- [x] Document contribution steps in docs (`site/src/content/docs/how-to/contribute.md`).

## 10) Documentation site (Diataxis + Pages)

- [x] Starlight site scaffolded (`site/`).
- [x] Diataxis structure enforced (tutorials/how-to/reference/explanation).
- [x] Docs navigation configured (`site/astro.config.mjs`).
- [x] Docs build workflow for GitHub Pages (`.github/workflows/docs.yml`).
- [x] Docs build verified locally (`npm run build`).
- [ ] GitHub Pages enabled for the repo (requires repo settings/plan).
