# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- OpenSearch process-backed parity — `DistroFamily.OPENSEARCH` with verified
  lines `3.5.0` and `2.19.4` (#30).
- Experimental embedded JVM runners for Elasticsearch 8/9 and OpenSearch 2/3
  under `experimental/embedded/` (#26, #28).
- Official Java API Client adapter module `es-runner-java-client` (#24).
- Startup failure diagnostics file with log tail, effective config, and
  remediation hints (#31).
- Gradle shared test cluster plugin with suite-level namespace isolation,
  lazy cluster startup, and `ElasticGradleTestEnv` helper (#27, #29).
- Shared-cluster best-practices guide (#33).
- Published Gradle consumer sample and automation harness sample (#34, #38).
- Performance guidance docs covering startup cost, shared-cluster savings,
  and cache/mirror tuning (#36).
- Compatibility and support policy docs (#35).
- Real plugin-install integration test coverage (#40).
- Realistic process-backed indexing workflow coverage (#39).
- Reliability regression tests (#32).
- Windows CI smoke coverage for Elasticsearch `9.3.1` (#44).
- Tag-driven release pipeline for Maven Central and Gradle Plugin Portal (#37).
- Documentation site (Astro Starlight) with Diataxis layout and sidebar
  ordering from simple to advanced (#46).
- README badges for CI status, docs, and MIT license (#42).
- Kotlin example test coverage.
- Programmatic distro resolution helper.

### Changed
- Renamed package from `com.elastic.runner` to `io.github.wboult.esrunner` and
  product name from "Elastic Runner" to "ES Runner" (#22, #23).
- Decomposed `ElasticServer` internals — extracted `ElasticProcessRuntime` for
  process lifecycle management (#45).
- Removed CLI-based cloud storage downloads (s3://, gs://, az:// schemes);
  replaced with pre-signed URL guidance and extension plan (#21).
- Improved UX, process reliability, and concurrency fixes (#19, #20).
- Expanded README examples, contributor onboarding, and positioning docs
  (#41, #42, #43).

## [0.1.0] - 2026-01-18
### Added
- Initial ES Runner API.
- Java/Scala examples and integration support.
