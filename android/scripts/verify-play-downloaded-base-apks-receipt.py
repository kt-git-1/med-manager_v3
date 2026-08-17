#!/usr/bin/env python3

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import sys
import tempfile
import zipfile


MODULE_NAME = "verify_play_generated_apks_receipt"
if MODULE_NAME in sys.modules:
    GENERATED = sys.modules[MODULE_NAME]
else:
    script = Path(__file__).with_name("verify-play-generated-apks-receipt.py")
    spec = importlib.util.spec_from_file_location(MODULE_NAME, script)
    if spec is None or spec.loader is None:
        raise RuntimeError("Unable to load Play generated APK receipt verifier")
    GENERATED = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = GENERATED
    spec.loader.exec_module(GENERATED)


EXPECTED_APPLICATION_ID = GENERATED.EXPECTED_APPLICATION_ID
MAX_APK_BYTES = 1024 * 1024 * 1024
MAX_APK_UNCOMPRESSED_BYTES = 2 * 1024 * 1024 * 1024
MAX_APK_ENTRIES = 200_000
MAX_TOOL_OUTPUT_BYTES = 2 * 1024 * 1024
DEX_PATTERN = re.compile(r"classes(?:[2-9]|[1-9][0-9]+)?\.dex$")
PACKAGE_ATTRIBUTE = re.compile(r"([A-Za-z][A-Za-z0-9_-]*)='([^']*)'")
SIGNER_DIGEST = re.compile(
    r"^Signer #([1-9][0-9]*) certificate SHA-256 digest: ([0-9A-Fa-f:]+)$"
)
SIGNATURE_SCHEME = re.compile(
    r"^Verified using (v[0-9]+(?:\.[0-9]+)?) scheme(?: \([^)]*\))?: (true|false)$"
)
NUMBER_OF_SIGNERS = re.compile(r"^Number of signers: ([1-9][0-9]*)$")
FORBIDDEN_NAMES = {
    ".env",
    "google-services.json",
    "service-account.json",
    "service_account.json",
}
FORBIDDEN_EXTENSIONS = {"jks", "keystore", "p12", "pem", "key"}


class DownloadedBaseApksReceiptError(RuntimeError):
    pass


def canonical_json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def load_existing_receipt(path: Path) -> bytes:
    if path.is_symlink() or not path.is_file():
        raise DownloadedBaseApksReceiptError(
            "C117 Play generated APK receipt is missing or unsafe"
        )
    try:
        receipt_bytes = path.read_bytes()
    except OSError as error:
        raise DownloadedBaseApksReceiptError(
            "C117 Play generated APK receipt could not be read"
        ) from error
    if not receipt_bytes or len(receipt_bytes) > GENERATED.MAX_RESPONSE_BYTES:
        raise DownloadedBaseApksReceiptError(
            "C117 Play generated APK receipt size is invalid"
        )
    return receipt_bytes


def base_master_download_certificates(
    response: dict[str, object], expected_fingerprints: tuple[str, ...]
) -> dict[str, str]:
    GENERATED.validate_generated_response(response, expected_fingerprints)
    mapping: dict[str, str] = {}
    groups = response["generatedApks"]
    assert isinstance(groups, list)
    all_download_ids: list[str] = []
    for group in groups:
        assert isinstance(group, dict)
        certificate = GENERATED.normalize_sha256(
            group["certificateSha256Hash"],
            label="Play generated APK certificateSha256Hash",
        )
        splits = group["generatedSplitApks"]
        assert isinstance(splits, list)
        all_download_ids.extend(str(split["downloadId"]) for split in splits)
        for field in (
            "generatedStandaloneApks",
            "unprotectedGeneratedSplitApks",
            "unprotectedGeneratedStandaloneApks",
        ):
            items = group.get(field, [])
            assert isinstance(items, list)
            all_download_ids.extend(str(item["downloadId"]) for item in items)
        universal = group.get("generatedUniversalApk")
        if universal is not None:
            assert isinstance(universal, dict)
            all_download_ids.append(str(universal["downloadId"]))
        for split in splits:
            assert isinstance(split, dict)
            if split["moduleName"] == "base" and split["splitId"] == "":
                download_id = split["downloadId"]
                assert isinstance(download_id, str)
                if download_id in mapping:
                    raise DownloadedBaseApksReceiptError(
                        "Play base-master downloadId is not globally unique"
                    )
                mapping[download_id] = certificate
    if len(all_download_ids) != len(set(all_download_ids)):
        raise DownloadedBaseApksReceiptError(
            "Play generated APK downloadIds are not globally unique"
        )
    return mapping


