# Plan: Initialize Repo and Build an ES 9.2.4 Distro Runner

This plan assumes we will use a local Elasticsearch 9.2.4 ZIP distribution
that you are downloading. The goal is a small, reliable runner that starts and
stops a local ES process for tests or offline tasks.

## 1) Initialize the Git repo

- Run `git init` in the root.
- Add a `.gitignore` (Java, build, IDE, and temp dirs).
- Add a `README.md` describing the runner and how to use it.
- Add a basic project layout (see "Repository layout" below).

## 2) Repository layout

Proposed minimal structure:

```
docs/
  embedded-elasticsearch-history.md
  plan.md
src/
  main/
  test/
```

If we pick Maven:

```
pom.xml
src/main/java/...
src/test/java/...
```

If we pick Gradle:

```
build.gradle
settings.gradle
src/main/java/...
src/test/java/...
```

## 3) MVP functionality (well specced)

**Goal:** Start and stop Elasticsearch 9.2.4 from a ZIP distribution, configure
it for local usage, and expose the HTTP endpoint for tests.

### MVP: Core features

- **Input**: path to the ES 9.2.4 ZIP distribution.
- **Extraction**: unzip into a workspace directory
  (for example `./.es/9.2.4/`).
- **Configuration**: write `config/elasticsearch.yml` with:
  - `discovery.type: single-node`
  - `xpack.security.enabled: false` (for local tests)
  - `cluster.name` set via config or default `local-es`
  - `http.port` set via config or default `9200`
  - `path.data` and `path.logs` inside the workspace
- **Environment**:
  - Set `ES_JAVA_OPTS` to a small heap, e.g. `-Xms256m -Xmx256m`
  - Allow override via config
- **Process management**:
  - Start `bin/elasticsearch` using `ProcessBuilder`
  - Capture and stream stdout/stderr to the console or a log file
  - Write a PID file to `./.es/<version>/es.pid`
- **Readiness check**:
  - Poll `http://localhost:<port>/` until a 200 response is returned
  - Optionally check `/_cluster/health?wait_for_status=yellow`
  - Fail with a clear error after a timeout (e.g., 60s)
- **Shutdown**:
  - Attempt to stop the process gracefully
  - Fallback to `process.destroyForcibly()` on timeout
  - Always delete the PID file on shutdown

### MVP: Public API or CLI

Pick one of these (or both):

- **Library API**:
  - `ElasticRunnerConfig` (zip path, port, cluster name, heap, workdir)
  - `ElasticRunner` with `start()`, `stop()`, `isRunning()`
- **CLI**:
  - `runner start --zip <path> --port 9200 --workdir ./.es`
  - `runner stop --workdir ./.es`

## 4) Good tests (expected to pass)

Split tests into fast unit tests and slower integration tests.
Integration tests require a local ES ZIP path.

### Unit tests (fast)

- **Config writer**: verifies `elasticsearch.yml` contains expected settings.
- **Port selection**: verifies explicit port vs. default port behavior.
- **Path layout**: verifies workdir, data, and logs directories are created.
- **PID file**: verifies PID file is written and removed.

### Integration tests (requires ES ZIP)

Use a system property or env var:
`ES_DISTRO_ZIP=C:\path\to\elasticsearch-9.2.4.zip`

- **Start/stop smoke test**:
  - Start ES
  - Verify `GET /` returns 200
  - Stop ES and verify process exits
- **Health check test**:
  - Verify `/_cluster/health` returns `yellow` or `green`
- **Re-start test**:
  - Start and stop twice to ensure cleanup works
- **Invalid ZIP test**:
  - Invalid zip path should return a clear error

### Test execution

- Unit tests always run.
- Integration tests run only when `ES_DISTRO_ZIP` is set.

## 5) Implementation milestones

1. Bootstrap repo (git init, README, build tool, .gitignore).
2. Implement config + extraction + process start.
3. Implement readiness check and shutdown.
4. Add unit tests.
5. Add integration tests (wired to `ES_DISTRO_ZIP`).
6. Run tests and verify passing.

## 6) Notes for ES 9.2.4 ZIP usage

- Use `discovery.type: single-node` to avoid cluster bootstrap issues.
- Disable security for tests with `xpack.security.enabled: false`.
- Keep heap size small for local use (256m or 512m).
- If the port is already in use, fail with a clear message.

## 7) Expanded scope (2 hours of work)

This section extends the plan with more robust behavior, better ergonomics, and
stronger test coverage. It is still achievable within one focused session.

### Expanded features

- **Automatic port selection**:
  - If `http.port` is not provided, pick a free port in a safe range
    (e.g. 9200-9300) and record it in a state file.
- **State file**:
  - Write `./.es/<version>/state.json` with `pid`, `httpPort`, `workdir`,
    `clusterName`, and `startTime`.
  - Use it for `stop` and `status` commands.
- **Status command**:
  - `runner status --workdir ./.es` prints running state, port, and PID.
- **Log capture**:
  - Stream logs to console and to `./.es/<version>/logs/runner.log`.
  - Allow `--quiet` to disable stdout echo.
- **Config overrides**:
  - Allow `--setting key=value` (repeatable) to inject extra
    `elasticsearch.yml` settings.
- **Plugin installation**:
  - If provided, run `bin/elasticsearch-plugin install` before startup.
- **Windows/Linux support**:
  - Use `bin/elasticsearch.bat` on Windows, `bin/elasticsearch` elsewhere.

### Expanded architecture

- **Core modules**:
  - `distro` (zip extraction + layout)
  - `config` (yml writer, validation, overrides)
  - `process` (start/stop, PID, logs)
  - `health` (readiness probe + timeouts)
  - `cli` (argument parsing and commands)

## 8) Expanded tests (expected to pass)

### Unit tests

- **Port selection**:
  - When port is not provided, it picks a free port within range.
- **State file writer**:
  - Creates JSON with all required fields.
- **Config overrides**:
  - `--setting` values appear in output config.
- **Platform script selection**:
  - Uses `.bat` on Windows and no extension elsewhere.

### Integration tests (requires ES ZIP)

Use `ES_DISTRO_ZIP` environment variable.

- **Status command**:
  - Start ES, call `status`, verify it reports running and correct port.
- **Runner log**:
  - Verify `runner.log` exists and contains ES startup lines.
- **Config override test**:
  - Set `cluster.name` via `--setting`, confirm via `GET /`.
- **Failure behavior**:
  - Use an occupied port and verify a clear error.

## 9) Optional stretch goals (if time allows)

- **Multiple versions**:
  - Allow workdir layout `./.es/<version>/<instance>` so you can run
    different versions side-by-side.
- **Health timeout customization**:
  - `--health-timeout` to tune readiness polling.
- **Cleanup command**:
  - `runner clean --workdir ./.es` removes extracted distros and logs.
