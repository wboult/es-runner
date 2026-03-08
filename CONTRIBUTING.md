# Contributing to Elastic Runner

Thanks for helping improve Elastic Runner! This guide covers setup, tests, and pull requests.

## Development setup

```bash
git clone https://github.com/wboult/elastic-runner.git
cd elastic-runner
```

## Build & test

```bash
./gradlew test
./gradlew scala3Test
```

If you need integration tests to run locally, enable downloads:

```bash
export ES_DISTRO_DOWNLOAD=true
export ES_VERSION=9.3.1
```

## Documentation site

Docs are built with Astro Starlight in the `site/` directory.

```bash
cd site
npm install
npm run dev
```

## Making changes

- Keep changes focused and small.
- Add tests for behavior changes.
- Update docs and examples where relevant.
- Follow existing code style.

## Submitting a PR

- Open a pull request on GitHub.
- Describe the change and any breaking impact.
- Ensure all CI checks pass.
- Keep `main` linear: squash merge only, no merge commits.

## Reporting issues

For bugs, open a GitHub issue with:

- Reproduction steps
- Expected vs actual behavior
- Logs or stack traces
- Environment details (JDK, OS, ES version)

## Public release work

If you are working on public-launch or publishing tasks, use
`docs/public-release-readiness.md` as the repo-specific checklist.

