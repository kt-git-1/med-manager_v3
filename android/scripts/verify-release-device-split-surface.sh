#!/usr/bin/env bash

set -euo pipefail

if (( $# != 3 )); then
  echo "Usage: scripts/verify-release-device-split-surface.sh bundletool-classpath app.aab output-directory" >&2
  exit 2
fi

readonly BUNDLETOOL_CLASSPATH="$1"
readonly AAB_PATH="$2"
readonly OUTPUT_DIR="$3"
readonly BUNDLETOOL_MAIN="com.android.tools.build.bundletool.BundleToolMain"
readonly TEST_PASSWORD="synthetic-device-splits-only"
readonly TEST_ALIAS="synthetic-device-splits"
readonly PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "$BUNDLETOOL_CLASSPATH" || ! -f "$AAB_PATH" ]]; then
  echo "Bundletool classpath and generated Release AAB are required." >&2
  exit 2
fi
for command_name in java keytool unzip; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required for the device split-surface gate." >&2
    exit 2
  fi
done

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_DIR" && -f "$PROJECT_DIR/local.properties" ]]; then
  SDK_DIR="$(sed -n 's/^sdk.dir=//p' "$PROJECT_DIR/local.properties" | tail -1)"
fi
AAPT2=""
APKSIGNER=""
ZIPALIGN=""
APKANALYZER=""
if [[ -n "$SDK_DIR" && -d "$SDK_DIR/build-tools" ]]; then
  AAPT2="$(find "$SDK_DIR/build-tools" -mindepth 2 -maxdepth 2 -type f -name aapt2 | sort | tail -1)"
  APKSIGNER="$(find "$SDK_DIR/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner | sort | tail -1)"
  ZIPALIGN="$(find "$SDK_DIR/build-tools" -mindepth 2 -maxdepth 2 -type f -name zipalign | sort | tail -1)"
  APKANALYZER="$SDK_DIR/cmdline-tools/latest/bin/apkanalyzer"
fi
if [[ -z "$AAPT2" || ! -x "$AAPT2" || -z "$APKSIGNER" || ! -x "$APKSIGNER" ||
  -z "$ZIPALIGN" || ! -x "$ZIPALIGN" || ! -x "$APKANALYZER" ]]; then
  echo "aapt2, apksigner, zipalign or apkanalyzer is unavailable in the configured Android SDK." >&2
  exit 2
fi

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/medmanager-device-splits.XXXXXX")"
staged_output="${OUTPUT_DIR}.tmp"
trap 'rm -rf "$work_dir" "$staged_output"' EXIT
rm -rf "$OUTPUT_DIR" "$staged_output"
mkdir -p "$staged_output"

keytool -genkeypair \
  -alias "$TEST_ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -dname "CN=Synthetic Device Splits, O=Med Manager Test, C=JP" \
  -keystore "$work_dir/device-splits.jks" \
  -storetype JKS \
  -storepass "$TEST_PASSWORD" \
  -keypass "$TEST_PASSWORD" \
  -noprompt >/dev/null 2>&1

java -cp "$BUNDLETOOL_CLASSPATH" "$BUNDLETOOL_MAIN" build-apks \
  --bundle="$AAB_PATH" \
  --output="$work_dir/full.apks" \
  --ks="$work_dir/device-splits.jks" \
  --ks-pass="pass:$TEST_PASSWORD" \
  --ks-key-alias="$TEST_ALIAS" \
  --key-pass="pass:$TEST_PASSWORD" \
  --aapt2="$AAPT2" \
  --overwrite >/dev/null

expected_certificate="$(
  LC_ALL=C keytool -exportcert -rfc \
    -keystore "$work_dir/device-splits.jks" \
    -storepass "$TEST_PASSWORD" \
    -alias "$TEST_ALIAS" 2>/dev/null
)"
normalize_certificate() {
  printf '%s\n' "$1" | sed '/^-----/d' | tr -d '\r\n'
}

verify_native_alignment() {
  local apk="$1"
  local extraction_root="$2"
  rm -rf "$extraction_root"
  mkdir -p "$extraction_root"
  unzip -qq "$apk" 'lib/*.so' 'lib/*/*.so' -d "$extraction_root" 2>/dev/null || true
  if ! find "$extraction_root" -type f -name '*.so' | grep -q .; then
    return
  fi
  local objdump
  objdump="$(command -v llvm-objdump || command -v objdump || true)"
  if [[ -z "$objdump" ]]; then
    echo "objdump is required to verify native ELF LOAD alignment." >&2
    exit 1
  fi
  while IFS= read -r library; do
    if ! "$objdump" -p "$library" | awk '
      $1 == "LOAD" {
        split($NF, exponent, "\\*\\*")
        if (exponent[2] + 0 < 14) exit 1
        count += 1
      }
      END { if (count == 0) exit 1 }
    '; then
      echo "Native library is not 16 KB LOAD-aligned: ${library#"$extraction_root"/}" >&2
      exit 1
    fi
  done < <(find "$extraction_root" -type f -name '*.so' | sort)
}

