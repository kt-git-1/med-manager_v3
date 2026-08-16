#!/usr/bin/env python3
"""Verify App Links on an exact Play-installed physical Android package."""

from __future__ import annotations

import argparse
import importlib.util
import os
import re
import shutil
import subprocess
import sys
import time
from collections.abc import Mapping, Sequence
from pathlib import Path


WEB_CONTRACT_SCRIPT = Path(__file__).with_name("verify-production-app-links.py")
SPEC = importlib.util.spec_from_file_location("production_app_links_contract", WEB_CONTRACT_SCRIPT)
assert SPEC and SPEC.loader
WEB = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = WEB
SPEC.loader.exec_module(WEB)

PACKAGE = WEB.ANDROID_PACKAGE_NAME
DOMAIN = "www.okusuri-mimamori.com"
PLAY_INSTALLER = "com.android.vending"
_DOMAIN_STATE = re.compile(r"^\s+([a-z0-9.-]+):\s+(\S+)\s*$")


def validate_installer(output: str) -> None:
    expected = re.compile(
        rf"^package:{re.escape(PACKAGE)}\s+installer={re.escape(PLAY_INSTALLER)}$"
    )
    if expected.fullmatch(output.strip()) is None:
        raise WEB.ContractError("package must be installed by Google Play (com.android.vending)")


def parse_app_links_state(output: str) -> tuple[tuple[str, ...], Mapping[str, str]]:
    lines = output.splitlines()
    if not lines or lines[0].strip() != f"{PACKAGE}:":
        raise WEB.ContractError(f"pm get-app-links output must identify {PACKAGE}")

    signature_line = next((line.strip() for line in lines if line.strip().startswith("Signatures: [")), "")
    if not signature_line.endswith("]"):
        raise WEB.ContractError("installed package signatures are missing")
    signature_text = signature_line.removeprefix("Signatures: [")[:-1]
    signatures = tuple(item.strip() for item in signature_text.split(",") if item.strip())
    if not signatures:
        raise WEB.ContractError("installed package signatures are empty")

    try:
        state_start = next(index for index, line in enumerate(lines) if line.strip() == "Domain verification state:")
    except StopIteration as error:
        raise WEB.ContractError("domain verification state is missing") from error

    domains: dict[str, str] = {}
    for line in lines[state_start + 1 :]:
        if line.startswith("  User "):
            break
        if not line.strip():
            continue
        match = _DOMAIN_STATE.fullmatch(line)
        if match is None:
            raise WEB.ContractError("domain verification state contains an unrecognized row")
        host, state = match.groups()
        if host in domains:
            raise WEB.ContractError("domain verification state contains a duplicate host")
        domains[host] = state
    return signatures, domains


def validate_app_links_state(output: str, expected_fingerprints: Sequence[str]) -> None:
    signatures, domains = parse_app_links_state(output)
    if not all(WEB._COLON_FINGERPRINT.fullmatch(item) for item in signatures):
        raise WEB.ContractError("installed signatures must be uppercase colon-separated SHA-256 values")
    if len(set(signatures)) != len(signatures):
        raise WEB.ContractError("installed signatures must not contain duplicates")
    if set(signatures) != set(expected_fingerprints):
        raise WEB.ContractError("installed package signatures do not match the Play app-signing set")
    if domains != {DOMAIN: "verified"}:
        raise WEB.ContractError(f"only {DOMAIN} may be declared and it must be verified")


def _run_adb(adb: str, serial: str, arguments: Sequence[str], timeout: float = 15.0) -> str:
    try:
        completed = subprocess.run(
            [adb, "-s", serial, *arguments],
            check=False,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise WEB.ContractError(f"adb command failed to execute ({type(error).__name__})") from error
    if completed.returncode != 0:
        raise WEB.ContractError(f"adb command failed ({' '.join(arguments[:2])})")
    return completed.stdout.replace("\r\n", "\n")


def _connected_devices(adb: str) -> Mapping[str, str]:
    try:
        completed = subprocess.run(
            [adb, "devices"], check=False, capture_output=True, text=True, timeout=10
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise WEB.ContractError(f"adb devices failed ({type(error).__name__})") from error
    if completed.returncode != 0:
        raise WEB.ContractError("adb devices failed")
    devices = {}
    for line in completed.stdout.splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2:
            devices[fields[0]] = fields[1]
    return devices


def _resolve_adb(configured: str) -> str | None:
    candidates: list[str] = []
    if configured:
        candidates.append(configured)
    path_adb = shutil.which("adb")
    if path_adb:
        candidates.append(path_adb)
    for variable in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        if os.environ.get(variable):
            candidates.append(str(Path(os.environ[variable]) / "platform-tools" / "adb"))
    local_properties = WEB_CONTRACT_SCRIPT.parent.parent / "local.properties"
    if local_properties.is_file():
        for line in local_properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                candidates.append(str(Path(line.removeprefix("sdk.dir=")) / "platform-tools" / "adb"))
    for candidate in candidates:
        resolved = shutil.which(candidate) if os.path.sep not in candidate else candidate
        if resolved and Path(resolved).is_file() and os.access(resolved, os.X_OK):
            return resolved
    return None


def _arguments(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify a Play-installed package's signer and domain verification state."
    )
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", ""))
    parser.add_argument("--adb", default=os.environ.get("ADB", ""))
    parser.add_argument(
        "--expected-sha256",
        default=os.environ.get("EXPECTED_APP_SIGNING_CERT_SHA256_FINGERPRINTS", ""),
    )
    parser.add_argument("--wait-seconds", type=float, default=30.0)
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _arguments(argv or sys.argv[1:])
    try:
        expected = WEB.parse_expected_fingerprints(arguments.expected_sha256)
        if not arguments.serial:
            raise WEB.ContractError("set ANDROID_SERIAL to the dedicated Play test device")
        if arguments.wait_seconds < 0 or arguments.wait_seconds > 60:
            raise WEB.ContractError("wait time must be between 0 and 60 seconds")
        adb = _resolve_adb(arguments.adb)
        if not adb:
            raise WEB.ContractError("adb executable was not found")
        if _connected_devices(adb).get(arguments.serial) != "device":
            raise WEB.ContractError("the requested physical device is not connected and authorized")

        api_text = _run_adb(adb, arguments.serial, ["shell", "getprop", "ro.build.version.sdk"]).strip()
        if not api_text.isdigit() or int(api_text) < 31:
            raise WEB.ContractError("Play App Links package-manager verification requires API 31 or newer")
        installer = _run_adb(
            adb,
            arguments.serial,
            ["shell", "cmd", "package", "list", "packages", "-i", PACKAGE],
        )
        validate_installer(installer)
        _run_adb(
            adb,
            arguments.serial,
            ["shell", "pm", "verify-app-links", "--re-verify", PACKAGE],
        )

        deadline = time.monotonic() + arguments.wait_seconds
        last_error: WEB.ContractError | None = None
        while True:
            state = _run_adb(
                adb,
                arguments.serial,
                ["shell", "pm", "get-app-links", PACKAGE],
            )
            try:
                validate_app_links_state(state, expected)
                break
            except WEB.ContractError as error:
                last_error = error
            if time.monotonic() >= deadline:
                assert last_error is not None
                raise last_error
            time.sleep(min(2.0, max(0.0, deadline - time.monotonic())))
    except WEB.ContractError as error:
        print(f"Play-installed App Links verification failed: {error}", file=sys.stderr)
        return 1

    print(
        "Play-installed App Links verified: "
        f"serial={arguments.serial} package={PACKAGE} domain={DOMAIN} "
        f"app_signing_certificates={len(expected)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
