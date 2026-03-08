# Public Release and Maven Central Readiness

This document is a repo-specific audit for taking Elastic Runner from a strong internal/private project to a polished first public release.

It is intentionally opinionated. The goal is not merely "can we upload a jar?" but "will the first public version feel professional, trustworthy, and easy to adopt?"

## Overall assessment

The project already has a solid engineering base:

- good test coverage, including real integration tests and README example tests
- a docs site with reference/how-to/tutorial/explanation structure
- `CHANGELOG.md`, `CONTRIBUTING.md`, and `SECURITY.md`
- sources and javadoc jars already enabled in the Gradle build

However, it is **not ready yet** for a high-confidence public `0.1.x` launch.

The biggest gaps are:

1. namespace / branding / coordinate ownership
2. missing publication and signing configuration
3. weak published API documentation
4. documentation inconsistencies and stale examples
5. missing top-level OSS/legal polish such as a real `LICENSE` file

## Hard blockers before making the repo public

### 1. Fix the namespace and branding story

Current state:

- root coordinates are `com.elastic` in [build.gradle](../build.gradle)
- Java packages are `com.elastic.runner.*`
- Gradle plugin id is `com.elastic.runner.shared-test-clusters` in [elastic-runner-gradle-plugin/build.gradle](../elastic-runner-gradle-plugin/build.gradle)

Why this is a blocker:

- Sonatype requires you to publish under a namespace you control.
- The current `com.elastic` namespace strongly implies ownership of `elastic.com`, which this repo does not appear to control.
- The naming also creates likely confusion with Elastic's official products and branding.

Recommended direction:

- pick a permanent owner-controlled namespace before the first public release
- for a personal GitHub-backed project, the safest default is `io.github.wboult`
- rename Java packages before release, not after
- rename the Gradle plugin id to an owner-traceable prefix before release

Practical recommendation:

- Maven coordinates:
  - `io.github.wboult:elastic-runner`
  - `io.github.wboult:elastic-runner-gradle-test-support`
  - `io.github.wboult:elastic-runner-gradle-plugin`
- Gradle plugin id:
  - something under `io.github.wboult...`
- Java package prefix:
  - something under `io.github.wboult...`

Open naming question:

- decide whether the product name should remain "Elastic Runner" at all
- even if the project name stays, the namespace must not stay `com.elastic`
- this is not legal advice, but it is a clear reputation and confusion risk

### 2. Add an actual `LICENSE` file

Current state:

- there is no top-level `LICENSE` file

Why this is a blocker:

- a public repo without a clear license is effectively "all rights reserved" by default
- Maven Central metadata also expects license information
- users and companies will reject adoption if licensing is ambiguous

Recommended action:

- choose the license now
- add a canonical `LICENSE` file at repo root
- mirror the same choice in generated POM metadata and docs

### 3. Add real Maven Central publishing and signing

Current state:

- `withSourcesJar()` and `withJavadocJar()` are present in [build.gradle](../build.gradle)
- there is no `maven-publish` setup
- there is no `signing` setup
- there is no Central Portal publishing workflow
- there is no POM metadata configuration

Why this is a blocker:

- you cannot ship a credible Central release without reproducible publication config
- Central releases are immutable, so the first public coordinate/version matters

Recommended action:

- add `maven-publish`
- add `signing`
- define publications for:
  - core library
  - Gradle test-support artifact
  - Gradle plugin module artifact
- configure required POM metadata for every publication
- add a repeatable release task and CI release workflow

### 4. Decide the Gradle plugin distribution strategy

Current state:

- README expects users to apply the plugin via `plugins { ... }`
- the plugin is only configured as a local Gradle plugin module today

Why this is a blocker:

- Maven Central alone is not enough for the nice `plugins { id("...") version "..." }` experience
- for that, the plugin should also be published to the Gradle Plugin Portal

Recommended action:

- publish the plugin to the Gradle Plugin Portal as well as Maven Central
- if you do not want to do that yet, remove the `plugins {}`-style docs and document `pluginManagement` / direct dependency wiring instead

### 5. Add real Javadocs and reduce the accidental public surface

Current state:

- Javadoc tasks succeed, but with very large warning counts
- during audit, `javadoc` reported roughly:
  - core module: 100 warnings
  - Gradle plugin module: 71 warnings
  - Gradle test-support module: 15 warnings
- only a tiny portion of the public API currently has class-level or method-level Javadocs

Repo-specific concern:

- some public types look more internal than public, especially [RunnerState.java](../src/main/java/com/elastic/runner/RunnerState.java)

Recommended action:

- review every public type and decide whether it is:
  - stable public API
  - incubating public API
  - internal and should become package-private
- add Javadocs to all public types and the main public methods
- make the generated javadocs something you would be comfortable shipping to first-time users

## Public repo polish gaps

### 6. Fix documentation accuracy issues before public launch

Current state:

- README still contains many stale version examples like `9.2.4`
- README has contradictory plugin guidance:
  - one section shows `id("com.elastic.runner")`
  - another shows `com.elastic.runner.shared-test-clusters`
- docs still read partly like an evolving private project rather than a finalized public library

Recommended action:

