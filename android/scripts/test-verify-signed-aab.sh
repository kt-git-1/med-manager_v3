#!/usr/bin/env bash

set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly VERIFY_SCRIPT="$ROOT_DIR/scripts/verify-signed-aab.sh"
readonly TEST_PASSWORD="synthetic-aab-contract-only"
readonly TEST_ALIAS="synthetic-upload"

for command_name in jar jarsigner keytool; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required for the signed-AAB verifier contract test." >&2
    exit 2
  fi
done

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/medmanager-aab-contract.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT

mkdir -p "$work_dir/payload/base/manifest"
printf 'synthetic bundle config\n' > "$work_dir/payload/BundleConfig.pb"
printf 'synthetic manifest\n' > "$work_dir/payload/base/manifest/AndroidManifest.xml"

jar --create --file "$work_dir/unsigned.aab" -C "$work_dir/payload" .
keytool -genkeypair \
  -alias "$TEST_ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -dname "CN=Synthetic AAB Contract, O=Med Manager Test, C=JP" \
  -keystore "$work_dir/synthetic.p12" \
  -storetype PKCS12 \
  -storepass "$TEST_PASSWORD" \
  -keypass "$TEST_PASSWORD" \
  -noprompt >/dev/null 2>&1
jarsigner \
  -keystore "$work_dir/synthetic.p12" \
  -storetype PKCS12 \
  -storepass "$TEST_PASSWORD" \
  -keypass "$TEST_PASSWORD" \
  -signedjar "$work_dir/signed.aab" \
  "$work_dir/unsigned.aab" \
  "$TEST_ALIAS" >/dev/null 2>&1

expected_fingerprint="$(
  LC_ALL=C keytool -list -v \
    -keystore "$work_dir/synthetic.p12" \
    -storetype PKCS12 \
    -storepass "$TEST_PASSWORD" \
    -alias "$TEST_ALIAS" |
    awk -F': ' '/SHA256:/{gsub(":", "", $2); print tolower($2); exit}'
)"

EXPECTED_UPLOAD_CERT_SHA256="$expected_fingerprint" \
  "$VERIFY_SCRIPT" "$work_dir/signed.aab" >/dev/null

if EXPECTED_UPLOAD_CERT_SHA256="$(printf '0%.0s' {1..64})" \
  "$VERIFY_SCRIPT" "$work_dir/signed.aab" >/dev/null 2>&1; then
  echo "Mismatched upload-certificate fingerprint unexpectedly passed." >&2
  exit 1
fi

if EXPECTED_UPLOAD_CERT_SHA256="$expected_fingerprint" \
  "$VERIFY_SCRIPT" "$work_dir/unsigned.aab" >/dev/null 2>&1; then
  echo "Unsigned AAB unexpectedly passed." >&2
  exit 1
fi

cp "$work_dir/signed.aab" "$work_dir/partially-signed.aab"
printf 'unsigned extra entry\n' > "$work_dir/payload/unsigned-extra.txt"
jar --update \
  --file "$work_dir/partially-signed.aab" \
  -C "$work_dir/payload" unsigned-extra.txt
if EXPECTED_UPLOAD_CERT_SHA256="$expected_fingerprint" \
  "$VERIFY_SCRIPT" "$work_dir/partially-signed.aab" >/dev/null 2>&1; then
  echo "Partially signed AAB unexpectedly passed." >&2
  exit 1
fi

echo "Signed AAB verifier contract passed."
