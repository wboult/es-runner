#!/usr/bin/env bash
set -euo pipefail

: "${CENTRAL_NAMESPACE:?CENTRAL_NAMESPACE must be set}"
: "${CENTRAL_PORTAL_USERNAME:?CENTRAL_PORTAL_USERNAME must be set}"
: "${CENTRAL_PORTAL_PASSWORD:?CENTRAL_PORTAL_PASSWORD must be set}"

central_base_url="${CENTRAL_BASE_URL:-https://ossrh-staging-api.central.sonatype.com}"
manual_upload_url="${central_base_url}/manual/upload/defaultRepository/${CENTRAL_NAMESPACE}"

echo "Finalizing Maven Central upload for namespace ${CENTRAL_NAMESPACE}"

curl \
  --fail-with-body \
  --silent \
  --show-error \
  --user "${CENTRAL_PORTAL_USERNAME}:${CENTRAL_PORTAL_PASSWORD}" \
  --request POST \
  "${manual_upload_url}"
