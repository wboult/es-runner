---
title: Use cloud storage mirrors
description: Download Elasticsearch distros from S3, GCS, Azure Blob, HTTPS, or file mirrors.
---

# Use cloud storage mirrors

ES Runner can download distro archives from:

- `https://`
- `file://`
- `s3://`
- `gs://`
- `az://`

The configured base is still a prefix. ES Runner appends the expected
archive filename for the selected version and current OS.

## Examples

```java
ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("s3://elastic-mirror/elasticsearch/"));

ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("gs://elastic-mirror/elasticsearch/"));

ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("az://myaccount/releases/elasticsearch/"));

ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("file:///srv/mirrors/elasticsearch/"));
```

## Access configuration

### S3

ES Runner uses `aws s3 cp`.

Configure access with normal AWS CLI settings such as:

- `aws configure`
- `AWS_PROFILE`
- `AWS_REGION`
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_SESSION_TOKEN`
- IAM roles

### GCS

ES Runner tries `gcloud storage cp` first and `gsutil cp` second.

Configure access with:

- `gcloud auth login`
- `gcloud auth activate-service-account --key-file=/path/to/key.json`
- your normal Cloud SDK configuration

### Azure Blob

ES Runner expects:

```text
az://<account>/<container>/<prefix>/
```

It tries `azcopy copy` first, then `az storage blob download`.

Recommended access options:

- `azcopy login`
- `azcopy login --identity`
- `az login`
- `AZURE_STORAGE_CONNECTION_STRING`
- `AZURE_STORAGE_KEY`
- `AZURE_STORAGE_SAS_TOKEN`

### HTTPS and signed URLs

Use `https://` for:

- public/private mirrors
- artifact proxies
- shared signed container URLs such as Azure SAS URLs

If the base URL includes a shared query string, ES Runner preserves it
when it appends the distro filename.

