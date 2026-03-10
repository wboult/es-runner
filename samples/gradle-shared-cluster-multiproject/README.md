# Shared cluster multi-project sample

This sample shows a normal consumer Gradle build that uses the ES Runner shared
cluster plugin and helper artifact via published coordinates.

It proves the intended model:

- one shared Elasticsearch 9 cluster per build
- multiple Gradle projects using that same cluster
- multiple suites per project
- suite-level namespace isolation

## Layout

- `app`
- `search`
- root shared-cluster plugin configuration

Each project defines `integrationTest` and `smokeTest`, and all four suites
share one ES node.

## How to run after publication

When the artifacts are published, run from this directory with:

```bash
./gradlew \
  :app:integrationTest \
  :app:smokeTest \
  :search:integrationTest \
  :search:smokeTest \
  -PesRunnerVersion=0.1.0
```

## How to run before publication

Point the build at a Maven repository that contains:

- `io.github.wboult:es-runner`
- `io.github.wboult:es-runner-gradle-test-support`
- plugin marker for `io.github.wboult.es-runner.shared-test-clusters`

Then run:

```bash
./gradlew \
  :app:integrationTest \
  :app:smokeTest \
  :search:integrationTest \
  :search:smokeTest \
  -PesRunnerVersion=0.1.0 \
  -PesRunnerRepositoryUrl=file:///path/to/repo
```

Optional local distro override:

```bash
-PesDistroZip=/path/to/elasticsearch-9.3.1.zip
```
