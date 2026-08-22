#!/usr/bin/env bash

set -euo pipefail

if [[ "$(basename "$0")" == "fake-adb" ]]; then
  readonly STATE_FILE="${FAKE_ADB_STATE:?}"
  readonly MODE="${FAKE_ADB_MODE:-normal}"
  if [[ "${1:-}" != "-s" || -z "${2:-}" ]]; then
    exit 2
  fi
  shift 2
  case "${1:-}" in
    get-state)
      echo device
      ;;
    install-multiple)
      : > "$STATE_FILE"
      ;;
    uninstall)
      rm -f "$STATE_FILE"
      echo Success
      ;;
    shell)
      shift
      case "$*" in
        "pm path com.afterlifearchive.medmanager")
          if [[ -f "$STATE_FILE" ]]; then
            echo package:/data/app/base.apk
            echo package:/data/app/split_config.arm64_v8a.apk
            echo package:/data/app/split_config.ja.apk
            if [[ "$MODE" != "short-install" ]]; then
              echo package:/data/app/split_config.xhdpi.apk
            fi
          fi
          ;;
        "getprop ro.build.version.sdk") echo 35 ;;
        "getprop ro.product.cpu.abilist") echo arm64-v8a,armeabi-v7a,armeabi ;;
        "wm density") echo 'Physical density: 280' ;;
        "getprop persist.sys.locale") echo ja-JP ;;
        "getprop ro.product.model") echo A302SH ;;
        "dumpsys package com.afterlifearchive.medmanager")
          echo 'versionCode=1 minSdk=26 targetSdk=35'
          echo 'versionName=1.0.0'
          ;;
        *) exit 2 ;;
      esac
      ;;
    *) exit 2 ;;
  esac
  exit 0
fi

readonly PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly VERIFIER="$PROJECT_DIR/scripts/verify-device-split-install.sh"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/medmanager-device-install-test.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT

ln -s "$(cd "$(dirname "$0")" && pwd)/$(basename "$0")" "$work_dir/fake-adb"
chmod +x "$work_dir/fake-adb"
split_dir="$work_dir/splits"
mkdir -p "$split_dir"
for apk in base-master_2.apk base-arm64_v8a.apk base-ja.apk base-xhdpi.apk; do
  : > "$split_dir/$apk"
done
printf '%s\n' \
  '{"supportedAbis":["arm64-v8a","armeabi-v7a","armeabi"],"supportedLocales":["ja-JP"],"screenDensity":280,"sdkVersion":35}' \
  > "$split_dir/device-spec.json"

state_file="$work_dir/installed"
ADB="$work_dir/fake-adb" FAKE_ADB_STATE="$state_file" \
  bash "$VERIFIER" synthetic-serial "$split_dir" >/dev/null
[[ ! -e "$state_file" ]]

: > "$state_file"
if ADB="$work_dir/fake-adb" FAKE_ADB_STATE="$state_file" \
  bash "$VERIFIER" synthetic-serial "$split_dir" >/dev/null 2>&1; then
  echo "Pre-existing installation unexpectedly passed." >&2
  exit 1
fi
[[ -e "$state_file" ]]
rm -f "$state_file"

if ADB="$work_dir/fake-adb" FAKE_ADB_STATE="$state_file" FAKE_ADB_MODE=short-install \
  bash "$VERIFIER" synthetic-serial "$split_dir" >/dev/null 2>&1; then
  echo "Incomplete installed split set unexpectedly passed." >&2
  exit 1
fi
[[ ! -e "$state_file" ]]

echo "Device split install contract passed (1 accepted, 2 rejected; refusal/cleanup passed)."
