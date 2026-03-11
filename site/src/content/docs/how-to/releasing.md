---
title: Release ES Runner
description: Verify and publish the Maven artifacts and Gradle plugin with the tag-driven release workflow.
---

ES Runner now has a real release pipeline for the publishable modules:

- `io.github.wboult:es-runner`
- `io.github.wboult:es-runner-java-client`
- `io.github.wboult:es-runner-gradle-test-support`
- `io.github.wboult:es-runner-gradle-plugin`
- Gradle plugin id `io.github.wboult.es-runner.shared-test-clusters`

Experimental embedded modules are intentionally excluded from publication.

## What the workflow does

The release workflow lives at `.github/workflows/release.yml`.

For a real tag such as `v0.1.0`, it:

1. verifies all publishable artifacts with `publishReleasePublicationsToMavenLocal`
2. validates the Gradle plugin metadata
3. publishes signed Maven artifacts through Sonatype's OSSRH staging compatibility service
4. finalizes the upload into the Central Portal
5. publishes the Gradle plugin to the Gradle Plugin Portal
6. creates a GitHub release from the tag

`workflow_dispatch` is verification-only. Use it to rehearse the publication
steps for a version without actually publishing anything.

## Required GitHub secrets

Add these repository secrets before the first live release:

- `CENTRAL_NAMESPACE`
- `CENTRAL_PORTAL_USERNAME`
- `CENTRAL_PORTAL_PASSWORD`
- `SIGNING_KEY`
- `SIGNING_PASSWORD`
- `GRADLE_PUBLISH_KEY`
- `GRADLE_PUBLISH_SECRET`

Notes:

- `SIGNING_KEY` should be the ASCII-armored private key content used for
  artifact signatures.
- `CENTRAL_PORTAL_USERNAME` / `CENTRAL_PORTAL_PASSWORD` should be a Central
  user token.
- `CENTRAL_NAMESPACE` should match the namespace claimed in Central, such as
  `io.github.wboult`.

## Dry-run locally

Use this before tagging anything:

```bash
./gradlew -PreleaseVersion=0.1.0 publishReleasePublicationsToMavenLocal :es-runner-gradle-plugin:validatePlugins
```

That proves:

- the Maven artifacts can be built and published
- the generated POM metadata is valid
- the Gradle plugin metadata is valid

## Dry-run on GitHub

Run the `Release` workflow manually with `workflow_dispatch` and a version such
as `0.1.0`.

That executes the same verification steps as a real tag release, but skips
Central and Plugin Portal publication.

## Publish a real release

1. Make sure the target commit is already on `main`.
2. Create and push a tag like `v0.1.0`.
3. Watch the `Release` workflow to completion.
4. Verify the published coordinates and plugin id from a consumer build.

Example:

```bash
git tag v0.1.0
git push origin v0.1.0
```

## Why Central uses the staging compatibility service

ES Runner still publishes Maven artifacts through Gradle's `maven-publish`
plugin. Sonatype's staging compatibility service accepts those uploads and then
requires one finalize call from the same runner after the upload finishes.

The workflow handles that finalize call through:

- `scripts/release/finalize-central-upload.sh`

## Verify consumption afterward

After the first real release, validate both consumption paths:

- Maven Central dependencies
- the Plugin Portal plugin id

Use `samples/gradle-shared-cluster-multiproject/` as the consumer smoke test.
