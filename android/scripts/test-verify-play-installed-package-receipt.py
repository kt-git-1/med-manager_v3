#!/usr/bin/env python3
"""Contract tests for the C119 Play-installed package receipt."""

from __future__ import annotations

import copy
import importlib.util
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path


SCRIPT = Path(__file__).with_name("verify-play-installed-package-receipt.py")
SPEC = importlib.util.spec_from_file_location("verify_play_installed_package_receipt", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

FINGERPRINT = ":".join(["AB"] * 32)
OTHER_FINGERPRINT = ":".join(["CD"] * 32)
BASE_BYTES = b"C119 exact Play base APK fixture bytes\x00\x01"


def c118_receipt(base_bytes: bytes = BASE_BYTES) -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "sources": [
            "google-play-developer-api-v3-generatedapks.download",
            "c117-play-generated-apks-receipt",
            "android-sdk-apksigner",
            "android-sdk-aapt2",
        ],
        "applicationId": MODULE.PACKAGE,
        "handoffDirectory": "fixture.play-release",
        "commitSha": "a" * 40,
        "versionName": "1.0.6",
        "versionCode": 1,
        "aabSha256": "b" * 64,
        "generatedApksReceiptSha256": "c" * 64,
        "androidBuildToolsVersion": "36.0.0",
        "completeSigningKeyCoverage": True,
        "downloadedBaseApks": [
            {
                "appSigningCertificateSha256Fingerprint": FINGERPRINT,
                "apkSha256": MODULE.sha256_bytes(base_bytes),
                "sizeBytes": len(base_bytes),
                "zipEntries": 10,
                "dexFiles": 1,
                "verifiedSignatureSchemes": ["v2", "v3"],
            }
        ],
    }


def app_links_state(fingerprints: str = FINGERPRINT, domain_state: str = "verified") -> str:
    return (
        f"  {MODULE.PACKAGE}:\n"
        "    ID: 00000000-0000-0000-0000-000000000000\n"
        f"    Signatures: [{fingerprints}]\n"
        "    Domain verification state:\n"
        f"      {MODULE.DOMAIN}: {domain_state}\n"
        "  User 0:\n"
        "    Verification link handling allowed: true\n"
    )


FAKE_ADB = r'''#!/usr/bin/env python3
import os
import pathlib
import sys

package = "com.afterlifearchive.medmanager"
mode = os.environ.get("FAKE_ADB_MODE", "ok")
args = sys.argv[1:]
if args == ["devices"]:
    state = "unauthorized" if mode == "unauthorized" else "device"
    print(f"List of devices attached\nPHYSICAL123\t{state} product:test model:Pixel_Test device:test")
    raise SystemExit(0)
if len(args) < 3 or args[0:2] != ["-s", "PHYSICAL123"]:
    raise SystemExit(2)
command = args[2:]
if command == ["shell", "getprop", "ro.build.version.sdk"]:
    print("30" if mode == "old-api" else "35")
elif command == ["shell", "getprop", "ro.kernel.qemu"]:
    print("1" if mode == "emulator" else "")
elif command == ["shell", "getprop", "ro.product.manufacturer"]:
    print("" if mode == "missing-device" else "Google")
elif command == ["shell", "getprop", "ro.product.model"]:
    print("Pixel Test")
elif command == ["shell", "cmd", "package", "list", "packages", "-i", package]:
    installer = "null" if mode == "wrong-installer" else "com.android.vending"
    print(f"package:{package}  installer={installer}")
elif command == ["shell", "dumpsys", "package", package]:
    code = "2" if mode == "wrong-version" else "1"
    duplicate = "\n  versionCode=1 minSdk=26 targetSdk=35" if mode == "duplicate-version" else ""
    print(f"Packages:\n  versionCode={code} minSdk=26 targetSdk=35{duplicate}\n  versionName=1.0.6")
elif command == ["shell", "pm", "path", package]:
    if mode == "bad-path":
        print("package:/sdcard/base.apk")
    elif mode == "duplicate-base":
        print("package:/data/app/test/base.apk\npackage:/data/app/test2/base.apk")
    else:
        print("package:/data/app/test/base.apk\npackage:/data/app/test/split_config.ja.apk")
elif command == ["shell", "pm", "verify-app-links", "--re-verify", package]:
    pass
elif command == ["shell", "pm", "get-app-links", package]:
    state_file = pathlib.Path(os.environ["FAKE_APP_LINKS_STATE"])
    value = state_file.read_text()
    if mode == "pending-links":
        value = value.replace(": verified", ": none")
    print(value, end="")
elif len(command) == 3 and command[0:2] == ["exec-out", "cat"]:
    value = pathlib.Path(os.environ["FAKE_BASE_APK"]).read_bytes()
    if mode == "wrong-base":
        value = b"wrong bytes"
    if mode == "changing-base":
        counter = pathlib.Path(os.environ["FAKE_COUNTER"])
        count = int(counter.read_text()) if counter.exists() else 0
        counter.write_text(str(count + 1))
        if count:
            value += b"changed"
    sys.stdout.buffer.write(value)
else:
    print(command, file=sys.stderr)
    raise SystemExit(3)
'''


