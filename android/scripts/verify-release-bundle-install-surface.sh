#!/usr/bin/env bash

set -euo pipefail

if (( $# != 3 )); then
  echo "Usage: scripts/verify-release-bundle-install-surface.sh bundletool-classpath app.aab output.apk" >&2
  exit 2
fi

readonly BUNDLETOOL_CLASSPATH="$1"
readonly AAB_PATH="$2"
readonly OUTPUT_APK="$3"
readonly BUNDLETOOL_MAIN="com.android.tools.build.bundletool.BundleToolMain"
readonly TEST_PASSWORD="synthetic-install-surface-only"
readonly TEST_ALIAS="synthetic-install-surface"
readonly PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "$BUNDLETOOL_CLASSPATH" || ! -f "$AAB_PATH" ]]; then
  echo "Bundletool classpath and generated Release AAB are required." >&2
  exit 2
fi
for command_name in java keytool; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required for the bundle install-surface gate." >&2
    exit 2
  fi
done

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_DIR" && -f "$PROJECT_DIR/local.properties" ]]; then
  SDK_DIR="$(sed -n 's/^sdk.dir=//p' "$PROJECT_DIR/local.properties" | tail -1)"
fi
AAPT2=""
APKSIGNER=""
if [[ -n "$SDK_DIR" && -d "$SDK_DIR/build-tools" ]]; then
  AAPT2="$(find "$SDK_DIR/build-tools" -mindepth 2 -maxdepth 2 -type f -name aapt2 | sort | tail -1)"
  APKSIGNER="$(find "$SDK_DIR/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner | sort | tail -1)"
fi
if [[ -z "$AAPT2" || ! -x "$AAPT2" || -z "$APKSIGNER" || ! -x "$APKSIGNER" ]]; then
  echo "aapt2 or apksigner is unavailable in the configured Android SDK." >&2
  exit 2
fi

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/medmanager-install-surface.XXXXXX")"
temporary_output="${OUTPUT_APK}.tmp"
trap 'rm -rf "$work_dir"; rm -f "$temporary_output"' EXIT
mkdir -p "$(dirname "$OUTPUT_APK")"
rm -f "$OUTPUT_APK" "$temporary_output"

keytool -genkeypair \
  -alias "$TEST_ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -dname "CN=Synthetic Install Surface, O=Med Manager Test, C=JP" \
  -keystore "$work_dir/install-surface.jks" \
  -storetype JKS \
  -storepass "$TEST_PASSWORD" \
  -keypass "$TEST_PASSWORD" \
  -noprompt >/dev/null 2>&1

java -cp "$BUNDLETOOL_CLASSPATH" "$BUNDLETOOL_MAIN" build-apks \
  --bundle="$AAB_PATH" \
  --output="$work_dir/universal.apks" \
  --mode=universal \
  --ks="$work_dir/install-surface.jks" \
  --ks-pass="pass:$TEST_PASSWORD" \
  --ks-key-alias="$TEST_ALIAS" \
  --key-pass="pass:$TEST_PASSWORD" \
  --aapt2="$AAPT2" \
  --overwrite >/dev/null

python3 "$PROJECT_DIR/scripts/extract-universal-apk.py" \
  "$work_dir/universal.apks" \
  "$work_dir/universal.apk"

expected_fingerprint="$(
  LC_ALL=C keytool -list -v \
    -keystore "$work_dir/install-surface.jks" \
    -storepass "$TEST_PASSWORD" \
    -alias "$TEST_ALIAS" 2>/dev/null |
    awk -F': ' '/SHA256:/{gsub(":", "", $2); print tolower($2); exit}'
)"
actual_fingerprint="$(
  LC_ALL=C "$APKSIGNER" verify --print-certs "$work_dir/universal.apk" 2>/dev/null |
    awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print tolower($2); exit}'
)"
if [[ -z "$actual_fingerprint" || "$actual_fingerprint" != "$expected_fingerprint" ]]; then
  echo "Universal APK does not use the ephemeral install-surface certificate." >&2
  exit 1
fi

bash "$PROJECT_DIR/scripts/verify-release-apk.sh" "$work_dir/universal.apk"
cp "$work_dir/universal.apk" "$temporary_output"
mv "$temporary_output" "$OUTPUT_APK"

echo "Release AAB install-surface verification passed."
echo "APK_SET_MODE=universal APK_SIGNER=synthetic-test-only"
echo "OUTPUT_APK=$OUTPUT_APK"
