# Releasing ES Runner

This repo now has a real publication pipeline for the publishable modules:

- `io.github.wboult:es-runner`
- `io.github.wboult:es-runner-java-client`
- `io.github.wboult:es-runner-gradle-test-support`
- `io.github.wboult:es-runner-gradle-plugin`
- Gradle plugin id `io.github.wboult.es-runner.shared-test-clusters`

Experimental embedded modules are intentionally excluded from publication.

## What the release workflow does

`.github/workflows/release.yml` is tag-driven.

For a real release tag such as `v0.1.0`, it:

1. verifies the publications locally with `publishReleasePublicationsToMavenLocal`
2. validates the Gradle plugin metadata with `:es-runner-gradle-plugin:validatePlugins`
3. publishes signed Maven artifacts through Sonatype's OSSRH staging compatibility service
4. finalizes the upload into the Central Publisher Portal
5. publishes the Gradle plugin to the Gradle Plugin Portal
6. creates a GitHub release from the tag

`workflow_dispatch` is intentionally verification-only. Use it to rehearse a
release version without publishing anything.

## Required GitHub secrets

Add these repository secrets before the first real tag release:

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
- `CENTRAL_PORTAL_USERNAME` / `CENTRAL_PORTAL_PASSWORD` should be a Sonatype
  Central user token, not your account password.
- `CENTRAL_NAMESPACE` should match the namespace claimed in the Central Portal,
  for example `io.github.wboult`.

## Dry-run the release locally

Use this before tagging anything:

```bash
./gradlew -PreleaseVersion=0.1.0 publishReleasePublicationsToMavenLocal :es-runner-gradle-plugin:validatePlugins
```

This checks:

- all publishable Maven artifacts can be built and published
- generated POM metadata is complete
- the Gradle plugin metadata is valid

## Dry-run the release workflow on GitHub

Use the `Release` workflow with `workflow_dispatch` and a version such as
`0.1.0`.

That runs the same local verification steps as the real release workflow, but
does not publish to Central or the Plugin Portal.

## Cut a real release

1. Make sure the target commit is already on `main`.
2. Create and push a tag like `v0.1.0`.
3. Watch the `Release` workflow to completion.
4. Verify:
   - the GitHub release exists
   - the Maven Central coordinates resolve
   - the Plugin Portal plugin resolves
   - the sample build in `samples/gradle-shared-cluster-multiproject/` works
     against the published version

Example:

```bash
git tag v0.1.0
git push origin v0.1.0
```

## Why Central uses the OSSRH staging compatibility service

Gradle still publishes Maven artifacts through `maven-publish`. Sonatype's
OSSRH staging compatibility service accepts those uploads and then requires one
manual finalize call from the same runner after the upload finishes.

The workflow performs that finalize step via:

- `scripts/release/finalize-central-upload.sh`

## Consumer verification after the first publish

After the first real release, verify both consumption paths:

- library coordinates from Maven Central
- Gradle plugin id from the Plugin Portal

The checked-in sample at `samples/gradle-shared-cluster-multiproject/` is the
intended smoke test for that.
