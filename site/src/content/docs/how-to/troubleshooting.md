---
title: Troubleshooting
description: Diagnose common failures and recover quickly.
---

This guide helps you resolve common issues when starting Elasticsearch or
OpenSearch with ES Runner.

## Distro ZIP not found

**Symptom**: `ElasticRunnerException: Distro archive does not exist`

**Fix**:
- Provide an explicit ZIP path with `.distroZip(...)`, or
- Enable downloads with `.download(true)` and ensure network access, or
- Set `ES_DISTRO_DOWNLOAD=true` / `OPENSEARCH_DISTRO_DOWNLOAD=true` in CI.

## Download fails

**Symptom**: `Failed to download distro: ... (HTTP 404)`

**Fix**:
- Verify the version exists.
- Confirm your `downloadBaseUrl` points to a mirror with the correct filename.
- Check the resolved download URI in `startup-diagnostics.txt`.

## Port collisions

**Symptom**: The node fails to start and logs show `BindException`.

**Fix**:
- Use `.httpPort(0)` (default) and a safe range with `.portRange(9200, 9300)`.
- Ensure each server gets its own `workDir`.
- Inspect `startup-diagnostics.txt` to see the resolved port settings.

## Process exits early

**Symptom**: `Elasticsearch process exited early` or `OpenSearch process
exited early`.

**Fix**:
- Inspect the `startup-diagnostics.txt` path printed in the exception first.
- Inspect `server.logTail()` for the root cause when you still have a server
  handle.
- Check JVM heap settings (`heap`) and disk permissions.

## Startup times out

**Symptom**: `Timed out waiting for Elasticsearch`, `Timed out waiting for
OpenSearch`, or `Timed out waiting for HTTP port bind`.

**Fix**:
- Open `<workDir>/<version>/logs/startup-diagnostics.txt`.
- Check the resolved archive path and download URI in the diagnostics output.
- Increase `startupTimeout` or `heap` if the node is starting slowly.
- Widen the HTTP port range if the machine may already have other local nodes.

## Cluster won't form

**Symptom**: Two nodes start but remain separate.

**Fix**:
- Ensure `cluster.name` matches across nodes.
- Use valid `discovery.seed_hosts` with **transport ports**.
- Provide `cluster.initial_master_nodes` on first boot.

## Related

- [Error messages reference](../../reference/errors/)
- [Configuration reference](../../reference/configuration/)
