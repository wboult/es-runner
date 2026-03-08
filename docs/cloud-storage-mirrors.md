# Cloud Storage Mirrors

ES Runner can resolve and download Elasticsearch distro archives from more
than plain HTTPS mirrors.

Supported `downloadBaseUrl(...)` schemes:

- `https://`
- `file://`
- `s3://`
- `gs://`
- `az://`

The runner still appends the expected distro filename for the requested
Elasticsearch version and current OS.

Examples:

```java
ElasticRunnerConfig s3Config = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("s3://elastic-mirror/elasticsearch/"));

ElasticRunnerConfig gcsConfig = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("gs://elastic-mirror/elasticsearch/"));

ElasticRunnerConfig azureConfig = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("az://myaccount/releases/elasticsearch/"));

ElasticRunnerConfig fileMirrorConfig = ElasticRunnerConfig.from(builder -> builder
    .version("9.3.1")
    .download(true)
    .downloadBaseUrl("file:///srv/mirrors/elasticsearch/"));
```

## Access configuration

### HTTPS mirrors

Use this for:

- public mirrors
- internal artifact proxies
- signed container URLs such as Azure Blob SAS container URLs

If the base URL has a shared query string, ES Runner preserves that query
when it appends the distro filename.

### Local/shared file mirrors

Use a `file://` URL that points at the directory containing the distro files.

Example:

```text
file:///srv/mirrors/elasticsearch/
```

### S3

ES Runner shells out to:

```text
aws s3 cp
```

Install the AWS CLI and configure access using any standard AWS CLI mechanism:

- `aws configure`
- `AWS_PROFILE`
- `AWS_REGION`
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_SESSION_TOKEN`
- IAM roles on EC2/ECS/EKS

### GCS

ES Runner tries:

```text
gcloud storage cp
```

and falls back to:

```text
gsutil cp
```

Install Google Cloud CLI or `gsutil` and configure access with:

- `gcloud auth login`
- `gcloud auth activate-service-account --key-file=/path/to/key.json`
- your normal Cloud SDK config/profile setup

### Azure Blob

Use the form:

```text
az://<account>/<container>/<prefix>/
```

Example:

```text
az://myaccount/releases/elasticsearch/
```

ES Runner tries:

```text
azcopy copy
```

first, then falls back to:

```text
az storage blob download
```

Recommended access options:

- `azcopy login`
- `azcopy login --identity`
- `az login`
- `AZURE_STORAGE_CONNECTION_STRING`
- `AZURE_STORAGE_KEY`
- `AZURE_STORAGE_SAS_TOKEN`

