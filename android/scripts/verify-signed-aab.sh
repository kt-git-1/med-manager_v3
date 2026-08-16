#!/usr/bin/env bash

set -euo pipefail

if (( $# != 1 )); then
  echo "Usage: scripts/verify-signed-aab.sh path/to/app-release.aab" >&2
  exit 2
fi

readonly AAB_PATH="$1"
readonly EXPECTED_FINGERPRINT_RAW="${EXPECTED_UPLOAD_CERT_SHA256:-}"

if [[ ! -f "$AAB_PATH" ]]; then
  echo "Signed AAB does not exist: $AAB_PATH" >&2
  exit 2
fi

for command_name in jarsigner keytool unzip; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required to verify the signed AAB." >&2
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

aab_entries="$(unzip -Z1 "$AAB_PATH")"
for required_entry in BundleConfig.pb base/manifest/AndroidManifest.xml; do
  if ! grep -Fx "$required_entry" >/dev/null <<< "$aab_entries"; then
    echo "Signed AAB is missing required entry: $required_entry" >&2
    exit 1
  fi
done

if ! verification_output="$(LC_ALL=C jarsigner -verify -verbose -certs "$AAB_PATH" 2>&1)"; then
  echo "Signed AAB JAR signature verification failed." >&2
  exit 1
fi
if ! grep -F 'jar verified.' >/dev/null <<< "$verification_output"; then
  echo "AAB is unsigned or its JAR signature could not be verified." >&2
  exit 1
fi
if grep -F 'This jar contains unsigned entries' >/dev/null <<< "$verification_output"; then
  echo "AAB contains entries not covered by its JAR signature." >&2
  exit 1
fi

if ! certificate_output="$(LC_ALL=C keytool -printcert -jarfile "$AAB_PATH" 2>&1)"; then
  echo "AAB signing certificate could not be read." >&2
  exit 1
fi
certificate_fingerprints="$(
  printf '%s\n' "$certificate_output" |
    awk -F': ' '/SHA256:/{gsub(":", "", $2); print tolower($2)}' |
    sort -u
)"
fingerprint_count="$(printf '%s\n' "$certificate_fingerprints" | sed '/^$/d' | wc -l | tr -d ' ')"
if [[ "$fingerprint_count" != "1" ]]; then
  echo "Expected exactly one AAB signing certificate." >&2
  exit 1
fi
actual_fingerprint="$(printf '%s\n' "$certificate_fingerprints" | sed '/^$/d')"
if [[ "$actual_fingerprint" != "$expected_fingerprint" ]]; then
  echo "AAB signer does not match EXPECTED_UPLOAD_CERT_SHA256." >&2
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  aab_sha256="$(sha256sum "$AAB_PATH" | awk '{print $1}')"
else
  aab_sha256="$(shasum -a 256 "$AAB_PATH" | awk '{print $1}')"
fi

echo "Signed AAB verification passed."
echo "AAB_SHA256=$aab_sha256"
echo "UPLOAD_CERT_SHA256=$actual_fingerprint"
