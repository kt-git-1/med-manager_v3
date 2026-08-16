#!/usr/bin/env python3

from io import BytesIO
import importlib.util
from pathlib import Path
import sys
import tempfile
import warnings
import zipfile


sys.dont_write_bytecode = True
SCRIPT_PATH = Path(__file__).with_name("extract-universal-apk.py")
SPEC = importlib.util.spec_from_file_location("extract_universal_apk", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def zip_bytes(entries: list[tuple[str, bytes]]) -> bytes:
    output = BytesIO()
    with warnings.catch_warnings():
        warnings.filterwarnings("ignore", message="Duplicate name:")
        with zipfile.ZipFile(output, mode="w") as archive:
            for name, payload in entries:
                archive.writestr(name, payload)
    return output.getvalue()


def write_apks(path: Path, entries: list[tuple[str, bytes]]) -> None:
    path.write_bytes(zip_bytes(entries))


def expect_failure(label: str, action) -> None:
    try:
        action()
    except MODULE.ArchiveError:
        return
    raise AssertionError(f"{label} unexpectedly passed")


def main() -> None:
    valid_apk = zip_bytes(
        [
            ("AndroidManifest.xml", b"synthetic manifest"),
            ("classes.dex", b"dex\n035\0synthetic"),
            ("resources.arsc", b"synthetic resources"),
        ]
    )
    with tempfile.TemporaryDirectory(prefix="medmanager-universal-apk-test.") as directory:
        root = Path(directory)
        valid_apks = root / "valid.apks"
        output = root / "universal-test-only.apk"
        write_apks(valid_apks, [("toc.pb", b"toc"), ("universal.apk", valid_apk)])
        MODULE.extract_atomic(valid_apks, output)
        assert output.read_bytes() == valid_apk
        assert not list(root.glob("*.tmp"))

        fixtures = {
            "missing toc": [("universal.apk", valid_apk)],
            "extra split": [("toc.pb", b"toc"), ("universal.apk", valid_apk), ("splits/base-master.apk", valid_apk)],
            "duplicate universal": [("toc.pb", b"toc"), ("universal.apk", valid_apk), ("universal.apk", valid_apk)],
            "empty universal": [("toc.pb", b"toc"), ("universal.apk", b"")],
            "malformed universal": [("toc.pb", b"toc"), ("universal.apk", b"not a zip")],
            "missing primary dex": [
                ("toc.pb", b"toc"),
                ("universal.apk", zip_bytes([("AndroidManifest.xml", b"manifest")])),
            ],
            "missing manifest": [
                ("toc.pb", b"toc"),
                ("universal.apk", zip_bytes([("classes.dex", b"dex\n035\0synthetic")])),
            ],
            "duplicate inner entry": [
                ("toc.pb", b"toc"),
                (
                    "universal.apk",
                    zip_bytes(
                        [
                            ("AndroidManifest.xml", b"manifest"),
                            ("classes.dex", b"first"),
                            ("classes.dex", b"second"),
                        ]
                    ),
                ),
            ],
        }
        for label, entries in fixtures.items():
            fixture = root / f"{label.replace(' ', '-')}.apks"
            write_apks(fixture, entries)
            output.write_bytes(b"preserve-on-rejection")
            expect_failure(label, lambda path=fixture: MODULE.extract_atomic(path, output))
            assert output.read_bytes() == b"preserve-on-rejection"
            assert not list(root.glob("*.tmp"))

    print("Universal APK Set extraction contract passed (1 accepted, 8 rejected; atomic output passed).")


if __name__ == "__main__":
    main()
