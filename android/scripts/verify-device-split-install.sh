#!/usr/bin/env bash

set -euo pipefail

if (( $# != 2 )); then
  echo "Usage: scripts/verify-device-split-install.sh SERIAL selected-split-directory" >&2
  exit 2
fi

readonly SERIAL="$1"
readonly SPLIT_DIR="$2"
readonly PACKAGE="com.afterlifearchive.medmanager"
readonly PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly DEVICE_SPEC="$SPLIT_DIR/device-spec.json"

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_DIR" && -f "$PROJECT_DIR/local.properties" ]]; then
  SDK_DIR="$(sed -n 's/^sdk.dir=//p' "$PROJECT_DIR/local.properties" | tail -1)"
fi
ADB="${ADB:-$SDK_DIR/platform-tools/adb}"
if [[ ! -x "$ADB" || ! -d "$SPLIT_DIR" || ! -f "$DEVICE_SPEC" ]]; then
  echo "adb, the selected split directory and its device spec are required." >&2
  exit 2
fi
if ! "$ADB" -s "$SERIAL" get-state 2>/dev/null | grep -Fxq device; then
  echo "Selected adb device is unavailable or unauthorized: $SERIAL" >&2
  exit 2
fi
existing_paths="$("$ADB" -s "$SERIAL" shell pm path "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
if grep -q '^package:' <<< "$existing_paths"; then
  echo "Refusing to replace an existing Med Manager installation." >&2
  exit 1
fi

IFS='|' read -r expected_sdk expected_abis expected_density expected_locale < <(
  python3 - "$DEVICE_SPEC" <<'PY'
import json
from pathlib import Path
import sys

value = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
print(
    value["sdkVersion"],
    ",".join(value["supportedAbis"]),
    value["screenDensity"],
    value["supportedLocales"][0],
    sep="|",
)
PY
)
actual_sdk="$("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
actual_abis="$("$ADB" -s "$SERIAL" shell getprop ro.product.cpu.abilist | tr -d '\r')"
actual_density="$("$ADB" -s "$SERIAL" shell wm density | tr -d '\r' | sed -n 's/^Physical density: //p')"
actual_locale="$("$ADB" -s "$SERIAL" shell getprop persist.sys.locale | tr -d '\r')"
device_model="$("$ADB" -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
if [[ "$actual_sdk" != "$expected_sdk" || "$actual_abis" != "$expected_abis" ||
  "$actual_density" != "$expected_density" || "$actual_locale" != "$expected_locale" ]]; then
  echo "Connected device does not match the retained device spec." >&2
  exit 1
fi

apks=()
while IFS= read -r apk; do
  apks+=("$apk")
done < <(find "$SPLIT_DIR" -type f -name '*.apk' | sort)
if (( ${#apks[@]} != 4 )); then
  echo "Exactly four selected split APKs are required." >&2
  exit 1
fi

installed=0
cleanup() {
  if (( installed == 1 )); then
    "$ADB" -s "$SERIAL" uninstall "$PACKAGE" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

"$ADB" -s "$SERIAL" install-multiple "${apks[@]}" >/dev/null
installed=1
installed_paths=()
while IFS= read -r installed_path; do
  installed_paths+=("$installed_path")
done < <("$ADB" -s "$SERIAL" shell pm path "$PACKAGE" | tr -d '\r' | sed -n 's/^package://p')
if (( ${#installed_paths[@]} != 4 )); then
  echo "Installed package path count does not match the selected split set." >&2
  exit 1
fi
installed_names="$(
  for installed_path in "${installed_paths[@]}"; do
    basename "$installed_path"
  done | sort
)"
expected_installed_names="$(printf '%s\n' \
  base.apk \
  split_config.arm64_v8a.apk \
  split_config.ja.apk \
  split_config.xhdpi.apk | sort)"
if [[ "$installed_names" != "$expected_installed_names" ]]; then
  echo "Installed split identities do not match base/ABI/language/density selection." >&2
  exit 1
fi
package_dump="$("$ADB" -s "$SERIAL" shell dumpsys package "$PACKAGE")"
if ! grep -Eq 'versionCode=1([[:space:]]|$)' <<< "$package_dump" ||
  ! grep -Eq 'versionName=1\.0\.6([[:space:]]|$)' <<< "$package_dump"; then
  echo "Installed split package version identity is incorrect." >&2
  exit 1
fi

"$ADB" -s "$SERIAL" uninstall "$PACKAGE" >/dev/null
installed=0
remaining_paths="$("$ADB" -s "$SERIAL" shell pm path "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
if grep -q '^package:' <<< "$remaining_paths"; then
  echo "Synthetic split package remains installed after cleanup." >&2
  exit 1
fi

echo "Physical device split install verification passed."
echo "serial=$SERIAL model=$device_model sdk=$actual_sdk abi=arm64-v8a density=$actual_density locale=$actual_locale"
echo "selectedApks=4 versionCode=1 versionName=1.0.6 cleanup=passed"
