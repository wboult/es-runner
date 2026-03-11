# Contributing to ES Runner

Thanks for helping improve ES Runner. This guide is the fastest path to a
useful local dev setup, the right test slices, and a PR that matches the repo
conventions.

## Development setup

Clone the repo and build from the root:

```bash
git clone https://github.com/wboult/es-runner.git
cd es-runner
```

Minimum practical toolchain:

- JDK 17 for the core library, Gradle plugin, Java client adapter, and most tests
- JDK 21 as well if you want to run the experimental embedded ES 9 / OpenSearch 3 modules
- Node.js for the docs site under `site/`

The repo uses Gradle toolchains. If Gradle cannot find the configured JDKs,
adjust `org.gradle.java.installations.paths` in `gradle.properties` or provide
compatible local installations.

## Common build commands

Use the narrowest slice that proves your change.

Full core verification:

```bash
./gradlew test
./gradlew scala3Test
```

Gradle plugin and helper:

```bash
./gradlew :es-runner-gradle-plugin:test :es-runner-gradle-test-support:test
```

Official Java client adapter:

```bash
./gradlew :es-runner-java-client:test
```

Run one test class or method while iterating:

```bash
./gradlew test --tests io.github.wboult.esrunner.ElasticRunnerTest
./gradlew :es-runner-gradle-plugin:test --tests io.github.wboult.esrunner.gradle.ElasticSharedTestClustersPluginFunctionalTest
```

Run a specific Scala/Spark lane locally:

```bash
./gradlew test -PscalaVersion=2.12.21 -PsparkVersion=3.5.8
./gradlew test -PscalaVersion=2.13.18 -PsparkVersion=4.1.1
```

## Process-backed integration tests

Most process-backed integration tests can either download a distro on demand or
use a local distro cache.

Download-on-demand example:

```bash
export ES_DISTRO_DOWNLOAD=true
export ES_VERSION=9.3.1
./gradlew test
```

Windows PowerShell equivalent:

```powershell
$env:ES_DISTRO_DOWNLOAD = "true"
$env:ES_VERSION = "9.3.1"
./gradlew.bat test
```

OpenSearch process tests also respect the repo's local distro directories and
`OPENSEARCH_*` environment variables. See the build/test matrix and
compatibility docs for the exact currently verified lines.

## Experimental embedded modules

The embedded runners live under `experimental/embedded/` and are experimental
by design.

Typical local slices are:

```bash
./gradlew :es-runner-embedded-8:test
./gradlew :es-runner-embedded-9:test
./gradlew :es-runner-embedded-opensearch-2:test
./gradlew :es-runner-embedded-opensearch-3:test
```

Those modules usually expect matching extracted distros or embedded-home env
vars as described in `docs/embedded-jvm-server.md`.

## Dependency verification updates

This repo uses Gradle dependency verification. If you intentionally add or
upgrade dependencies, refresh the verification metadata in the smallest scope
you can.

Example:

```bash
./gradlew --write-verification-metadata sha256 :es-runner-java-client:test
```

That updates `gradle/verification-metadata.xml`. Review it carefully before
committing.

## Documentation workflow

Docs live in two places:

- top-level `docs/` for repo-facing markdown
- `site/` for the published docs site

When changing user-facing behavior, update both the relevant repo docs and the
site docs.

Build the site locally with:

```bash
cd site
npm ci
npm run build
```

Use `npm run dev` only when you need the live preview.

## Change expectations

- Keep PRs focused on one main objective.
- Add or update tests for behavior changes.
- Update docs and examples when user-visible behavior changes.
- Prefer realistic verification over broad but low-signal changes.
- Preserve the existing public API unless the change is clearly intentional.

## Pull request expectations

- Open a GitHub PR against `main`.
- Let GitHub Actions finish and fix failures on the same branch until the PR is green.
- When merging to `main`, use a squash merge only.
- Do not create merge commits on top of `main`.

## Reporting issues

Useful issue reports include:

- a minimal reproduction
- expected vs actual behavior
- stack traces or cluster logs
- OS, JDK, Gradle, and Elasticsearch/OpenSearch version details

## Related docs

- `docs/public-release-readiness.md`
- `docs/gradle-shared-test-clusters.md`
- `docs/embedded-jvm-server.md`
- `site/src/content/docs/reference/build-test-matrix.md`
- `site/src/content/docs/reference/compatibility.md`


