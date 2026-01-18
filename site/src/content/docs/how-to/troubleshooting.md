---
title: Troubleshooting
description: Diagnose common failures and recover quickly.
---

# Troubleshooting

This guide helps you resolve common issues when starting Elasticsearch with Elastic Runner.

## Distro ZIP not found

**Symptom**: `ElasticRunnerException: Distro archive does not exist`

**Fix**:
- Provide an explicit ZIP path with `.distroZip(...)`, or
- Enable downloads with `.download(true)` and ensure network access, or
- Set `ES_DISTRO_DOWNLOAD=true` in CI.

## Download fails

**Symptom**: `Failed to download distro: ... (HTTP 404)`

**Fix**:
- Verify the version exists.
- Confirm your `downloadBaseUrl` points to a mirror with the correct filename.

## Port collisions

**Symptom**: Elasticsearch fails to start and logs show `BindException`.

**Fix**:
- Use `.httpPort(0)` (default) and a safe range with `.portRange(9200, 9300)`.
- Ensure each server gets its own `workDir`.

## Process exits early

**Symptom**: `Elasticsearch process exited early` with log tail.

**Fix**:
- Inspect `server.logTail()` for the root cause.
- Check JVM heap settings (`heap`) and disk permissions.

## Cluster won’t form

**Symptom**: Two nodes start but remain separate.

**Fix**:
- Ensure `cluster.name` matches across nodes.
- Use valid `discovery.seed_hosts` with **transport ports**.
- Provide `cluster.initial_master_nodes` on first boot.

## Related

- [Error messages reference](../../reference/errors/)
- [Configuration reference](../../reference/configuration/)
