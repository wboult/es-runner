---
title: Use cloud storage mirrors
description: Download Elasticsearch or OpenSearch distros from HTTPS or file mirrors, with guidance for S3, GCS, and Azure Blob via pre-signed URLs.
---

ES Runner can download distro archives from:

- `https://` / `http://`
- `file://`

> **Note:** Native `s3://`, `gs://`, and `az://` URI schemes are not currently
> supported. See the [extension plan](#future-cloud-storage-modules) below.
> In the meantime, use pre-signed / SAS HTTPS URLs as described here.

## HTTPS mirrors

Use `https://` for public and internal mirrors, artifact proxies, and
pre-signed / SAS URLs for private cloud buckets.

```java
ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("https://internal-mirror.example.com/elasticsearch/"));

ElasticRunnerConfig.from(builder -> builder
    .family(DistroFamily.OPENSEARCH)
    .version("3.5.0")
    .download(true)
    .downloadBaseUrl("https://internal-mirror.example.com/opensearch/"));
```

If the base URL includes a shared query string (e.g. a SAS token), ES Runner
preserves it when it appends the distro filename.

## File mirrors

```java
ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("file:///srv/mirrors/elasticsearch/"));

ElasticRunnerConfig.from(builder -> builder
    .family(DistroFamily.OPENSEARCH)
    .version("3.5.0")
    .download(true)
    .downloadBaseUrl("file:///srv/mirrors/opensearch/"));
```

## Cloud storage via pre-signed / SAS URLs

All three major providers support HTTPS pre-signed or SAS URLs that work with
the built-in `https://` downloader — no extra tooling required.

### S3 pre-signed URL

```sh
aws s3 presign s3://bucket/elasticsearch-9.3.1-linux-x86_64.tar.gz \
  --expires-in 3600
```

Pass the resulting `https://…` URL directly via `distroZip`, or as a
`downloadBaseUrl` prefix if you're using the version-based download path.

### GCS signed URL

```sh
gcloud storage sign-url gs://bucket/elasticsearch-9.3.1-linux-x86_64.tar.gz \
  --duration=1h
```

### Azure Blob SAS URL

```sh
az storage blob generate-sas \
  --account-name myaccount \
  --container-name releases \
  --name elasticsearch-9.3.1-linux-x86_64.tar.gz \
  --permissions r \
  --expiry 2025-12-31T00:00:00Z \
  --full-uri
```

Use the resulting `https://<account>.blob.core.windows.net/…?<sas>` URL.

## Future cloud storage modules

Native SDK-based extension modules (`es-runner-s3`, `es-runner-gcs`,
`es-runner-azure`) are planned. Each will be an optional dependency that
brings in only the SDK for the provider you use, registered via
`java.util.ServiceLoader` so the core library stays dependency-free.

See the [cloud storage extension plan](../../../../docs/cloud-storage-extension-plan.md)
for the full design.
