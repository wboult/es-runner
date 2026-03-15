# Try Embedded Runners (Experimental)

The embedded runners are not the main product path, but they are available for
experimentation under `experimental/embedded/`.

## When this makes sense

Try the embedded modules when you specifically want:

- one search node inside the current JVM
- lower startup overhead than a child process on repeated local runs
- experiments around JVM-level integration or internals

Do not start here if you want the stable path for tests or tooling. Use the
process-backed runner or one of the shared Gradle plugins first.

## Available modules

- `es-runner-embedded-8`
- `es-runner-embedded-9`
- `es-runner-embedded-opensearch-2`
- `es-runner-embedded-opensearch-3`

## Typical local commands

```bash
./gradlew :es-runner-embedded-8:test
./gradlew :es-runner-embedded-9:test
./gradlew :es-runner-embedded-opensearch-2:test
./gradlew :es-runner-embedded-opensearch-3:test
```

## Important caveats

- the embedded modules are experimental and version-specific
- they may require Java 21 for the ES 9 / OpenSearch 3 lines
- they still rely on staged distro content under a local work directory
- they are intentionally documented as secondary to the process-backed path

For the detailed design, supported lines, and staging model, see
`docs/embedded-jvm-server.md`.
