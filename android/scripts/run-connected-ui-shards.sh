#!/usr/bin/env bash

set -euo pipefail

readonly APP_PACKAGE="com.afterlifearchive.medmanager"
readonly TEST_PACKAGE="com.afterlifearchive.medmanager.test"
readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage: scripts/run-connected-ui-shards.sh [adb-serial]

Runs the complete connected Debug UI suite in bounded AndroidJUnitRunner shards.
The target must not already contain the app or test package. Both packages are
removed when the command exits, including after a failed shard.

Environment:
  ANDROID_UI_TEST_SHARDS  shard count (default: 4, range: 1..16)
  ANDROID_UI_TEST_SHARD_INDEX
                          optional zero-based single shard to rerun
  ADB                     absolute adb path override
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi
if (( $# > 1 )); then
  usage >&2
  exit 2
fi

readonly SHARD_COUNT="${ANDROID_UI_TEST_SHARDS:-4}"
if [[ ! "$SHARD_COUNT" =~ ^[0-9]+$ ]] || (( SHARD_COUNT < 1 || SHARD_COUNT > 16 )); then
  echo "ANDROID_UI_TEST_SHARDS must be an integer from 1 to 16." >&2
  exit 2
fi
readonly REQUESTED_SHARD="${ANDROID_UI_TEST_SHARD_INDEX:-}"
if [[ -n "$REQUESTED_SHARD" ]] && {
  [[ ! "$REQUESTED_SHARD" =~ ^[0-9]+$ ]] || (( REQUESTED_SHARD >= SHARD_COUNT ));
}; then
  echo "ANDROID_UI_TEST_SHARD_INDEX must be from 0 to $((SHARD_COUNT - 1))." >&2
  exit 2
fi

sdk_dir=""
if [[ -f "$ROOT_DIR/local.properties" ]]; then
  sdk_dir="$(awk -F= '$1 == "sdk.dir" {sub(/^[[:space:]]*/, "", $2); print $2; exit}' "$ROOT_DIR/local.properties")"
fi

adb_bin="${ADB:-}"
if [[ -z "$adb_bin" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
  adb_bin="$ANDROID_SDK_ROOT/platform-tools/adb"
fi
if [[ -z "$adb_bin" && -n "${ANDROID_HOME:-}" ]]; then
  adb_bin="$ANDROID_HOME/platform-tools/adb"
fi
if [[ -z "$adb_bin" && -n "$sdk_dir" ]]; then
  adb_bin="$sdk_dir/platform-tools/adb"
fi
if [[ -z "$adb_bin" ]]; then
  adb_bin="$(command -v adb || true)"
fi
if [[ -z "$adb_bin" || ! -x "$adb_bin" ]]; then
  echo "adb was not found. Set ADB, ANDROID_SDK_ROOT, ANDROID_HOME, or sdk.dir." >&2
  exit 2
fi

serial="${1:-}"
if [[ -z "$serial" ]]; then
  devices="$($adb_bin devices | awk 'NR > 1 && $2 == "device" {print $1}')"
  device_count="$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [[ "$device_count" != "1" ]]; then
    echo "Exactly one authorized device is required when no adb serial is supplied." >&2
    exit 2
  fi
  serial="$(printf '%s\n' "$devices" | sed '/^$/d' | head -n 1)"
fi

if [[ "$($adb_bin -s "$serial" get-state 2>/dev/null || true)" != "device" ]]; then
  echo "The selected adb target is not ready." >&2
  exit 2
fi
for package_name in "$APP_PACKAGE" "$TEST_PACKAGE"; do
  if $adb_bin -s "$serial" shell pm path "$package_name" 2>/dev/null | grep -q '^package:'; then
    echo "Refusing to overwrite an existing $package_name installation. Use a disposable target." >&2
    exit 2
  fi
done

cleanup() {
  $adb_bin -s "$serial" uninstall "$TEST_PACKAGE" >/dev/null 2>&1 || true
  $adb_bin -s "$serial" uninstall "$APP_PACKAGE" >/dev/null 2>&1 || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

cd "$ROOT_DIR"
first_shard=0
last_shard="$SHARD_COUNT"
if [[ -n "$REQUESTED_SHARD" ]]; then
  first_shard="$REQUESTED_SHARD"
  last_shard="$((REQUESTED_SHARD + 1))"
fi
for (( shard = first_shard; shard < last_shard; shard += 1 )); do
  echo "Running connected UI shard $((shard + 1))/$SHARD_COUNT"
  ANDROID_SERIAL="$serial" ./gradlew :app:connectedDebugAndroidTest \
    --no-configuration-cache \
    "-Pandroid.testInstrumentationRunnerArguments.numShards=$SHARD_COUNT" \
    "-Pandroid.testInstrumentationRunnerArguments.shardIndex=$shard"
done

if [[ -n "$REQUESTED_SHARD" ]]; then
  echo "Connected UI shard $((REQUESTED_SHARD + 1))/$SHARD_COUNT passed."
else
  echo "All $SHARD_COUNT connected UI shards passed."
fi