def _tool_version_key(path: Path) -> tuple[tuple[int, ...], str]:
    numbers = tuple(int(item) for item in re.findall(r"[0-9]+", path.name))
    return numbers, path.name


def resolve_android_tools(
    repository_root: Path, configured_sdk_root: Path | None
) -> tuple[Path, Path]:
    candidates: list[Path] = []
    if configured_sdk_root is not None:
        candidates.append(configured_sdk_root)
    else:
        for variable in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
            value = os.environ.get(variable)
            if value:
                candidates.append(Path(value))
        local_properties = repository_root / "android" / "local.properties"
        if local_properties.is_file() and not local_properties.is_symlink():
            try:
                for line in local_properties.read_text(encoding="utf-8").splitlines():
                    if line.startswith("sdk.dir=") and line.removeprefix("sdk.dir="):
                        candidates.append(Path(line.removeprefix("sdk.dir=")))
            except (OSError, UnicodeDecodeError) as error:
                raise DownloadedBaseApksReceiptError(
                    "Android SDK location could not be read"
                ) from error

    seen: set[Path] = set()
    for candidate in candidates:
        expanded = candidate.expanduser()
        try:
            resolved = expanded.resolve(strict=True)
        except OSError:
            continue
        if resolved in seen or not resolved.is_dir():
            continue
        seen.add(resolved)
        build_tools = resolved / "build-tools"
        if not build_tools.is_dir():
            continue
        versions = sorted(
            (item for item in build_tools.iterdir() if item.is_dir()),
            key=_tool_version_key,
            reverse=True,
        )
        for version in versions:
            aapt2 = version / "aapt2"
            apksigner = version / "apksigner"
            if (
                aapt2.is_file()
                and not aapt2.is_symlink()
                and os.access(aapt2, os.X_OK)
                and apksigner.is_file()
                and not apksigner.is_symlink()
                and os.access(apksigner, os.X_OK)
            ):
                return aapt2, apksigner
    raise DownloadedBaseApksReceiptError(
        "Android SDK build-tools with aapt2 and apksigner are unavailable"
    )


