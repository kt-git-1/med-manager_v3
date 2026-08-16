#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import zipfile


FORBIDDEN_NAMES = {
    ".env",
    "google-services.json",
    "service-account.json",
    "service_account.json",
}
FORBIDDEN_EXTENSIONS = {"jks", "keystore", "p12", "pem", "key"}
DEX_PATTERN = re.compile(r"classes(?:[2-9]|[1-9][0-9]+)?\.dex$")
MASTER_APK_PATTERN = re.compile(r"base-master(?:_[1-9][0-9]*)?\.apk$")


class SplitSetError(RuntimeError):
    pass


def _atomic_json(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f"{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as temporary:
            temporary_name = temporary.name
            json.dump(value, temporary, ensure_ascii=False, sort_keys=True, indent=2)
            temporary.write("\n")
            temporary.flush()
            os.fsync(temporary.fileno())
        os.replace(temporary_name, path)
    except BaseException:
        if temporary_name:
            Path(temporary_name).unlink(missing_ok=True)
        raise


def validate_split_set(
    root: Path,
    expected_abi: str,
    expected_density: str,
    expected_language: str,
) -> dict[str, object]:
    if not root.is_dir() or root.is_symlink():
        raise SplitSetError("Selected split root must be a real directory")
    for path in root.rglob("*"):
        if path.is_symlink():
            raise SplitSetError("Selected split set must not contain symlinks")

    apks = sorted(root.rglob("*.apk"))
    expected_configuration_names = {
        f"base-{expected_abi.replace('-', '_')}.apk",
        f"base-{expected_density}.apk",
        f"base-{expected_language}.apk",
    }
    names = [path.name for path in apks]
    master_names = [name for name in names if MASTER_APK_PATTERN.fullmatch(name)]
    configuration_names = set(names) - set(master_names)
    if (
        len(names) != 4
        or len(names) != len(set(names))
        or len(master_names) != 1
        or configuration_names != expected_configuration_names
    ):
        raise SplitSetError("Selected split set must contain exactly master, ABI, density and language APKs")
    master_name = master_names[0]

    dex_counts: dict[str, int] = {}
    native_entries: list[str] = []
    for apk in apks:
        try:
            with zipfile.ZipFile(apk) as archive:
                entries = archive.namelist()
                if len(entries) != len(set(entries)):
                    raise SplitSetError(f"{apk.name} contains duplicate ZIP entries")
                if "AndroidManifest.xml" not in entries:
                    raise SplitSetError(f"{apk.name} is missing AndroidManifest.xml")
                corrupt_entry = archive.testzip()
                if corrupt_entry is not None:
                    raise SplitSetError(f"{apk.name} contains a corrupt entry: {corrupt_entry}")
        except (OSError, zipfile.BadZipFile) as error:
            raise SplitSetError(f"{apk.name} is not a valid APK archive") from error

        dex_counts[apk.name] = sum(1 for entry in entries if DEX_PATTERN.fullmatch(entry))
        native_entries.extend(entry for entry in entries if entry.startswith("lib/") and entry.endswith(".so"))
        for entry in entries:
            file_name = entry.rsplit("/", 1)[-1].lower()
            extension = file_name.rsplit(".", 1)[-1] if "." in file_name else ""
            if file_name in FORBIDDEN_NAMES or extension in FORBIDDEN_EXTENSIONS:
                raise SplitSetError(f"Forbidden private configuration/key entry: {entry}")

    if dex_counts[master_name] < 1:
        raise SplitSetError("Selected base master APK must contain primary DEX")
    if any(count != 0 for name, count in dex_counts.items() if name != master_name):
        raise SplitSetError("Configuration splits must not contain DEX")
    if not native_entries:
        raise SplitSetError("Selected ABI split must contain native libraries")
    expected_prefix = f"lib/{expected_abi}/"
    if any(not entry.startswith(expected_prefix) for entry in native_entries):
        raise SplitSetError("Selected split set contains a native library for the wrong ABI")

    return {
        "schemaVersion": 1,
        "expectedAbi": expected_abi,
        "expectedDensity": expected_density,
        "expectedLanguage": expected_language,
        "selectedApks": names,
        "dexFileCount": sum(dex_counts.values()),
        "nativeLibraryCount": len(native_entries),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate one exact base-module device split selection.")
    parser.add_argument("split_root", type=Path)
    parser.add_argument("expected_abi")
    parser.add_argument("expected_density")
    parser.add_argument("expected_language")
    parser.add_argument("--report", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        report = validate_split_set(
            args.split_root,
            args.expected_abi,
            args.expected_density,
            args.expected_language,
        )
        if args.report:
            _atomic_json(args.report, report)
    except (SplitSetError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 1
    print("Device split-set structure verification passed.")
    print(
        f"selectedApks={len(report['selectedApks'])} "
        f"dexFiles={report['dexFileCount']} nativeLibraries={report['nativeLibraryCount']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