def rejected(label: str, operation) -> None:
    try:
        operation()
    except (MODULE.InstalledPackageReceiptError, MODULE.APP_LINKS.WEB.ContractError):
        return
    raise AssertionError(f"Rejected fixture unexpectedly passed: {label}")


def main() -> None:
    accepted = 0
    rejected_count = 0
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        base = root / "base.apk"
        base.write_bytes(BASE_BYTES)
        state = root / "app-links.txt"
        state.write_text(app_links_state(), encoding="utf-8")
        adb = root / "adb"
        adb.write_text(FAKE_ADB, encoding="utf-8")
        adb.chmod(0o755)
        source = root / "fixture.play-downloaded-base-apks-receipt.json"
        source.write_text(json.dumps(c118_receipt(), sort_keys=True) + "\n", encoding="utf-8")
        output = root / "fixture.play-installed-package-receipt.json"
        env_before = os.environ.copy()
        os.environ.update(
            {
                "FAKE_BASE_APK": str(base),
                "FAKE_APP_LINKS_STATE": str(state),
                "FAKE_COUNTER": str(root / "counter"),
            }
        )
        try:
            parsed = MODULE.parse_c118_receipt(source.read_bytes())
            assert parsed["versionCode"] == 1
            accepted += 1
            assert MODULE.parse_package_dump("  versionCode=1 minSdk=26 targetSdk=35\n  versionName=1.0.6\n") == (1, "1.0.6")
            accepted += 1
            assert MODULE.parse_package_paths("package:/data/app/x/base.apk\npackage:/data/app/x/split_config.ja.apk\n")[1] == ("base.apk", "split_config.ja.apk")
            accepted += 1
            MODULE.APP_LINKS.validate_app_links_state(app_links_state(), (FINGERPRINT,))
            accepted += 1

            receipt = MODULE.verify_installed_package_receipt(source, str(adb), "PHYSICAL123", output, 0)
            assert receipt["installedBaseApkSha256"] == MODULE.sha256_bytes(BASE_BYTES)
            assert receipt["installedApkCount"] == 2
            assert receipt["installedSplitApkCount"] == 1
            assert receipt["installerPackageName"] == MODULE.PLAY_INSTALLER
            encoded = output.read_bytes()
            assert b"PHYSICAL123" not in encoded and b"/data/app" not in encoded
            accepted += 1
            assert MODULE.verify_installed_package_receipt(source, str(adb), "PHYSICAL123", output, 0) == receipt
            accepted += 1

            cli = subprocess.run(
                [sys.executable, str(SCRIPT), "--downloaded-base-apks-receipt", str(source), "--serial", "PHYSICAL123", "--adb", str(adb), "--output", str(output), "--wait-seconds", "0"],
                check=False,
                capture_output=True,
                text=True,
                env=os.environ.copy(),
            )
            assert cli.returncode == 0, cli.stderr
            assert "VERSION_CODE=1 INSTALLED_APKS=2 APP_LINKS=verified" in cli.stdout
            accepted += 1

            receipt_mutations = [
                ("root array", []),
                ("extra field", {**c118_receipt(), "secret": "x"}),
                ("wrong schema", {**c118_receipt(), "schemaVersion": 2}),
                ("wrong package", {**c118_receipt(), "applicationId": "com.example"}),
                ("incomplete keys", {**c118_receipt(), "completeSigningKeyCoverage": False}),
                ("bad commit", {**c118_receipt(), "commitSha": "A" * 40}),
                ("bad version code", {**c118_receipt(), "versionCode": 0}),
                ("bad version name", {**c118_receipt(), "versionName": "latest"}),
                ("bad AAB hash", {**c118_receipt(), "aabSha256": "B" * 64}),
                ("empty APK set", {**c118_receipt(), "downloadedBaseApks": []}),
            ]
            for label, value in receipt_mutations:
                rejected(label, lambda value=value: MODULE.parse_c118_receipt(json.dumps(value).encode()))
                rejected_count += 1

            row_mutations = [
                ("bad fingerprint", "appSigningCertificateSha256Fingerprint", FINGERPRINT.lower()),
                ("bad APK hash", "apkSha256", "d" * 63),
                ("bad APK size", "sizeBytes", 0),
                ("bad ZIP count", "zipEntries", 0),
                ("bad DEX count", "dexFiles", 0),
                ("bad schemes", "verifiedSignatureSchemes", ["v1"]),
            ]
            for label, key, value in row_mutations:
                mutated = c118_receipt()
                mutated["downloadedBaseApks"][0][key] = value
                rejected(label, lambda mutated=mutated: MODULE.parse_c118_receipt(json.dumps(mutated).encode()))
                rejected_count += 1

            second = copy.deepcopy(c118_receipt()["downloadedBaseApks"][0])
            duplicate = c118_receipt()
            duplicate["downloadedBaseApks"].append(second)
            rejected("duplicate key/hash", lambda: MODULE.parse_c118_receipt(json.dumps(duplicate).encode()))
            rejected_count += 1

            parser_cases = [
                ("missing version", lambda: MODULE.parse_package_dump("versionName=1.0.6")),
                ("duplicate version", lambda: MODULE.parse_package_dump("versionCode=1\nversionCode=1\nversionName=1.0.6")),
                ("empty paths", lambda: MODULE.parse_package_paths("")),
                ("relative path", lambda: MODULE.parse_package_paths("package:base.apk")),
                ("traversal path", lambda: MODULE.parse_package_paths("package:/data/app/../base.apk")),
                ("duplicate paths", lambda: MODULE.parse_package_paths("package:/data/app/x/base.apk\npackage:/data/app/x/base.apk")),
                ("missing base", lambda: MODULE.parse_package_paths("package:/data/app/x/split_config.ja.apk")),
                ("unsafe basename", lambda: MODULE.parse_package_paths("package:/data/app/x/base app.apk")),
            ]
            for label, operation in parser_cases:
                rejected(label, operation)
                rejected_count += 1

            for mode in ("unauthorized", "old-api", "emulator", "missing-device", "wrong-installer", "wrong-version", "duplicate-version", "bad-path", "duplicate-base", "wrong-base", "pending-links", "changing-base"):
                os.environ["FAKE_ADB_MODE"] = mode
                mode_output = root / f"mode-{mode}.play-installed-package-receipt.json"
                rejected(mode, lambda mode_output=mode_output: MODULE.verify_installed_package_receipt(source, str(adb), "PHYSICAL123", mode_output, 0))
                rejected_count += 1
                os.environ.pop("FAKE_ADB_MODE", None)
                counter = root / "counter"
                if counter.exists():
                    counter.unlink()

            rejected("emulator serial", lambda: MODULE.verify_installed_package_receipt(source, str(adb), "emulator-5554", root / "x.play-installed-package-receipt.json", 0))
            rejected_count += 1
            rejected("unsafe output name", lambda: MODULE.verify_installed_package_receipt(source, str(adb), "PHYSICAL123", root / "wrong.json", 0))
            rejected_count += 1
            conflicting = root / "conflict.play-downloaded-base-apks-receipt.json"
            conflicting.write_text(source.read_text(), encoding="utf-8")
            conflict_output = root / "conflict.play-installed-package-receipt.json"
            conflict_output.write_text("{}\n", encoding="utf-8")
            rejected("conflicting output", lambda: MODULE.verify_installed_package_receipt(conflicting, str(adb), "PHYSICAL123", conflict_output, 0))
            rejected_count += 1
        finally:
            os.environ.clear()
            os.environ.update(env_before)

    assert accepted == 7
    assert rejected_count == 40
    print(f"Play-installed package receipt contract passed: accepted={accepted} rejected={rejected_count}")


if __name__ == "__main__":
    main()