verify_selected_apks() {
  local label="$1"
  local selected_dir="$2"
  local expected_abi="$3"
  local expected_density="$4"
  local expected_language="$5"

  printf 'Selected APKs for %s:' "$label"
  while IFS= read -r selected_apk; do
    printf ' %s' "$(basename "$selected_apk")"
  done < <(find "$selected_dir" -type f -name '*.apk' | sort)
  printf '\n'

  python3 "$PROJECT_DIR/scripts/verify-device-split-set.py" \
    "$selected_dir" "$expected_abi" "$expected_density" "$expected_language" \
    --report="$staged_output/$label/report.json"

  local base_apk=""
  while IFS= read -r apk; do
    local signer_output certificate_count actual_certificate application_id
    if ! signer_output="$(LC_ALL=C "$APKSIGNER" verify --print-certs-pem "$apk" 2>/dev/null)"; then
      echo "Selected split signature verification failed: $apk" >&2
      exit 1
    fi
    certificate_count="$(printf '%s\n' "$signer_output" | grep -c '^-----BEGIN CERTIFICATE-----$')"
    actual_certificate="$(
      printf '%s\n' "$signer_output" |
        sed -n '/^-----BEGIN CERTIFICATE-----$/,/^-----END CERTIFICATE-----$/p'
    )"
    if [[ "$certificate_count" != "1" ]] ||
      [[ "$(normalize_certificate "$actual_certificate")" != "$(normalize_certificate "$expected_certificate")" ]]; then
      echo "Selected split does not use the ephemeral device-split certificate: $apk" >&2
      exit 1
    fi
    "$ZIPALIGN" -c -P 16 -v 4 "$apk" >/dev/null
    application_id="$($APKANALYZER manifest application-id "$apk")"
    if [[ "$application_id" != "com.afterlifearchive.medmanager" ]]; then
      echo "Unexpected split applicationId: $application_id" >&2
      exit 1
    fi
    verify_native_alignment "$apk" "$work_dir/native-$label-$(basename "$apk" .apk)"
    if [[ "$(basename "$apk")" =~ ^base-master(_[1-9][0-9]*)?\.apk$ ]]; then
      base_apk="$apk"
    fi
  done < <(find "$selected_dir" -type f -name '*.apk' | sort)

  if [[ -z "$base_apk" ]]; then
    echo "Selected split set has no base master APK." >&2
    exit 1
  fi
  bash "$PROJECT_DIR/scripts/verify-release-apk.sh" "$base_apk"
  cp -R "$selected_dir/." "$staged_output/$label/"
  echo "Device split-surface verification passed: $label abi=$expected_abi density=$expected_density language=$expected_language"
}

declare -a LABELS=("api26-arm64-xhdpi" "api33-x86_64-xxhdpi" "api35-a302sh-ja")
declare -a ABIS=("arm64-v8a" "x86_64" "arm64-v8a")
declare -a DENSITIES=("xhdpi" "xxhdpi" "xhdpi")
declare -a LANGUAGES=("ja" "ja" "ja")
declare -a SPECS=(
  '{"supportedAbis":["arm64-v8a"],"supportedLocales":["ja-JP"],"screenDensity":320,"sdkVersion":26}'
  '{"supportedAbis":["x86_64"],"supportedLocales":["ja-JP"],"screenDensity":480,"sdkVersion":33}'
  '{"supportedAbis":["arm64-v8a","armeabi-v7a","armeabi"],"supportedLocales":["ja-JP"],"screenDensity":280,"sdkVersion":35}'
)

for index in "${!LABELS[@]}"; do
  label="${LABELS[$index]}"
  spec="$work_dir/$label.json"
  selected="$work_dir/$label"
  printf '%s\n' "${SPECS[$index]}" > "$spec"
  java -cp "$BUNDLETOOL_CLASSPATH" "$BUNDLETOOL_MAIN" extract-apks \
    --apks="$work_dir/full.apks" \
    --device-spec="$spec" \
    --output-dir="$selected" >/dev/null
  mkdir -p "$staged_output/$label"
  cp "$spec" "$staged_output/$label/device-spec.json"
  verify_selected_apks \
    "$label" "$selected" "${ABIS[$index]}" "${DENSITIES[$index]}" "${LANGUAGES[$index]}"
done

mv "$staged_output" "$OUTPUT_DIR"
echo "Release AAB device split-surface verification passed."
echo "DEVICE_SPECS=3 SELECTED_APKS_PER_SPEC=4 APK_SIGNER=synthetic-test-only"
echo "OUTPUT_DIR=$OUTPUT_DIR"
