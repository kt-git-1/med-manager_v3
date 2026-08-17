#!/usr/bin/env bash

set -euo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/medmanager-ui-runner.XXXXXX")"
trap 'rm -rf "$TEMP_ROOT"' EXIT

fail() {
  echo "UI shard runner contract failed: $*" >&2
  exit 1
}

make_fixture() {
  local name="$1"
  local root="$TEMP_ROOT/$name"
  mkdir -p "$root/scripts"
  cp "$SOURCE_ROOT/scripts/run-connected-ui-shards.sh" "$root/scripts/"
  printf '%s\n' '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'printf "%s\\n" "$*" >> "$FAKE_ADB_LOG"' \
    'if [[ "${1:-}" == "devices" ]]; then' \
    '  printf "List of devices attached\\nFAKE_SERIAL\\tdevice\\n"' \
    '  exit 0' \
    'fi' \
    'shift 2' \
    'case "$*" in' \
    '  get-state) printf "device\\n" ;;' \
    '  "shell dumpsys power")' \
    '    if [[ "${FAKE_DEVICE_STATE:-awake}" == "dozing" ]]; then printf "mWakefulness=Dozing\\n"; else printf "mWakefulness=Awake\\n"; fi ;;' \
    '  "shell dumpsys window")' \
    '    if [[ "${FAKE_DEVICE_STATE:-awake}" == "locked" ]]; then printf "isKeyguardShowing=true mInputRestricted=true\\n";' \
    '    elif [[ "${FAKE_DEVICE_STATE:-awake}" == "unknown" ]]; then printf "window state unavailable\\n";' \
    '    else printf "isKeyguardShowing=false mInputRestricted=false\\n"; fi ;;' \
    '  shell\ pm\ path*) exit 0 ;;' \
    '  uninstall*) printf "Success\\n" ;;' \
    '  *) printf "Unexpected fake adb command: %s\\n" "$*" >&2; exit 91 ;;' \
    'esac' > "$root/fake-adb"
  chmod +x "$root/fake-adb"
  printf '%s\n' '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'printf "%s\\n" "$*" >> "$FAKE_GRADLE_LOG"' \
    'exit "${FAKE_GRADLE_EXIT:-0}"' > "$root/gradlew"
  chmod +x "$root/gradlew"
  printf '%s\n' "$root"
}

run_fixture() {
  local root="$1"
  shift
  FAKE_ADB_LOG="$root/adb.log" \
  FAKE_GRADLE_LOG="$root/gradle.log" \
  ADB="$root/fake-adb" \
  "$@" "$root/scripts/run-connected-ui-shards.sh" FAKE_SERIAL
}

awake_root="$(make_fixture awake)"
run_fixture "$awake_root" env ANDROID_UI_TEST_SHARDS=1 > "$awake_root/output"
grep -q 'All 1 connected UI shards passed.' "$awake_root/output" || fail "awake target did not pass"
grep -q 'connectedDebugAndroidTest' "$awake_root/gradle.log" || fail "Gradle shard was not invoked"
[[ "$(grep -c '^.*uninstall com.afterlifearchive.medmanager' "$awake_root/adb.log")" == "2" ]] || \
  fail "both packages were not cleaned after success"

for state in dozing locked unknown; do
  state_root="$(make_fixture "$state")"
  set +e
  run_fixture "$state_root" env FAKE_DEVICE_STATE="$state" ANDROID_UI_TEST_SHARDS=1 \
    > "$state_root/output" 2>&1
  status=$?
  set -e
  [[ "$status" == "2" ]] || fail "$state target returned $status instead of 2"
  [[ ! -e "$state_root/gradle.log" ]] || fail "$state target reached Gradle"
done
grep -q 'must be awake' "$TEMP_ROOT/dozing/output" || fail "Doze rejection is unclear"
grep -q 'must be unlocked' "$TEMP_ROOT/locked/output" || fail "lock rejection is unclear"
grep -q 'could not be confirmed' "$TEMP_ROOT/unknown/output" || fail "unknown-state rejection is unclear"

failure_root="$(make_fixture failure)"
set +e
run_fixture "$failure_root" env FAKE_GRADLE_EXIT=17 ANDROID_UI_TEST_SHARDS=1 \
  > "$failure_root/output" 2>&1
status=$?
set -e
[[ "$status" == "17" ]] || fail "Gradle failure returned $status instead of 17"
[[ "$(grep -c '^.*uninstall com.afterlifearchive.medmanager' "$failure_root/adb.log")" == "2" ]] || \
  fail "both packages were not cleaned after failure"

echo "UI shard runner contract passed: awake=1 dozingRejected=1 lockedRejected=1 unknownRejected=1 failureCleanup=1"
