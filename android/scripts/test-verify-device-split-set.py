#!/usr/bin/env python3

from io import BytesIO
import importlib.util
from pathlib import Path
import sys
import tempfile
import warnings
import zipfile


sys.dont_write_bytecode = True
SCRIPT_PATH = Path(__file__).with_name("verify-device-split-set.py")
SPEC = importlib.util.spec_from_file_location("verify_device_split_set", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def apk_bytes(entries: list[tuple[str, bytes]]) -> bytes:
    output = BytesIO()
    with warnings.catch_warnings():
        warnings.filterwarnings("ignore", message="Duplicate name:")
        with zipfile.ZipFile(output, mode="w") as archive:
            for name, payload in entries:
                archive.writestr(name, payload)
    return output.getvalue()


def write_valid(root: Path) -> None:
    root.mkdir(parents=True, exist_ok=True)
    (root / "base-master.apk").write_bytes(
        apk_bytes([("AndroidManifest.xml", b"manifest"), ("classes.dex", b"dex")])
    )
    (root / "base-arm64_v8a.apk").write_bytes(
        apk_bytes([("AndroidManifest.xml", b"manifest"), ("lib/arm64-v8a/libfixture.so", b"elf")])
    )
    (root / "base-xhdpi.apk").write_bytes(
        apk_bytes([("AndroidManifest.xml", b"manifest"), ("resources.arsc", b"resources")])
    )
    (root / "base-ja.apk").write_bytes(
        apk_bytes([("AndroidManifest.xml", b"manifest"), ("resources.arsc", b"resources")])
    )


def expect_failure(label: str, action) -> None:
    try:
        action()
    except MODULE.SplitSetError:
        return
    raise AssertionError(f"{label} unexpectedly passed")


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="medmanager-device-splits-test.") as directory:
        root = Path(directory)
        valid = root / "valid"
        write_valid(valid)
        report_path = root / "report.json"
        report = MODULE.validate_split_set(valid, "arm64-v8a", "xhdpi", "ja")
        MODULE._atomic_json(report_path, report)
        assert report["selectedApks"] == [
            "base-arm64_v8a.apk",
            "base-ja.apk",
            "base-master.apk",
            "base-xhdpi.apk",
        ]
        assert report_path.is_file()
        assert not list(root.glob("*.tmp"))

        suffixed = root / "valid-suffixed-master"
        write_valid(suffixed)
        (suffixed / "base-master.apk").rename(suffixed / "base-master_2.apk")
        suffixed_report = MODULE.validate_split_set(suffixed, "arm64-v8a", "xhdpi", "ja")
        assert "base-master_2.apk" in suffixed_report["selectedApks"]

        fixtures: dict[str, callable] = {
            "missing master": lambda path: (path / "base-master.apk").unlink(),
            "extra apk": lambda path: (path / "base-extra.apk").write_bytes(
                apk_bytes([("AndroidManifest.xml", b"manifest")])
            ),
            "wrong abi name": lambda path: (path / "base-arm64_v8a.apk").rename(path / "base-x86_64.apk"),
            "wrong density": lambda path: (path / "base-xhdpi.apk").rename(path / "base-xxhdpi.apk"),
            "wrong language": lambda path: (path / "base-ja.apk").rename(path / "base-en.apk"),
            "missing dex": lambda path: (path / "base-master.apk").write_bytes(
                apk_bytes([("AndroidManifest.xml", b"manifest")])
            ),
            "dex in config": lambda path: (path / "base-xhdpi.apk").write_bytes(
                apk_bytes([("AndroidManifest.xml", b"manifest"), ("classes.dex", b"dex")])
            ),
            "wrong native abi": lambda path: (path / "base-arm64_v8a.apk").write_bytes(
                apk_bytes([("AndroidManifest.xml", b"manifest"), ("lib/x86_64/libfixture.so", b"elf")])
            ),
            "missing native": lambda path: (path / "base-arm64_v8a.apk").write_bytes(
                apk_bytes([("AndroidManifest.xml", b"manifest")])
            ),
            "duplicate entry": lambda path: (path / "base-master.apk").write_bytes(
                apk_bytes(
                    [
                        ("AndroidManifest.xml", b"first"),
                        ("AndroidManifest.xml", b"second"),
                        ("classes.dex", b"dex"),
                    ]
                )
            ),
        }
        for label, mutate in fixtures.items():
            fixture = root / label.replace(" ", "-")
            write_valid(fixture)
            mutate(fixture)
            expect_failure(
                label,
                lambda path=fixture: MODULE.validate_split_set(path, "arm64-v8a", "xhdpi", "ja"),
            )

    print("Device split-set contract passed (2 accepted, 10 rejected; atomic report passed).")


if __name__ == "__main__":
    main()