def run_tool(arguments: list[str], label: str) -> str:
    try:
        completed = subprocess.run(
            arguments,
            check=False,
            capture_output=True,
            timeout=60,
            env={**os.environ, "LC_ALL": "C", "LANG": "C"},
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise DownloadedBaseApksReceiptError(f"{label} could not execute") from error
    if completed.returncode != 0:
        raise DownloadedBaseApksReceiptError(f"{label} rejected the downloaded APK")
    if len(completed.stdout) > MAX_TOOL_OUTPUT_BYTES or len(completed.stderr) > MAX_TOOL_OUTPUT_BYTES:
        raise DownloadedBaseApksReceiptError(f"{label} output is unexpectedly large")
    try:
        return completed.stdout.decode("utf-8").replace("\r\n", "\n")
    except UnicodeDecodeError as error:
        raise DownloadedBaseApksReceiptError(f"{label} output is not UTF-8") from error


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_badging(output: str, expected_version_code: int, expected_version_name: str) -> None:
    lines = output.splitlines()
    if not lines or not lines[0].startswith("package: "):
        raise DownloadedBaseApksReceiptError("aapt2 badging package row is missing")
    pairs = PACKAGE_ATTRIBUTE.findall(lines[0])
    attributes = {key: value for key, value in pairs}
    if len(attributes) != len(pairs):
        raise DownloadedBaseApksReceiptError("aapt2 badging package attributes are duplicated")
    if attributes.get("name") != EXPECTED_APPLICATION_ID:
        raise DownloadedBaseApksReceiptError("Downloaded APK package name is not production")
    version_code = attributes.get("versionCode", "")
    if not re.fullmatch(r"[1-9][0-9]*", version_code) or int(version_code) != expected_version_code:
        raise DownloadedBaseApksReceiptError("Downloaded APK versionCode does not match C117")
    if attributes.get("versionName") != expected_version_name:
        raise DownloadedBaseApksReceiptError("Downloaded APK versionName does not match C117")
    if attributes.get("split", ""):
        raise DownloadedBaseApksReceiptError("Downloaded APK is not a base-master split")


def parse_apksigner(output: str, expected_certificate: str) -> tuple[str, ...]:
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    if "Verifies" not in lines:
        raise DownloadedBaseApksReceiptError("Downloaded APK signature did not verify")

    signer_rows = [NUMBER_OF_SIGNERS.fullmatch(line) for line in lines]
    signer_counts = [int(match.group(1)) for match in signer_rows if match is not None]
    if signer_counts != [1]:
        raise DownloadedBaseApksReceiptError(
            "Downloaded APK must contain exactly one verified signer"
        )

    certificate_rows = [SIGNER_DIGEST.fullmatch(line) for line in lines]
    certificates = [
        GENERATED.normalize_sha256(
            match.group(2), label="Downloaded APK signer certificate SHA-256"
        )
        for match in certificate_rows
        if match is not None
    ]
    signer_numbers = [
        int(match.group(1)) for match in certificate_rows if match is not None
    ]
    if signer_numbers != [1] or certificates != [expected_certificate]:
        raise DownloadedBaseApksReceiptError(
            "Downloaded APK signer does not match its generated APK signing-key group"
        )

    schemes: dict[str, bool] = {}
    for line in lines:
        match = SIGNATURE_SCHEME.fullmatch(line)
        if match is None:
            continue
        name, enabled = match.groups()
        if name in schemes:
            raise DownloadedBaseApksReceiptError(
                "Downloaded APK signature scheme rows are duplicated"
            )
        schemes[name] = enabled == "true"
    verified = tuple(
        sorted(
            (name for name, enabled in schemes.items() if enabled),
            key=lambda value: tuple(int(item) for item in value[1:].split(".")),
        )
    )
    if not any(name in {"v2", "v3", "v3.1"} for name in verified):
        raise DownloadedBaseApksReceiptError(
            "Downloaded APK requires a verified v2-or-newer embedded signature"
        )
    return verified


def inspect_zip(apk: Path, handoff: Path) -> dict[str, int | str]:
    if apk.is_symlink() or not apk.is_file() or apk.suffix != ".apk":
        raise DownloadedBaseApksReceiptError("Downloaded base APK is missing or unsafe")
    apk_resolved = apk.resolve()
    handoff_resolved = handoff.resolve()
    if apk_resolved == handoff_resolved or apk_resolved.is_relative_to(handoff_resolved):
        raise DownloadedBaseApksReceiptError(
            "Downloaded base APK must remain outside the three-file handoff"
        )
    size = apk.stat().st_size
    if size <= 0 or size > MAX_APK_BYTES:
        raise DownloadedBaseApksReceiptError("Downloaded base APK size is invalid")
    try:
        with zipfile.ZipFile(apk) as archive:
            entries = archive.infolist()
            names = [entry.filename for entry in entries]
            if not entries or len(entries) > MAX_APK_ENTRIES or len(names) != len(set(names)):
                raise DownloadedBaseApksReceiptError(
                    "Downloaded base APK ZIP inventory is invalid"
                )
            for entry in entries:
                name = entry.filename
                pure = PurePosixPath(name)
                if (
                    not name
                    or "\\" in name
                    or pure.is_absolute()
                    or ".." in pure.parts
                    or stat.S_IFMT(entry.external_attr >> 16) == stat.S_IFLNK
                ):
                    raise DownloadedBaseApksReceiptError(
                        "Downloaded base APK contains an unsafe ZIP entry"
                    )
                file_name = pure.name.lower()
                extension = file_name.rsplit(".", 1)[-1] if "." in file_name else ""
                if file_name in FORBIDDEN_NAMES or extension in FORBIDDEN_EXTENSIONS:
                    raise DownloadedBaseApksReceiptError(
                        "Downloaded base APK contains private configuration or key material"
                    )
            uncompressed_bytes = sum(entry.file_size for entry in entries)
            if uncompressed_bytes <= 0 or uncompressed_bytes > MAX_APK_UNCOMPRESSED_BYTES:
                raise DownloadedBaseApksReceiptError(
                    "Downloaded base APK uncompressed size is invalid"
                )
            if "AndroidManifest.xml" not in names:
                raise DownloadedBaseApksReceiptError(
                    "Downloaded base APK is missing AndroidManifest.xml"
                )
            dex_count = sum(1 for name in names if DEX_PATTERN.fullmatch(name))
            if dex_count < 1:
                raise DownloadedBaseApksReceiptError(
                    "Downloaded base APK is missing primary DEX content"
                )
            corrupt = archive.testzip()
            if corrupt is not None:
                raise DownloadedBaseApksReceiptError(
                    "Downloaded base APK contains a corrupt ZIP entry"
                )
    except (OSError, zipfile.BadZipFile) as error:
        raise DownloadedBaseApksReceiptError(
            "Downloaded base APK is not a valid APK archive"
        ) from error
    return {
        "apkSha256": sha256_file(apk),
        "sizeBytes": size,
        "zipEntries": len(entries),
        "dexFiles": dex_count,
    }


def inspect_downloaded_apk(
    apk: Path,
    expected_certificate: str,
    expected_version_code: int,
    expected_version_name: str,
    handoff: Path,
    aapt2: Path,
    apksigner: Path,
) -> dict[str, object]:
    inspection = inspect_zip(apk, handoff)
    badging = run_tool([str(aapt2), "dump", "badging", str(apk)], "aapt2")
    parse_badging(badging, expected_version_code, expected_version_name)
    signing = run_tool(
        [
            str(apksigner),
            "verify",
            "--verbose",
            "--print-certs",
            "--min-sdk-version",
            "26",
            str(apk),
        ],
        "apksigner",
    )
    schemes = parse_apksigner(signing, expected_certificate)
    if (
        apk.is_symlink()
        or not apk.is_file()
        or apk.stat().st_size != inspection["sizeBytes"]
        or sha256_file(apk) != inspection["apkSha256"]
    ):
        raise DownloadedBaseApksReceiptError(
            "Downloaded base APK changed during SDK verification"
        )
    return {
        "appSigningCertificateSha256Fingerprint": expected_certificate,
        **inspection,
        "verifiedSignatureSchemes": list(schemes),
    }


def write_receipt_atomic(output: Path, receipt: dict[str, object], handoff: Path) -> None:
    expected_name = f"{handoff.name}.play-downloaded-base-apks-receipt.json"
    if output.name != expected_name:
        raise DownloadedBaseApksReceiptError(
            f"Play downloaded base APK receipt must be named {expected_name}"
        )
    output_parent = output.parent
    if output_parent.is_symlink():
        raise DownloadedBaseApksReceiptError(
            "Play downloaded base APK receipt parent is unsafe"
        )
    output_parent.mkdir(parents=True, exist_ok=True)
    handoff_resolved = handoff.resolve()
    output_resolved = output_parent.resolve() / output.name
    if output_resolved == handoff_resolved or output_resolved.is_relative_to(handoff_resolved):
        raise DownloadedBaseApksReceiptError(
            "Play downloaded base APK receipt must remain outside the three-file handoff"
        )
    receipt_bytes = canonical_json_bytes(receipt)
    if output.exists() or output.is_symlink():
        if output.is_symlink() or not output.is_file():
            raise DownloadedBaseApksReceiptError(
                "Existing Play downloaded base APK receipt is unsafe"
            )
        if output.read_bytes() != receipt_bytes:
            raise DownloadedBaseApksReceiptError(
                "Existing Play downloaded base APK receipt conflicts with verified APKs"
            )
        return

    temporary_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            dir=output_parent,
            prefix=f".{output.name}.",
            suffix=".tmp",
            delete=False,
        ) as temporary:
            temporary_name = temporary.name
            temporary.write(receipt_bytes)
            temporary.flush()
            os.fsync(temporary.fileno())
        os.replace(temporary_name, output)
    except BaseException:
        if temporary_name:
            Path(temporary_name).unlink(missing_ok=True)
        raise


