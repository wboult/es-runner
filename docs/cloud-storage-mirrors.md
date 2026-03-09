# Cloud Storage Mirrors

ES Runner can resolve and download Elasticsearch and OpenSearch distro archives
from HTTPS mirrors and local file paths.

Supported `downloadBaseUrl(...)` schemes:

- `https://`
- `http://`
- `file://`

> **Note:** S3 (`s3://`), GCS (`gs://`), and Azure Blob (`az://`) schemes
> are not currently supported. See the [extension plan](cloud-storage-extension-plan.md)
> for the planned approach. In the meantime, use pre-signed / SAS HTTPS URLs
> (see below).

## HTTPS mirrors

Use this for:

- Public mirrors
- Internal artifact proxies (Artifactory, Nexus, etc.)
- Pre-signed S3 URLs
- Azure Blob SAS URLs
- GCS signed URLs

If the base URL has a shared query string, ES Runner preserves it when
appending the distro filename.

```java
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("https://internal-mirror.example.com/elasticsearch/"));

ElasticRunnerConfig openSearchConfig = ElasticRunnerConfig.from(builder -> builder
    .family(DistroFamily.OPENSEARCH)
    .version("3.5.0")
    .download(true)
    .downloadBaseUrl("https://internal-mirror.example.com/opensearch/"));
```

## Local / shared file mirrors

Use a `file://` URL pointing at the directory containing distro archives.

```java
ElasticRunnerConfig config = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("file:///srv/mirrors/elasticsearch/"));

ElasticRunnerConfig openSearchConfig = ElasticRunnerConfig.from(builder -> builder
    .family(DistroFamily.OPENSEARCH)
    .version("3.5.0")
    .download(true)
    .downloadBaseUrl("file:///srv/mirrors/opensearch/"));
```

## Cloud storage workarounds

Until native SDK-based extension modules exist, all three major providers
support HTTPS pre-signed / SAS URLs that work with the built-in downloader.

### S3

Generate a pre-signed URL:

```sh
aws s3 presign s3://bucket/elasticsearch-9.3.1-linux-x86_64.tar.gz --expires-in 3600
```

Pass the resulting `https://…` URL directly via `distroZip` or as the
`downloadBaseUrl` prefix (without the filename).

### GCS

Generate a signed URL:

```sh
gcloud storage sign-url gs://bucket/elasticsearch-9.3.1-linux-x86_64.tar.gz \
  --duration=1h
```

Use the resulting `https://storage.googleapis.com/…` URL as above.

### Azure Blob

Generate a SAS URL in the portal, or via CLI:

```sh
az storage blob generate-sas \
  --account-name myaccount \
  --container-name releases \
  --name elasticsearch-9.3.1-linux-x86_64.tar.gz \
  --permissions r \
  --expiry 2025-12-31T00:00:00Z \
  --full-uri
```

Use the resulting `https://<account>.blob.core.windows.net/…?<sas>` URL directly.
