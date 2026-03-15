# Public Release and Maven Central Readiness

This document tracks what still needs doing before ES Runner is made public and
eventually published.

## Decisions already made

- public project name: `ES Runner`
- owner namespace: `io.github.wboult`
- Java package prefix: `io.github.wboult.esrunner`
- Maven coordinate base:
  - `io.github.wboult:es-runner`
  - `io.github.wboult:es-runner-gradle-core`
  - `io.github.wboult:es-runner-gradle-plugin`
  - `io.github.wboult:es-runner-gradle-test-support`
- Gradle plugin id: `io.github.wboult.es-runner.shared-test-clusters`
- OSS license: `MIT`
- Gradle plugin distribution target: Maven Central and Gradle Plugin Portal

## Already completed on this branch

- renamed packages away from `com.elastic`
- renamed Gradle project/module names to `es-runner-*`
- updated docs and examples to current tested Elasticsearch lines
- added public API Javadocs for the main entry points
- added GitHub community-health files:
  - issue templates
  - pull request template
  - code of conduct
- added the MIT license file

## Remaining blockers before a first public artifact release

### 1. Configure live release secrets and namespace ownership

The build and workflows are now wired for:

- Maven Central
- Gradle Plugin Portal
- signatures
- complete generated POM metadata
- release workflow automation

What still needs owner setup is:

- claim and verify the `io.github.wboult` Central namespace
- create Central Portal user tokens
- create Plugin Portal publish credentials
- add the signing key/password and publish tokens as GitHub secrets

### 2. Exercise the first real tag release

The release workflow now exists at `.github/workflows/release.yml`, but the
first real release should still be treated as a dress rehearsal:

- create a fresh tag such as `v0.1.0`
- verify Central publication finishes cleanly
- verify Plugin Portal publication finishes cleanly
- verify the GitHub release is created with the expected notes
- run the checked-in consumer sample against the newly published coordinates

### 3. Decide how much API is truly public

The API is in much better shape now, but before `1.0` it is still worth
reviewing whether some currently public types should stay public, especially:

- `RunnerState`
- the full Gradle plugin DSL surface

### 4. Final public repo polish

Still worth doing before flipping the repo public:

- set the GitHub repo description and topics
- add a social preview image
- sanity-check README wording one last time from a first-time-user perspective

## Recommended next order

1. add the required GitHub secrets and Central namespace ownership
2. run the release workflow from a real tag
3. validate the published coordinates with the checked-in consumer sample
4. only then announce the first public artifact release

## Notes

- GitHub repo name, Pages URL, and public docs links should stay aligned:
  - repo: `wboult/es-runner`
  - docs: `https://wboult.github.io/es-runner/`
- The docs/site title and public identifiers use `ES Runner`.
- The release workflow docs live in `docs/releasing.md`.
