#!/usr/bin/env bash

set -euo pipefail

if (( $# != 1 )); then
  echo "Usage: scripts/verify-upload-keystore.sh path/to/upload-keystore" >&2
  exit 2
fi

readonly KEYSTORE_PATH="$1"
readonly STORE_PASSWORD="${RELEASE_STORE_PASSWORD:-}"
readonly KEY_ALIAS="${RELEASE_KEY_ALIAS:-}"
readonly KEY_PASSWORD="${RELEASE_KEY_PASSWORD:-}"
readonly EXPECTED_FINGERPRINT_RAW="${EXPECTED_UPLOAD_CERT_SHA256:-}"

if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "Upload keystore does not exist." >&2
  exit 2
fi
if [[ -z "$STORE_PASSWORD" || -z "$KEY_ALIAS" || -z "$KEY_PASSWORD" ]]; then
  echo "Upload keystore password, key alias and key password are required." >&2
  exit 2
fi

for command_name in jar jarsigner keytool; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required to verify the upload keystore." >&2
    exit 2
  fi
done

normalize_fingerprint() {
  printf '%s' "$1" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]'
}

expected_fingerprint="$(normalize_fingerprint "$EXPECTED_FINGERPRINT_RAW")"
if [[ ! "$expected_fingerprint" =~ ^[0-9a-f]{64}$ ]]; then
  echo "EXPECTED_UPLOAD_CERT_SHA256 must be a SHA-256 certificate fingerprint." >&2
  exit 2
fi

if ! alias_output="$(
  LC_ALL=C keytool -list -v \
    -keystore "$KEYSTORE_PATH" \
    -storepass "$STORE_PASSWORD" \
    -alias "$KEY_ALIAS" 2>/dev/null
)"; then
  echo "Upload keystore could not be opened or the configured alias does not exist." >&2
  exit 1
fi

if ! grep -Fx 'Entry type: PrivateKeyEntry' >/dev/null <<< "$alias_output"; then
  echo "Configured upload alias is not a private-key entry." >&2
  exit 1
fi

certificate_fingerprints="$(
  printf '%s\n' "$alias_output" |
    awk -F': ' '/SHA256:/{gsub(":", "", $2); print tolower($2)}' |
    sort -u
)"
fingerprint_count="$(printf '%s\n' "$certificate_fingerprints" | sed '/^$/d' | wc -l | tr -d ' ')"
if [[ "$fingerprint_count" != "1" ]]; then
  echo "Expected exactly one SHA-256 certificate fingerprint for the upload alias." >&2
  exit 1
fi
actual_fingerprint="$(printf '%s\n' "$certificate_fingerprints" | sed '/^$/d')"
if [[ "$actual_fingerprint" != "$expected_fingerprint" ]]; then
  echo "Upload keystore certificate does not match EXPECTED_UPLOAD_CERT_SHA256." >&2
  exit 1
fi

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/medmanager-upload-key.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT
printf 'synthetic upload-key proof\n' > "$work_dir/proof.txt"
jar --create --file "$work_dir/unsigned.jar" -C "$work_dir" proof.txt
if ! LC_ALL=C jarsigner \
  -keystore "$KEYSTORE_PATH" \
  -storepass "$STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -signedjar "$work_dir/signed.jar" \
  "$work_dir/unsigned.jar" \
  "$KEY_ALIAS" >/dev/null 2>&1; then
  echo "Configured upload alias could not sign with the supplied key password." >&2
  exit 1
fi
if ! LC_ALL=C jarsigner -verify "$work_dir/signed.jar" >/dev/null 2>&1; then
  echo "Synthetic upload-key signature could not be verified." >&2
  exit 1
fi

echo "Upload keystore verification passed."
echo "UPLOAD_CERT_SHA256=$actual_fingerprint"