- update examples to the currently tested line, or use a placeholder like `<current-tested-version>`
- make the README's first example the easiest path, not the most manual path
- choose one plugin story and remove the contradictory one
- explicitly label the Gradle plugin as `Incubating` if you are not ready to freeze it

### 7. Make the docs optimize for adoption, not completeness

Today the docs are rich, but the first-release experience can still be improved.

Recommended action:

- make the README landing path brutally simple:
  - dependency
  - one minimal Java example
  - one minimal Gradle example
  - one sentence on when to use it vs Testcontainers
- add a short "Should I use this?" section
- add a short "What this does not do" section
- add one end-to-end example project that consumes the published artifact, not just source-tree examples

### 8. Improve public GitHub hygiene

Current state:

- `CHANGELOG.md`, `CONTRIBUTING.md`, and `SECURITY.md` exist
- GitHub issue templates, PR template, and code of conduct do not appear to exist
- docs workflow exists, but GitHub Pages still has to be enabled in repo settings

Recommended action:

- add:
  - `.github/ISSUE_TEMPLATE/`
  - `.github/pull_request_template.md`
  - `CODE_OF_CONDUCT.md`
- enable GitHub Pages for the docs site
- add repo description, topics, and social preview image in GitHub settings

## Maven Central specifics

These items come from Sonatype's current Central Portal guidance:

- namespace ownership and verification:
  - <https://central.sonatype.org/register/namespace/>
  - <https://central.sonatype.org/faq/verify-ownership/>
- Central publishing requirements:
  - <https://central.sonatype.org/publish/requirements/>
- Central Portal registration:
  - <https://central.sonatype.org/register/central-portal/>
- Gradle-specific publishing guidance:
  - <https://central.sonatype.org/publish/publish-portal-gradle/>

What that means concretely for this repo:

- you need a verified namespace you actually control
- every published jar needs matching `-sources.jar` and `-javadoc.jar`
- every published file needs signatures
- every published module needs a complete POM:
  - name
  - description
  - url
  - licenses
  - developers
  - SCM info

### Recommended publication architecture

Because Sonatype does not currently provide a first-party Gradle publishing plugin for the Portal, decide this explicitly:

Option A:

- use a community Gradle Central publishing plugin
- simpler release ergonomics
- fewer custom build steps

Option B:

- use `maven-publish` plus Sonatype's compatibility/portal flow
- more control
- more custom CI scripting

For a professional first release, the important thing is not which option you choose, but that:

- it is scripted
- it is repeatable locally and in CI
- it is validated before the first public tag

## API maturity recommendations

### 9. Freeze the "easy path" API before release

The easiest path should be obvious and ergonomic from the README and Javadocs.

Before public release, confirm that these are the canonical entry points:

- `ElasticRunner.start(...)`
- `ElasticRunner.withServer(...)`
- `ElasticRunnerConfig.from(builder -> ...)`
- `ElasticServer`
- `ElasticClient`

Everything else should either:

- support those flows cleanly, or
- be hidden/package-private, or
- be clearly marked as advanced/incubating

### 10. Review for accidental complexity

Specific review targets:

- are there too many near-duplicate methods on `ElasticServer` and `ElasticClient`?
- should some convenience methods return typed results instead of raw JSON?
- should any builder defaults be changed to make the first example simpler?
- are internal persistence types like `RunnerState` really supposed to be public?
- does the Gradle plugin DSL feel stable enough to publish publicly yet?

### 11. Add a compatibility policy before people depend on it

Recommended action:

- define what can break in `0.x`
- define what "Incubating" means for the Gradle plugin
- define whether package names / plugin ids may still move before `1.0`
- document deprecation policy once the API settles

## Release engineering checklist

Before the first public release, I would want all of these done:

- [ ] choose final namespace / branding
- [ ] rename coordinates, package names, and plugin ids
- [ ] add `LICENSE`
- [ ] add `maven-publish` + `signing`
- [ ] configure complete POM metadata
- [ ] decide Central publishing path and wire CI secrets
- [ ] decide Plugin Portal publishing path for the Gradle plugin
- [ ] add Javadocs to every public type and key method
- [ ] remove or hide accidental public API
- [ ] fix README/plugin/doc inconsistencies
- [ ] update stale example versions
- [ ] add issue templates / PR template / code of conduct
- [ ] enable GitHub Pages
- [ ] add a release workflow for tags
- [ ] add a "consume the published artifact" smoke test

## Recommended execution order

Do this in order:

### Phase 1: irreversible decisions

- namespace
- branding
- package names
- plugin ids
- license

### Phase 2: public API polish

- Javadocs
- API surface review
- docs simplification
- example cleanup

### Phase 3: publishing mechanics

- Maven Central publishing
- signing
- release workflow
- Plugin Portal publishing

### Phase 4: public launch polish

- GitHub templates
- Pages/docs live
- first release notes
- announcement-quality README

## My recommendation

Do **not** wire Maven Central publication first.

Do these first:

1. fix namespace / branding
2. add `LICENSE`
3. tighten the public API and Javadocs
4. clean the README and plugin story

Only then set up Maven Central and Plugin Portal publication.

That order gives you a much better chance that the first public version feels deliberate instead of "technically published, but still in private-project shape."