def verify_play_downloaded_base_apks_receipt(
    handoff: Path,
    bundle_response: Path,
    upload_receipt: Path,
    release_list_response: Path,
    track_response: Path,
    internal_track_receipt: Path,
    generated_apks_response: Path,
    expected_app_signing_sha256: str,
    generated_apks_receipt: Path,
    downloaded_base_apks: list[tuple[str, Path]],
    output: Path,
    repository_root: Path,
    android_sdk_root: Path | None = None,
) -> dict[str, object]:
    generated_receipt_bytes = load_existing_receipt(generated_apks_receipt)
    generated_receipt = GENERATED.verify_play_generated_apks_receipt(
        handoff,
        bundle_response,
        upload_receipt,
        release_list_response,
        track_response,
        internal_track_receipt,
        generated_apks_response,
        expected_app_signing_sha256,
        generated_apks_receipt,
        repository_root,
    )
    if generated_apks_receipt.read_bytes() != generated_receipt_bytes:
        raise DownloadedBaseApksReceiptError(
            "C117 Play generated APK receipt changed during verification"
        )

    expected = GENERATED.parse_expected_fingerprints(expected_app_signing_sha256)
    response, _ = GENERATED.load_json_response(generated_apks_response)
    base_masters = base_master_download_certificates(response, expected)
    if not downloaded_base_apks:
        raise DownloadedBaseApksReceiptError(
            "At least one downloaded base APK selection is required"
        )
    download_ids = [download_id for download_id, _ in downloaded_base_apks]
    if len(download_ids) != len(set(download_ids)):
        raise DownloadedBaseApksReceiptError(
            "Downloaded base APK selections contain duplicate downloadIds"
        )
    paths = [path.resolve() for _, path in downloaded_base_apks]
    if len(paths) != len(set(paths)):
        raise DownloadedBaseApksReceiptError(
            "Downloaded base APK selections reuse a local file"
        )
    selected_certificates: list[str] = []
    for download_id in download_ids:
        if download_id not in base_masters:
            raise DownloadedBaseApksReceiptError(
                "Downloaded APK selection is not a C117 base-master downloadId"
            )
        selected_certificates.append(base_masters[download_id])
    if len(selected_certificates) != len(set(selected_certificates)):
        raise DownloadedBaseApksReceiptError(
            "Select exactly one base-master APK per app-signing key"
        )
    if set(selected_certificates) != set(expected):
        raise DownloadedBaseApksReceiptError(
            "Downloaded base APK selections do not cover every C117 signing key"
        )

    aapt2, apksigner = resolve_android_tools(repository_root, android_sdk_root)
    inspected = [
        inspect_downloaded_apk(
            apk,
            base_masters[download_id],
            int(generated_receipt["versionCode"]),
            str(generated_receipt["versionName"]),
            handoff,
            aapt2,
            apksigner,
        )
        for download_id, apk in downloaded_base_apks
    ]
    if len({item["apkSha256"] for item in inspected}) != len(inspected):
        raise DownloadedBaseApksReceiptError(
            "Downloaded base APK bytes are duplicated across signing keys"
        )
    inspected.sort(key=lambda item: str(item["appSigningCertificateSha256Fingerprint"]))
    final_generated_receipt = GENERATED.verify_play_generated_apks_receipt(
        handoff,
        bundle_response,
        upload_receipt,
        release_list_response,
        track_response,
        internal_track_receipt,
        generated_apks_response,
        expected_app_signing_sha256,
        generated_apks_receipt,
        repository_root,
    )
    if (
        final_generated_receipt != generated_receipt
        or generated_apks_receipt.read_bytes() != generated_receipt_bytes
    ):
        raise DownloadedBaseApksReceiptError(
            "C117 chain changed during downloaded APK verification"
        )
    receipt = {
        "schemaVersion": 1,
        "sources": [
            "google-play-developer-api-v3-generatedapks.download",
            "c117-play-generated-apks-receipt",
            "android-sdk-apksigner",
            "android-sdk-aapt2",
        ],
        "applicationId": generated_receipt["applicationId"],
        "handoffDirectory": generated_receipt["handoffDirectory"],
        "commitSha": generated_receipt["commitSha"],
        "versionName": generated_receipt["versionName"],
        "versionCode": generated_receipt["versionCode"],
        "aabSha256": generated_receipt["aabSha256"],
        "generatedApksReceiptSha256": hashlib.sha256(
            generated_receipt_bytes
        ).hexdigest(),
        "androidBuildToolsVersion": aapt2.parent.name,
        "completeSigningKeyCoverage": True,
        "downloadedBaseApks": inspected,
    }
    if output.resolve() == generated_apks_receipt.resolve():
        raise DownloadedBaseApksReceiptError(
            "Play downloaded base APK receipt must not replace the C117 receipt"
        )
    write_receipt_atomic(output, receipt, handoff.resolve())
    return receipt


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Bind downloaded Play base-master APK bytes to an existing C117 receipt."
        )
    )
    parser.add_argument("--handoff", type=Path, required=True)
    parser.add_argument("--bundle-response", type=Path, required=True)
    parser.add_argument("--upload-receipt", type=Path, required=True)
    parser.add_argument("--release-list-response", type=Path, required=True)
    parser.add_argument("--track-response", type=Path, required=True)
    parser.add_argument("--internal-track-receipt", type=Path, required=True)
    parser.add_argument("--generated-apks-response", type=Path, required=True)
    parser.add_argument(
        "--expected-app-signing-sha256",
        default=os.environ.get("EXPECTED_APP_SIGNING_CERT_SHA256_FINGERPRINTS", ""),
    )
    parser.add_argument("--generated-apks-receipt", type=Path, required=True)
    parser.add_argument(
        "--downloaded-base-apk",
        nargs=2,
        action="append",
        metavar=("DOWNLOAD_ID", "APK"),
        required=True,
    )
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository-root", type=Path, required=True)
    parser.add_argument("--android-sdk-root", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    selections = [
        (download_id, Path(apk)) for download_id, apk in args.downloaded_base_apk
    ]
    try:
        receipt = verify_play_downloaded_base_apks_receipt(
            args.handoff,
            args.bundle_response,
            args.upload_receipt,
            args.release_list_response,
            args.track_response,
            args.internal_track_receipt,
            args.generated_apks_response,
            args.expected_app_signing_sha256,
            args.generated_apks_receipt,
            selections,
            args.output,
            args.repository_root,
            args.android_sdk_root,
        )
    except (
        DownloadedBaseApksReceiptError,
        GENERATED.GeneratedApksReceiptError,
        GENERATED.INTERNAL.TrackReceiptError,
        GENERATED.INTERNAL.UPLOAD.ReceiptError,
        GENERATED.INTERNAL.UPLOAD.HANDOFF.HANDOFF.HandoffError,
        OSError,
    ) as error:
        print(str(error), file=sys.stderr)
        return 1
    print(f"Google Play downloaded base APK receipt verified: {args.output}")
    print(
        f"VERSION_CODE={receipt['versionCode']} "
        f"BASE_APKS={len(receipt['downloadedBaseApks'])}"
    )
    print(f"COMMIT={receipt['commitSha']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
