#!/usr/bin/env bash

set -euo pipefail

readonly APP_PACKAGE="com.afterlifearchive.medmanager"
readonly TEST_PACKAGE="com.afterlifearchive.medmanager.test"
readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly RESULT_ROOT="$ROOT_DIR/app/build/reports/connected-ui-shards"

usage() {
  cat <<'EOF'
Usage: scripts/run-connected-ui-shards.sh [adb-serial]

Runs the complete connected Debug UI suite in bounded AndroidJUnitRunner shards.
The target must not already contain the app or test package. Both packages are
removed when the command exits, including after a failed shard.
The target must remain awake and unlocked before every shard; the runner never
changes lock-screen or device power settings on the user's behalf.

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

require_interactive_target() {
  local power_state window_state
  power_state="$($adb_bin -s "$serial" shell dumpsys power 2>/dev/null || true)"
  if ! grep -Eq 'mWakefulness=(Awake|AWAKE)' <<<"$power_state"; then
    echo "The selected adb target must be awake before running UI tests. Wake and unlock it, then retry." >&2
    return 2
  fi

  window_state="$($adb_bin -s "$serial" shell dumpsys window 2>/dev/null || true)"
  if grep -Eq \
    'mDreamingLockscreen=true|mShowingLockscreen=true|isKeyguardShowing=true|mInputRestricted=true' \
    <<<"$window_state"; then
    echo "The selected adb target must be unlocked before running UI tests. Unlock it, then retry." >&2
    return 2
  fi
  if ! grep -Eq \
    'mDreamingLockscreen=false|mShowingLockscreen=false|isKeyguardShowing=false|mInputRestricted=false' \
    <<<"$window_state"; then
    echo "The selected adb target unlock state could not be confirmed. Unlock it and verify adb access, then retry." >&2
    return 2
  fi
}

require_interactive_target
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
rm -rf "$RESULT_ROOT"
mkdir -p "$RESULT_ROOT"
printf 'shard\ttests\tfailures\terrors\tskipped\txml\n' > "$RESULT_ROOT/results.tsv"

xml_attribute() {
  local file="$1"
  local attribute="$2"
  sed -n "s/.* ${attribute}=\"\([0-9][0-9]*\)\".*/\1/p" "$file" | head -n 1
}

total_tests=0
first_shard=0
last_shard="$SHARD_COUNT"
if [[ -n "$REQUESTED_SHARD" ]]; then
  first_shard="$REQUESTED_SHARD"
  last_shard="$((REQUESTED_SHARD + 1))"
fi
for (( shard = first_shard; shard < last_shard; shard += 1 )); do
  require_interactive_target
  echo "Running connected UI shard $((shard + 1))/$SHARD_COUNT"
  ANDROID_SERIAL="$serial" ./gradlew :app:connectedDebugAndroidTest \
    --no-configuration-cache \
    "-Pandroid.testInstrumentationRunnerArguments.numShards=$SHARD_COUNT" \
    "-Pandroid.testInstrumentationRunnerArguments.shardIndex=$shard"

  result_directory="$ROOT_DIR/app/build/outputs/androidTest-results/connected/debug"
  result_files="$(find "$result_directory" -maxdepth 1 -type f -name 'TEST-*.xml' -print 2>/dev/null || true)"
  result_count="$(printf '%s\n' "$result_files" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [[ "$result_count" != "1" ]]; then
    echo "Expected exactly one instrumentation XML after shard $((shard + 1)); found $result_count." >&2
    exit 2
  fi
  result_file="$(printf '%s\n' "$result_files" | sed '/^$/d' | head -n 1)"
  tests="$(xml_attribute "$result_file" tests)"
  failures="$(xml_attribute "$result_file" failures)"
  errors="$(xml_attribute "$result_file" errors)"
  skipped="$(xml_attribute "$result_file" skipped)"
  for value in "$tests" "$failures" "$errors" "$skipped"; do
    if [[ ! "$value" =~ ^[0-9]+$ ]]; then
      echo "Instrumentation XML has an invalid summary after shard $((shard + 1))." >&2
      exit 2
    fi
  done
  if (( tests < 1 || failures != 0 || errors != 0 || skipped != 0 )); then
    echo "Instrumentation XML is not a complete pass after shard $((shard + 1)): tests=$tests failures=$failures errors=$errors skipped=$skipped." >&2
    exit 2
  fi
  preserved_name="shard-$((shard + 1))-of-$SHARD_COUNT.xml"
  cp "$result_file" "$RESULT_ROOT/$preserved_name"
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$((shard + 1))/$SHARD_COUNT" "$tests" "$failures" "$errors" "$skipped" "$preserved_name" \
    >> "$RESULT_ROOT/results.tsv"
  total_tests="$((total_tests + tests))"
  echo "Connected UI shard $((shard + 1))/$SHARD_COUNT evidence preserved: tests=$tests failures=0 errors=0 skipped=0"
done

if [[ -n "$REQUESTED_SHARD" ]]; then
  echo "Connected UI shard $((REQUESTED_SHARD + 1))/$SHARD_COUNT passed: tests=$total_tests evidence=$RESULT_ROOT"
else
  echo "All $SHARD_COUNT connected UI shards passed: tests=$total_tests failures=0 errors=0 skipped=0 evidence=$RESULT_ROOT"
fi
