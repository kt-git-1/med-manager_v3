#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$PROJECT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk}"

if [[ ! -f "$APK" ]]; then
  echo "Release APK not found: $APK" >&2
  echo "Run ./gradlew :app:assembleRelease first." >&2
  exit 1
fi

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_DIR" && -f "$PROJECT_DIR/local.properties" ]]; then
  SDK_DIR="$(sed -n 's/^sdk.dir=//p' "$PROJECT_DIR/local.properties" | tail -1)"
fi
if [[ -z "$SDK_DIR" || ! -d "$SDK_DIR" ]]; then
  echo "Android SDK directory is unavailable. Set ANDROID_HOME or sdk.dir." >&2
  exit 1
fi

ZIPALIGN="$(find "$SDK_DIR/build-tools" -mindepth 2 -maxdepth 2 -type f -name zipalign | sort | tail -1)"
APKANALYZER="$SDK_DIR/cmdline-tools/latest/bin/apkanalyzer"
if [[ ! -x "$ZIPALIGN" || ! -x "$APKANALYZER" ]]; then
  echo "zipalign or apkanalyzer is unavailable in the configured Android SDK." >&2
  exit 1
fi

APPLICATION_ID="$($APKANALYZER manifest application-id "$APK")"
MIN_SDK="$($APKANALYZER manifest min-sdk "$APK")"
TARGET_SDK="$($APKANALYZER manifest target-sdk "$APK")"
[[ "$APPLICATION_ID" == "com.afterlifearchive.medmanager" ]] || {
  echo "Unexpected applicationId: $APPLICATION_ID" >&2
  exit 1
}
[[ "$MIN_SDK" == "26" ]] || {
  echo "Unexpected minSdk: $MIN_SDK" >&2
  exit 1
}
[[ "$TARGET_SDK" =~ ^[0-9]+$ && "$TARGET_SDK" -ge 35 ]] || {
  echo "targetSdk must satisfy the current Play minimum (35 or newer): $TARGET_SDK" >&2
  exit 1
}

PERMISSIONS="$($APKANALYZER manifest permissions "$APK")"
for forbidden in \
  com.google.android.gms.permission.AD_ID \
  android.permission.ACCESS_ADSERVICES_AD_ID \
  android.permission.ACCESS_ADSERVICES_ATTRIBUTION \
  android.permission.ACCESS_ADSERVICES_TOPICS \
  com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE
do
  if printf '%s\n' "$PERMISSIONS" | grep -Fqx "$forbidden"; then
    echo "Forbidden advertising/attribution permission found: $forbidden" >&2
    exit 1
  fi
done

"$ZIPALIGN" -c -P 16 -v 4 "$APK" >/dev/null

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
unzip -qq "$APK" 'lib/*.so' 'lib/*/*.so' -d "$TMP_DIR" 2>/dev/null || true

OBJDUMP="$(command -v llvm-objdump || command -v objdump || true)"
if find "$TMP_DIR" -type f -name '*.so' | grep -q .; then
  [[ -n "$OBJDUMP" ]] || {
    echo "objdump is required to verify native ELF LOAD alignment." >&2
    exit 1
  }
  while IFS= read -r library; do
    if "$OBJDUMP" -p "$library" | awk '
      $1 == "LOAD" {
        split($NF, exponent, "\\*\\*")
        if (exponent[2] + 0 < 14) exit 1
        count += 1
      }
      END { if (count == 0) exit 1 }
    '; then
      :
    else
      echo "Native library is not 16 KB LOAD-aligned: ${library#"$TMP_DIR"/}" >&2
      exit 1
    fi
  done < <(find "$TMP_DIR" -type f -name '*.so' | sort)
fi

if command -v shasum >/dev/null 2>&1; then
  SHA256="$(shasum -a 256 "$APK" | awk '{print $1}')"
else
  SHA256="$(sha256sum "$APK" | awk '{print $1}')"
fi

echo "Release APK compatibility verification passed."
echo "applicationId=$APPLICATION_ID minSdk=$MIN_SDK targetSdk=$TARGET_SDK"
echo "16 KB ZIP and native ELF alignment: passed"
echo "Advertising/attribution permission exclusion: passed"
echo "SHA-256=$SHA256"
