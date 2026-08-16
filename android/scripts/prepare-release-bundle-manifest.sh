#!/usr/bin/env bash

set -euo pipefail

if (( $# != 3 )); then
  echo "Usage: scripts/prepare-release-bundle-manifest.sh bundletool-classpath app.aab output.xml" >&2
  exit 2
fi

readonly BUNDLETOOL_CLASSPATH="$1"
readonly AAB_PATH="$2"
readonly OUTPUT_PATH="$3"
readonly BUNDLETOOL_MAIN="com.android.tools.build.bundletool.BundleToolMain"

if [[ -z "$BUNDLETOOL_CLASSPATH" || ! -f "$AAB_PATH" ]]; then
  echo "Bundletool classpath and generated Release AAB are required." >&2
  exit 2
fi
if ! command -v java >/dev/null 2>&1; then
  echo "java is required to validate the Release AAB." >&2
  exit 2
fi

mkdir -p "$(dirname "$OUTPUT_PATH")"
temporary_output="${OUTPUT_PATH}.tmp"
trap 'rm -f "$temporary_output"' EXIT

java -cp "$BUNDLETOOL_CLASSPATH" "$BUNDLETOOL_MAIN" \
  validate --bundle="$AAB_PATH" >/dev/null
java -cp "$BUNDLETOOL_CLASSPATH" "$BUNDLETOOL_MAIN" \
  dump manifest --bundle="$AAB_PATH" --module=base > "$temporary_output"

if [[ ! -s "$temporary_output" ]]; then
  echo "bundletool did not emit a base-module manifest." >&2
  exit 1
fi
mv "$temporary_output" "$OUTPUT_PATH"

echo "bundletool validation and base-manifest extraction passed."
