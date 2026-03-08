# Public Release and Maven Central Readiness

This document tracks what still needs doing before ES Runner is made public and
eventually published.

## Decisions already made

- public project name: `ES Runner`
- owner namespace: `io.github.wboult`
- Java package prefix: `io.github.wboult.esrunner`
- Maven coordinate base:
  - `io.github.wboult:es-runner`
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

### 1. Add real publishing

The build still needs publication wiring for:

- Maven Central
- Gradle Plugin Portal
- signatures
- complete generated POM metadata
- release workflow automation

Until that exists, the repo can become public, but it is not ready for a
polished first published release.

### 2. Add publication metadata

Every published module should have:

- name
- description
- URL
- license metadata
- developer metadata
- SCM metadata

This is still missing from the Gradle build.

### 3. Add a consume-the-published-artifact smoke test

Before the first release tag, add one small external-consumer smoke test that
uses:

- the published library coordinates
- the published Gradle plugin id

That catches packaging mistakes that normal source-tree tests do not.

### 4. Decide how much API is truly public

The API is in much better shape now, but before `1.0` it is still worth
reviewing whether some currently public types should stay public, especially:

- `RunnerState`
- the full Gradle plugin DSL surface

### 5. Final public repo polish

Still worth doing before flipping the repo public:

- set the GitHub repo description and topics
- enable GitHub Pages for the docs site if it is not already enabled
- add a social preview image
- sanity-check README wording one last time from a first-time-user perspective

## Recommended next order

1. make the repo public with the renamed namespace and MIT license in place
2. wire Maven Central publishing and signing
3. wire Gradle Plugin Portal publication
4. add a published-artifact smoke test
5. cut the first release only after those pieces are green

## Notes

- GitHub repo name, Pages URL, and public docs links should stay aligned:
  - repo: `wboult/es-runner`
  - docs: `https://wboult.github.io/es-runner/`
- The docs/site title and public identifiers use `ES Runner`.
