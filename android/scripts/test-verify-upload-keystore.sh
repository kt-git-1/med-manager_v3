#!/usr/bin/env bash

set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly VERIFY_SCRIPT="$ROOT_DIR/scripts/verify-upload-keystore.sh"
readonly TEST_STORE_PASSWORD="synthetic-store-contract"
readonly TEST_KEY_PASSWORD="synthetic-key-contract"
readonly TEST_ALIAS="synthetic-upload"

for command_name in keytool; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required for the upload-keystore verifier contract test." >&2
    exit 2
  fi
done

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/medmanager-upload-key-contract.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT

keytool -genkeypair \
  -alias "$TEST_ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -dname "CN=Synthetic Upload Contract, O=Med Manager Test, C=JP" \
  -keystore "$work_dir/upload.jks" \
  -storetype JKS \
  -storepass "$TEST_STORE_PASSWORD" \
  -keypass "$TEST_KEY_PASSWORD" \
  -noprompt >/dev/null 2>&1

expected_fingerprint="$(
  LC_ALL=C keytool -list -v \
    -keystore "$work_dir/upload.jks" \
    -storepass "$TEST_STORE_PASSWORD" \
    -alias "$TEST_ALIAS" 2>/dev/null |
    awk -F': ' '/SHA256:/{gsub(":", "", $2); print tolower($2); exit}'
)"

verify() {
  RELEASE_STORE_PASSWORD="${1:-$TEST_STORE_PASSWORD}" \
  RELEASE_KEY_ALIAS="${2:-$TEST_ALIAS}" \
  RELEASE_KEY_PASSWORD="${3:-$TEST_KEY_PASSWORD}" \
  EXPECTED_UPLOAD_CERT_SHA256="${4:-$expected_fingerprint}" \
    "$VERIFY_SCRIPT" "${5:-$work_dir/upload.jks}"
}

expect_failure() {
  local label="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    echo "$label unexpectedly passed." >&2
    exit 1
  fi
}

verify >/dev/null
expect_failure "Mismatched upload certificate" \
  verify "$TEST_STORE_PASSWORD" "$TEST_ALIAS" "$TEST_KEY_PASSWORD" "$(printf '0%.0s' {1..64})"
expect_failure "Unknown upload alias" \
  verify "$TEST_STORE_PASSWORD" unknown-alias "$TEST_KEY_PASSWORD" "$expected_fingerprint"
expect_failure "Incorrect store password" \
  verify incorrect-store "$TEST_ALIAS" "$TEST_KEY_PASSWORD" "$expected_fingerprint"
expect_failure "Incorrect key password" \
  verify "$TEST_STORE_PASSWORD" "$TEST_ALIAS" incorrect-key "$expected_fingerprint"
expect_failure "Malformed expected fingerprint" \
  verify "$TEST_STORE_PASSWORD" "$TEST_ALIAS" "$TEST_KEY_PASSWORD" malformed
expect_failure "Missing upload keystore" \
  verify "$TEST_STORE_PASSWORD" "$TEST_ALIAS" "$TEST_KEY_PASSWORD" "$expected_fingerprint" "$work_dir/missing.jks"

keytool -exportcert \
  -keystore "$work_dir/upload.jks" \
  -storepass "$TEST_STORE_PASSWORD" \
  -alias "$TEST_ALIAS" \
  -file "$work_dir/upload.cer" >/dev/null 2>&1
keytool -importcert \
  -alias trusted-only \
  -file "$work_dir/upload.cer" \
  -keystore "$work_dir/trusted-only.jks" \
  -storetype JKS \
  -storepass "$TEST_STORE_PASSWORD" \
  -noprompt >/dev/null 2>&1
expect_failure "Trusted-certificate-only alias" \
  verify "$TEST_STORE_PASSWORD" trusted-only "$TEST_KEY_PASSWORD" "$expected_fingerprint" "$work_dir/trusted-only.jks"

echo "Upload keystore verifier contract passed (1 accepted, 7 rejected)."
