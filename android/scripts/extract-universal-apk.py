#!/usr/bin/env python3

from __future__ import annotations

import argparse
from io import BytesIO
import os
from pathlib import Path
import sys
import tempfile
import zipfile


EXPECTED_APKS_ENTRIES = {"toc.pb", "universal.apk"}
REQUIRED_APK_ENTRIES = {"AndroidManifest.xml", "classes.dex"}


class ArchiveError(RuntimeError):
    pass


def universal_apk_bytes(apks_path: Path) -> bytes:
    try:
        with zipfile.ZipFile(apks_path) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)):
                raise ArchiveError("APK Set contains duplicate entries")
            if set(names) != EXPECTED_APKS_ENTRIES:
                raise ArchiveError("APK Set must contain exactly toc.pb and universal.apk")
            payload = archive.read("universal.apk")
    except (OSError, zipfile.BadZipFile, KeyError) as error:
        raise ArchiveError("APK Set could not be read") from error
    if not payload:
        raise ArchiveError("Universal APK is empty")
    try:
        with zipfile.ZipFile(BytesIO(payload)) as universal:
            universal_names = universal.namelist()
            if len(universal_names) != len(set(universal_names)):
                raise ArchiveError("Universal APK contains duplicate entries")
            if not REQUIRED_APK_ENTRIES.issubset(universal_names):
                raise ArchiveError("Universal APK is missing manifest or primary DEX")
            corrupt_entry = universal.testzip()
            if corrupt_entry is not None:
                raise ArchiveError(f"Universal APK contains a corrupt entry: {corrupt_entry}")
    except zipfile.BadZipFile as error:
        raise ArchiveError("Universal APK is not a valid ZIP archive") from error
    return payload


def extract_atomic(apks_path: Path, output_path: Path) -> None:
    payload = universal_apk_bytes(apks_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            dir=output_path.parent,
            prefix=f"{output_path.name}.",
            suffix=".tmp",
            delete=False,
        ) as temporary:
            temporary_name = temporary.name
            temporary.write(payload)
            temporary.flush()
            os.fsync(temporary.fileno())
        os.replace(temporary_name, output_path)
    except BaseException:
        if temporary_name:
            Path(temporary_name).unlink(missing_ok=True)
        raise


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate an APK Set and atomically extract universal.apk.")
    parser.add_argument("apks", type=Path)
    parser.add_argument("output", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        extract_atomic(args.apks, args.output)
    except (ArchiveError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 1
    print("Universal APK Set extraction passed.")
    print("apksEntries=2 requiredApkEntries=2")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
