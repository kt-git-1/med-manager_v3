#!/usr/bin/env python3
"""Bind an exact C118 Play base APK to its installed physical-device state."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from collections.abc import Mapping, Sequence
from pathlib import Path, PurePosixPath


APP_LINKS_SCRIPT = Path(__file__).with_name("verify-play-installed-app-links.py")
SPEC = importlib.util.spec_from_file_location("play_installed_app_links", APP_LINKS_SCRIPT)
assert SPEC and SPEC.loader
APP_LINKS = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = APP_LINKS
SPEC.loader.exec_module(APP_LINKS)

PACKAGE = APP_LINKS.PACKAGE
PLAY_INSTALLER = APP_LINKS.PLAY_INSTALLER
DOMAIN = APP_LINKS.DOMAIN
MAX_RECEIPT_BYTES = 1024 * 1024
MAX_ADB_TEXT_BYTES = 1024 * 1024
MAX_BASE_APK_BYTES = 256 * 1024 * 1024
COMMIT_SHA = re.compile(r"^[0-9a-f]{40}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
VERSION_NAME = re.compile(r"^[0-9]+(?:\.[0-9]+){1,3}(?:[-+][A-Za-z0-9.-]+)?$")
BUILD_TOOLS_VERSION = re.compile(r"^[0-9]+(?:\.[0-9]+){1,3}(?:-[A-Za-z0-9.-]+)?$")
APK_BASENAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*\.apk$")
VERSION_CODE_ROW = re.compile(r"^\s*versionCode=([1-9][0-9]*)(?:\s+.*)?$")
VERSION_NAME_ROW = re.compile(r"^\s*versionName=(\S+)\s*$")
C118_SUFFIX = ".play-downloaded-base-apks-receipt.json"
C119_SUFFIX = ".play-installed-package-receipt.json"


class InstalledPackageReceiptError(ValueError):
    """Raised when the installed package cannot be bound to C118."""


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _read_stable(path: Path) -> bytes:
    if path.is_symlink() or not path.is_file():
        raise InstalledPackageReceiptError("C118 receipt must be a regular non-symlink file")
    before = path.stat()
    if before.st_size <= 0 or before.st_size > MAX_RECEIPT_BYTES:
        raise InstalledPackageReceiptError("C118 receipt size is invalid")
    value = path.read_bytes()
    after = path.stat()
    if before.st_size != after.st_size or before.st_mtime_ns != after.st_mtime_ns:
        raise InstalledPackageReceiptError("C118 receipt changed while being read")
    return value


def parse_c118_receipt(value: bytes) -> dict[str, object]:
    try:
        receipt = json.loads(value.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise InstalledPackageReceiptError("C118 receipt is not canonical UTF-8 JSON") from error
    if not isinstance(receipt, dict):
        raise InstalledPackageReceiptError("C118 receipt root must be an object")
    required = {
        "schemaVersion",
        "sources",
        "applicationId",
        "handoffDirectory",
        "commitSha",
        "versionName",
        "versionCode",
        "aabSha256",
        "generatedApksReceiptSha256",
        "androidBuildToolsVersion",
        "completeSigningKeyCoverage",
        "downloadedBaseApks",
    }
    if set(receipt) != required:
        raise InstalledPackageReceiptError("C118 receipt fields do not match schema 1")
    if receipt["schemaVersion"] != 1 or receipt["applicationId"] != PACKAGE:
        raise InstalledPackageReceiptError("C118 receipt identity is invalid")
    if receipt["sources"] != [
        "google-play-developer-api-v3-generatedapks.download",
        "c117-play-generated-apks-receipt",
        "android-sdk-apksigner",
        "android-sdk-aapt2",
    ]:
        raise InstalledPackageReceiptError("C118 receipt sources are invalid")
    handoff_directory = receipt["handoffDirectory"]
    if (
        not isinstance(handoff_directory, str)
        or not handoff_directory.endswith(".play-release")
        or Path(handoff_directory).name != handoff_directory
        or len(handoff_directory) > 160
    ):
        raise InstalledPackageReceiptError("C118 handoff directory identity is malformed")
    if receipt["completeSigningKeyCoverage"] is not True:
        raise InstalledPackageReceiptError("C118 receipt lacks complete signing-key coverage")
    if not isinstance(receipt["commitSha"], str) or not COMMIT_SHA.fullmatch(receipt["commitSha"]):
        raise InstalledPackageReceiptError("C118 commit SHA is malformed")
    if not isinstance(receipt["versionCode"], int) or isinstance(receipt["versionCode"], bool) or receipt["versionCode"] <= 0:
        raise InstalledPackageReceiptError("C118 versionCode is malformed")
    if not isinstance(receipt["versionName"], str) or not VERSION_NAME.fullmatch(receipt["versionName"]):
        raise InstalledPackageReceiptError("C118 versionName is malformed")
    if not isinstance(receipt["aabSha256"], str) or not SHA256.fullmatch(receipt["aabSha256"]):
        raise InstalledPackageReceiptError("C118 AAB SHA-256 is malformed")
    if not isinstance(receipt["generatedApksReceiptSha256"], str) or not SHA256.fullmatch(receipt["generatedApksReceiptSha256"]):
        raise InstalledPackageReceiptError("C118 generated-APK receipt SHA-256 is malformed")
    if not isinstance(receipt["androidBuildToolsVersion"], str) or BUILD_TOOLS_VERSION.fullmatch(receipt["androidBuildToolsVersion"]) is None:
        raise InstalledPackageReceiptError("C118 Android Build Tools version is malformed")
    downloaded = receipt["downloadedBaseApks"]
    if not isinstance(downloaded, list) or not 1 <= len(downloaded) <= 8:
        raise InstalledPackageReceiptError("C118 downloaded base APK set is invalid")
    fingerprints: list[str] = []
    apk_hashes: list[str] = []
    for item in downloaded:
        if not isinstance(item, dict) or set(item) != {
            "appSigningCertificateSha256Fingerprint",
            "apkSha256",
            "sizeBytes",
            "zipEntries",
            "dexFiles",
            "verifiedSignatureSchemes",
        }:
            raise InstalledPackageReceiptError("C118 downloaded base APK row is invalid")
        fingerprint = item["appSigningCertificateSha256Fingerprint"]
        apk_hash = item["apkSha256"]
        if not isinstance(fingerprint, str) or APP_LINKS.WEB._COLON_FINGERPRINT.fullmatch(fingerprint) is None:
            raise InstalledPackageReceiptError("C118 app-signing fingerprint is malformed")
        if not isinstance(apk_hash, str) or SHA256.fullmatch(apk_hash) is None:
            raise InstalledPackageReceiptError("C118 base APK SHA-256 is malformed")
        if not isinstance(item["sizeBytes"], int) or isinstance(item["sizeBytes"], bool) or not 1 <= item["sizeBytes"] <= MAX_BASE_APK_BYTES:
            raise InstalledPackageReceiptError("C118 base APK size is invalid")
        if not isinstance(item["zipEntries"], int) or isinstance(item["zipEntries"], bool) or item["zipEntries"] <= 0:
            raise InstalledPackageReceiptError("C118 ZIP entry count is invalid")
        if not isinstance(item["dexFiles"], int) or isinstance(item["dexFiles"], bool) or item["dexFiles"] <= 0:
            raise InstalledPackageReceiptError("C118 DEX count is invalid")
        schemes = item["verifiedSignatureSchemes"]
        if (
            not isinstance(schemes, list)
            or not schemes
            or len(set(schemes)) != len(schemes)
            or schemes != sorted(schemes)
            or "v2" not in schemes
            or not all(isinstance(scheme, str) and scheme in {"v2", "v3", "v3.1", "v4"} for scheme in schemes)
        ):
            raise InstalledPackageReceiptError("C118 signature-scheme set is invalid")
        fingerprints.append(fingerprint)
        apk_hashes.append(apk_hash)
    if len(set(fingerprints)) != len(fingerprints) or fingerprints != sorted(fingerprints):
        raise InstalledPackageReceiptError("C118 app-signing fingerprints are duplicated or unsorted")
    if len(set(apk_hashes)) != len(apk_hashes):
        raise InstalledPackageReceiptError("C118 base APK hashes are duplicated")
    return receipt


def parse_package_dump(output: str) -> tuple[int, str]:
    version_codes = [int(match.group(1)) for line in output.splitlines() if (match := VERSION_CODE_ROW.fullmatch(line))]
    version_names = [match.group(1) for line in output.splitlines() if (match := VERSION_NAME_ROW.fullmatch(line))]
    if len(version_codes) != 1 or len(version_names) != 1:
        raise InstalledPackageReceiptError("installed package version rows are missing or duplicated")
    return version_codes[0], version_names[0]


def parse_package_paths(output: str) -> tuple[str, tuple[str, ...]]:
    paths: list[str] = []
    for line in output.splitlines():
        if not line.startswith("package:"):
            raise InstalledPackageReceiptError("installed APK path output contains an unrecognized row")
        path = line.removeprefix("package:")
        pure = PurePosixPath(path)
        if not path.startswith("/data/app/") or not pure.is_absolute() or ".." in pure.parts:
            raise InstalledPackageReceiptError("installed APK path is outside the package-manager app directory")
        if APK_BASENAME.fullmatch(pure.name) is None:
            raise InstalledPackageReceiptError("installed APK basename is unsafe")
        paths.append(path)
    if not 2 <= len(paths) <= 64 or len(set(paths)) != len(paths):
        raise InstalledPackageReceiptError("installed APK path set is empty, duplicated or too large")
    base_paths = [path for path in paths if PurePosixPath(path).name == "base.apk"]
    if len(base_paths) != 1:
        raise InstalledPackageReceiptError("installed package must expose exactly one base.apk")
    basenames = tuple(sorted(PurePosixPath(path).name for path in paths))
    if len(set(basenames)) != len(basenames):
        raise InstalledPackageReceiptError("installed APK basenames are duplicated")
    return base_paths[0], basenames


def _run(adb: str, serial: str, arguments: Sequence[str], *, binary: bool = False, timeout: float = 30.0) -> str | bytes:
    try:
        completed = subprocess.run([adb, "-s", serial, *arguments], check=False, capture_output=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise InstalledPackageReceiptError("adb command could not execute") from error
    if completed.returncode != 0:
        raise InstalledPackageReceiptError(f"adb command failed ({' '.join(arguments[:2])})")
    limit = MAX_BASE_APK_BYTES if binary else MAX_ADB_TEXT_BYTES
    if len(completed.stdout) > limit or len(completed.stderr) > MAX_ADB_TEXT_BYTES:
        raise InstalledPackageReceiptError("adb output is unexpectedly large")
    if binary:
        if completed.stderr:
            raise InstalledPackageReceiptError("adb base APK stream emitted stderr")
        return completed.stdout
    try:
        return completed.stdout.decode("utf-8").replace("\r\n", "\n")
    except UnicodeDecodeError as error:
        raise InstalledPackageReceiptError("adb text output is not UTF-8") from error


def _resolve_adb(configured: str) -> str | None:
    if configured:
        resolved = shutil.which(configured) if os.path.sep not in configured else configured
        if resolved and Path(resolved).is_file() and os.access(resolved, os.X_OK):
            return resolved
    return APP_LINKS._resolve_adb("")


def _write_atomic(output: Path, receipt: Mapping[str, object], c118_path: Path) -> None:
    if not c118_path.name.endswith(C118_SUFFIX):
        raise InstalledPackageReceiptError(f"C118 receipt name must end with {C118_SUFFIX}")
    expected_name = c118_path.name.removesuffix(C118_SUFFIX) + C119_SUFFIX
    if output.name != expected_name or output.resolve() == c118_path.resolve():
        raise InstalledPackageReceiptError(f"installed-package receipt must be named {expected_name}")
    if output.parent.is_symlink() or (output.exists() and (output.is_symlink() or not output.is_file())):
        raise InstalledPackageReceiptError("installed-package receipt output path is unsafe")
    encoded = (json.dumps(receipt, ensure_ascii=True, indent=2, sort_keys=True) + "\n").encode("utf-8")
    if output.exists():
        if output.read_bytes() == encoded:
            return
        raise InstalledPackageReceiptError("installed-package receipt already exists with different content")
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=f".{output.name}.", dir=output.parent)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(encoded)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, output)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def verify_installed_package_receipt(c118_path: Path, adb: str, serial: str, output: Path, wait_seconds: float) -> dict[str, object]:
    if not serial or len(serial) > 128 or any(character.isspace() for character in serial):
        raise InstalledPackageReceiptError("set one bounded ANDROID_SERIAL for the Play test device")
    if serial.startswith("emulator-"):
        raise InstalledPackageReceiptError("Play installed-package receipt requires a physical device")
    if wait_seconds < 0 or wait_seconds > 60:
        raise InstalledPackageReceiptError("wait time must be between 0 and 60 seconds")
    initial_c118 = _read_stable(c118_path)
    c118 = parse_c118_receipt(initial_c118)
    devices = APP_LINKS._connected_devices(adb)
    if devices.get(serial) != "device":
        raise InstalledPackageReceiptError("requested physical device is not connected and authorized")

    api_text = str(_run(adb, serial, ["shell", "getprop", "ro.build.version.sdk"])).strip()
    qemu = str(_run(adb, serial, ["shell", "getprop", "ro.kernel.qemu"])).strip()
    manufacturer = str(_run(adb, serial, ["shell", "getprop", "ro.product.manufacturer"])).strip()
    model = str(_run(adb, serial, ["shell", "getprop", "ro.product.model"])).strip()
    if not api_text.isdigit() or int(api_text) < 31:
        raise InstalledPackageReceiptError("installed-package receipt requires physical API 31 or newer")
    if qemu == "1" or not manufacturer or not model or len(manufacturer) > 80 or len(model) > 80:
        raise InstalledPackageReceiptError("device identity is missing or identifies an emulator")
    if any(ord(character) < 32 for character in manufacturer + model):
        raise InstalledPackageReceiptError("device identity contains control characters")

    def snapshot() -> tuple[int, str, str, tuple[str, ...], bytes]:
        installer = str(_run(adb, serial, ["shell", "cmd", "package", "list", "packages", "-i", PACKAGE]))
        APP_LINKS.validate_installer(installer)
        package_dump = str(_run(adb, serial, ["shell", "dumpsys", "package", PACKAGE]))
        version_code, version_name = parse_package_dump(package_dump)
        if version_code != c118["versionCode"] or version_name != c118["versionName"]:
            raise InstalledPackageReceiptError("installed package version does not match C118")
        path_output = str(_run(adb, serial, ["shell", "pm", "path", PACKAGE]))
        base_path, basenames = parse_package_paths(path_output)
        base_bytes = _run(adb, serial, ["exec-out", "cat", base_path], binary=True, timeout=60.0)
        assert isinstance(base_bytes, bytes)
        return version_code, version_name, base_path, basenames, base_bytes

    before = snapshot()
    base_hash = sha256_bytes(before[4])
    matching_rows = [item for item in c118["downloadedBaseApks"] if item["apkSha256"] == base_hash and item["sizeBytes"] == len(before[4])]
    if len(matching_rows) != 1:
        raise InstalledPackageReceiptError("installed base.apk bytes do not match exactly one C118 base APK")

    expected = tuple(item["appSigningCertificateSha256Fingerprint"] for item in c118["downloadedBaseApks"])
    _run(adb, serial, ["shell", "pm", "verify-app-links", "--re-verify", PACKAGE])
    deadline = time.monotonic() + wait_seconds
    while True:
        state = str(_run(adb, serial, ["shell", "pm", "get-app-links", PACKAGE]))
        try:
            APP_LINKS.validate_app_links_state(state, expected)
            break
        except APP_LINKS.WEB.ContractError as error:
            if time.monotonic() >= deadline:
                raise InstalledPackageReceiptError(str(error)) from error
            time.sleep(min(2.0, max(0.0, deadline - time.monotonic())))

    after = snapshot()
    if before[:4] != after[:4] or before[4] != after[4]:
        raise InstalledPackageReceiptError("installed package changed during verification")
    final_c118 = _read_stable(c118_path)
    if final_c118 != initial_c118 or parse_c118_receipt(final_c118) != c118:
        raise InstalledPackageReceiptError("C118 receipt changed during installed-package verification")

    receipt: dict[str, object] = {
        "schemaVersion": 1,
        "sources": ["c118-play-downloaded-base-apks-receipt", "android-package-manager", "android-domain-verification"],
        "applicationId": PACKAGE,
        "commitSha": c118["commitSha"],
        "versionName": c118["versionName"],
        "versionCode": c118["versionCode"],
        "aabSha256": c118["aabSha256"],
        "downloadedBaseApksReceiptSha256": sha256_bytes(initial_c118),
        "installedBaseApkSha256": base_hash,
        "installedBaseApkSizeBytes": len(before[4]),
        "matchedAppSigningCertificateSha256Fingerprint": matching_rows[0]["appSigningCertificateSha256Fingerprint"],
        "completeAppSigningCertificateSha256Fingerprints": list(expected),
        "installerPackageName": PLAY_INSTALLER,
        "installedApkCount": len(before[3]),
        "installedSplitApkCount": len(before[3]) - 1,
        "appLinks": {DOMAIN: "verified"},
        "device": {"apiLevel": int(api_text), "manufacturer": manufacturer, "model": model},
    }
    _write_atomic(output, receipt, c118_path)
    return receipt


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Bind C118 base APK bytes to a Play-installed physical package.")
    parser.add_argument("--downloaded-base-apks-receipt", type=Path, required=True)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", ""))
    parser.add_argument("--adb", default=os.environ.get("ADB", ""))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--wait-seconds", type=float, default=30.0)
    args = parser.parse_args(argv)
    try:
        adb = _resolve_adb(args.adb)
        if not adb:
            raise InstalledPackageReceiptError("adb executable was not found")
        receipt = verify_installed_package_receipt(args.downloaded_base_apks_receipt, adb, args.serial, args.output, args.wait_seconds)
    except (InstalledPackageReceiptError, APP_LINKS.WEB.ContractError, OSError) as error:
        print(f"Play-installed package receipt failed: {error}", file=sys.stderr)
        return 1
    print(f"Play-installed package receipt verified: {args.output}")
    print(f"VERSION_CODE={receipt['versionCode']} INSTALLED_APKS={receipt['installedApkCount']} APP_LINKS=verified")
    print(f"COMMIT={receipt['commitSha']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
