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
    'status="${FAKE_GRADLE_EXIT:-0}"' \
    'if [[ "$status" != "0" ]]; then exit "$status"; fi' \
    'if [[ "${FAKE_RESULT_MODE:-valid}" == "missing" ]]; then exit 0; fi' \
    'shard=0' \
    'for argument in "$@"; do' \
    '  case "$argument" in *shardIndex=*) shard="${argument##*=}" ;; esac' \
    'done' \
    'tests="$((shard + 3))"' \
    'skipped=0' \
    'if [[ "${FAKE_RESULT_MODE:-valid}" == "skipped" ]]; then skipped=1; fi' \
    'directory="$PWD/app/build/outputs/androidTest-results/connected/debug/flavors/staging"' \
    'mkdir -p "$directory"' \
    'printf "%s\n" "<testsuite tests=\"$tests\" failures=\"0\" errors=\"0\" skipped=\"$skipped\"></testsuite>" > "$directory/TEST-fixture.xml"' > "$root/gradlew"
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
grep -q 'All 1 connected UI shards passed: tests=3 failures=0 errors=0 skipped=0' "$awake_root/output" || \
  fail "awake target did not pass with an aggregate summary"
grep -q 'connectedStagingDebugAndroidTest' "$awake_root/gradle.log" || fail "Staging Gradle shard was not invoked"
grep -q $'1/1\t3\t0\t0\t0\tshard-1-of-1.xml' "$awake_root/app/build/reports/connected-ui-shards/results.tsv" || \
  fail "awake target evidence summary drifted"
[[ -f "$awake_root/app/build/reports/connected-ui-shards/shard-1-of-1.xml" ]] || \
  fail "awake target XML was not preserved"
[[ "$(grep -c '^.*uninstall com.afterlifearchive.medmanager.staging' "$awake_root/adb.log")" == "2" ]] || \
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
[[ "$(grep -c '^.*uninstall com.afterlifearchive.medmanager.staging' "$failure_root/adb.log")" == "2" ]] || \
  fail "both packages were not cleaned after failure"

for result_mode in missing skipped; do
  result_root="$(make_fixture "result-$result_mode")"
  set +e
  run_fixture "$result_root" env FAKE_RESULT_MODE="$result_mode" ANDROID_UI_TEST_SHARDS=1 \
    > "$result_root/output" 2>&1
  status=$?
  set -e
  [[ "$status" == "2" ]] || fail "$result_mode result returned $status instead of 2"
  [[ "$(grep -c '^.*uninstall com.afterlifearchive.medmanager.staging' "$result_root/adb.log")" == "2" ]] || \
    fail "both packages were not cleaned after $result_mode result"
done
grep -q 'Expected exactly one instrumentation XML' "$TEMP_ROOT/result-missing/output" || \
  fail "missing-result rejection is unclear"
grep -q 'is not a complete pass' "$TEMP_ROOT/result-skipped/output" || \
  fail "skipped-result rejection is unclear"

multi_root="$(make_fixture multi)"
run_fixture "$multi_root" env ANDROID_UI_TEST_SHARDS=2 > "$multi_root/output"
grep -q 'All 2 connected UI shards passed: tests=7 failures=0 errors=0 skipped=0' "$multi_root/output" || \
  fail "multi-shard aggregate summary drifted"
[[ "$(find "$multi_root/app/build/reports/connected-ui-shards" -name 'shard-*-of-2.xml' | wc -l | tr -d ' ')" == "2" ]] || \
  fail "multi-shard XML evidence was not preserved"

echo "UI shard runner contract passed: awake=1 dozingRejected=1 lockedRejected=1 unknownRejected=1 failureCleanup=1 missingResultRejected=1 skippedResultRejected=1 multiShardEvidence=2"
