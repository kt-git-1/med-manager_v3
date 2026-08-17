#!/usr/bin/env python3

from __future__ import annotations

import argparse
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import tempfile


MODULE_NAME = "verify_play_internal_track_receipt"
if MODULE_NAME in sys.modules:
    INTERNAL = sys.modules[MODULE_NAME]
else:
    script = Path(__file__).with_name("verify-play-internal-track-receipt.py")
    spec = importlib.util.spec_from_file_location(MODULE_NAME, script)
    if spec is None or spec.loader is None:
        raise RuntimeError("Unable to load Play Internal track receipt verifier")
    INTERNAL = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = INTERNAL
    spec.loader.exec_module(INTERNAL)


EXPECTED_APPLICATION_ID = "com.afterlifearchive.medmanager"
ROOT_FIELDS = {"generatedApks"}
SIGNING_KEY_REQUIRED_FIELDS = {
    "certificateSha256Hash",
    "generatedSplitApks",
    "targetingInfo",
}
SIGNING_KEY_FIELDS = SIGNING_KEY_REQUIRED_FIELDS | {
    "generatedAssetPackSlices",
    "generatedStandaloneApks",
    "generatedUniversalApk",
    "generatedRecoveryModules",
    "unprotectedGeneratedSplitApks",
    "unprotectedGeneratedStandaloneApks",
}
SPLIT_FIELDS = {"downloadId", "variantId", "moduleName", "splitId"}
STANDALONE_FIELDS = {"downloadId", "variantId"}
UNIVERSAL_FIELDS = {"downloadId"}
TARGETING_REQUIRED_FIELDS = {"packageName", "variant"}
TARGETING_FIELDS = TARGETING_REQUIRED_FIELDS | {"assetSliceSet"}
MAX_RESPONSE_BYTES = 8 * 1024 * 1024
MAX_SIGNING_KEY_GROUPS = 4
MAX_DOWNLOAD_ID_LENGTH = 4096
HEX_SHA256 = re.compile(r"^[0-9A-Fa-f]{64}$")
COLON_SHA256 = re.compile(r"^(?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2}$")
BASE64_SHA256 = re.compile(r"^[A-Za-z0-9_-]{43}=?$|^[A-Za-z0-9+/]{43}={0,1}$")


class GeneratedApksReceiptError(RuntimeError):
    pass


def canonical_json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def normalize_sha256(value: object, *, label: str) -> str:
    if not isinstance(value, str) or not value or value.strip() != value:
        raise GeneratedApksReceiptError(f"{label} is malformed")
    if HEX_SHA256.fullmatch(value):
        digest = bytes.fromhex(value)
    elif COLON_SHA256.fullmatch(value):
        digest = bytes.fromhex(value.replace(":", ""))
    elif BASE64_SHA256.fullmatch(value):
        padded = value + "=" * ((4 - len(value) % 4) % 4)
        try:
            digest = base64.b64decode(padded, altchars=b"-_", validate=True)
        except (ValueError, base64.binascii.Error) as error:
            raise GeneratedApksReceiptError(f"{label} is malformed") from error
        if len(digest) != 32:
            raise GeneratedApksReceiptError(f"{label} is malformed")
    else:
        raise GeneratedApksReceiptError(f"{label} is malformed")
    return ":".join(f"{byte:02X}" for byte in digest)


def parse_expected_fingerprints(value: str) -> tuple[str, ...]:
    raw = [item.strip() for item in value.split(",") if item.strip()]
    if not raw:
        raise GeneratedApksReceiptError(
            "At least one expected Play app-signing SHA-256 fingerprint is required"
        )
    normalized = tuple(
        normalize_sha256(item, label="Expected Play app-signing SHA-256 fingerprint")
        for item in raw
    )
    if len(normalized) != len(set(normalized)):
        raise GeneratedApksReceiptError(
            "Expected Play app-signing SHA-256 fingerprints contain duplicates"
        )
    return normalized


def load_json_response(path: Path) -> tuple[dict[str, object], bytes]:
    if path.is_symlink() or not path.is_file():
        raise GeneratedApksReceiptError("Play generated APK response is missing or unsafe")
    try:
        size = path.stat().st_size
        if size <= 0 or size > MAX_RESPONSE_BYTES:
            raise GeneratedApksReceiptError("Play generated APK response size is invalid")
        response_bytes = path.read_bytes()
        value = json.loads(response_bytes.decode("utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise GeneratedApksReceiptError(
            "Play generated APK response JSON could not be read"
        ) from error
    if not isinstance(value, dict):
        raise GeneratedApksReceiptError("Play generated APK response root must be an object")
    return value, response_bytes


def validate_download_id(value: object) -> str:
    if (
        not isinstance(value, str)
        or not value
        or len(value) > MAX_DOWNLOAD_ID_LENGTH
        or value.strip() != value
        or any(ord(character) < 33 or ord(character) > 126 for character in value)
    ):
        raise GeneratedApksReceiptError("Play generated APK downloadId is invalid")
    return value


def validate_variant_id(value: object) -> int:
    if (
        not isinstance(value, int)
        or isinstance(value, bool)
        or value < 0
        or value > INTERNAL.MAX_VERSION_CODE
    ):
        raise GeneratedApksReceiptError("Play generated APK variantId is invalid")
    return value


def validate_split(value: object) -> dict[str, object]:
    if not isinstance(value, dict) or set(value) != SPLIT_FIELDS:
        raise GeneratedApksReceiptError("Play GeneratedSplitApk fields drifted")
    module_name = value.get("moduleName")
    split_id = value.get("splitId")
    if (
        not isinstance(module_name, str)
        or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.-]{0,127}", module_name)
        or not isinstance(split_id, str)
        or len(split_id) > 512
        or split_id.strip() != split_id
    ):
        raise GeneratedApksReceiptError("Play generated split identity is invalid")
    return {
        "downloadId": validate_download_id(value.get("downloadId")),
        "variantId": validate_variant_id(value.get("variantId")),
        "moduleName": module_name,
        "splitId": split_id,
    }


def validate_standalone(value: object) -> str:
    if not isinstance(value, dict) or set(value) != STANDALONE_FIELDS:
        raise GeneratedApksReceiptError("Play GeneratedStandaloneApk fields drifted")
    validate_variant_id(value.get("variantId"))
    return validate_download_id(value.get("downloadId"))


def validate_optional_list(
    value: dict[str, object], field: str, validator
) -> list[object]:
    if field not in value:
        return []
    items = value[field]
    if not isinstance(items, list):
        raise GeneratedApksReceiptError(f"Play {field} must be a list")
    return [validator(item) for item in items]


def validate_targeting_info(value: object) -> None:
    if not isinstance(value, dict):
        raise GeneratedApksReceiptError("Play generated targetingInfo must be an object")
    fields = set(value)
    if not TARGETING_REQUIRED_FIELDS <= fields or not fields <= TARGETING_FIELDS:
        raise GeneratedApksReceiptError("Play generated targetingInfo fields drifted")
    if value.get("packageName") != EXPECTED_APPLICATION_ID:
        raise GeneratedApksReceiptError("Play generated APK package name is not production")
    variants = value.get("variant")
    if not isinstance(variants, list) or not variants:
        raise GeneratedApksReceiptError("Play generated targeting variants are empty or malformed")
    if not all(isinstance(item, dict) and item for item in variants):
        raise GeneratedApksReceiptError("Play generated targeting variant is malformed")
    asset_slice_sets = value.get("assetSliceSet", [])
    if not isinstance(asset_slice_sets, list) or asset_slice_sets:
        raise GeneratedApksReceiptError(
            "Play generated asset slices conflict with the base-only release contract"
        )


def validate_signing_key_group(value: object) -> dict[str, object]:
    if not isinstance(value, dict):
        raise GeneratedApksReceiptError("Play signing-key group must be an object")
    fields = set(value)
    if not SIGNING_KEY_REQUIRED_FIELDS <= fields or not fields <= SIGNING_KEY_FIELDS:
        raise GeneratedApksReceiptError("Play signing-key group fields drifted")

    certificate = normalize_sha256(
        value.get("certificateSha256Hash"),
        label="Play generated APK certificateSha256Hash",
    )
    splits = value.get("generatedSplitApks")
    if not isinstance(splits, list) or not splits:
        raise GeneratedApksReceiptError("Play generated split APK list is empty or malformed")
    validated_splits = [validate_split(item) for item in splits]
    download_ids = [item["downloadId"] for item in validated_splits]
    if len(download_ids) != len(set(download_ids)):
        raise GeneratedApksReceiptError("Play generated split downloadIds contain duplicates")
    base_masters = [
        item
        for item in validated_splits
        if item["moduleName"] == "base" and item["splitId"] == ""
    ]
    if not base_masters:
        raise GeneratedApksReceiptError("Play generated APKs contain no base master split")

    standalone_ids = validate_optional_list(
        value, "generatedStandaloneApks", validate_standalone
    )
    unprotected_splits = validate_optional_list(
        value, "unprotectedGeneratedSplitApks", validate_split
    )
    unprotected_standalone_ids = validate_optional_list(
        value, "unprotectedGeneratedStandaloneApks", validate_standalone
    )
    all_download_ids = download_ids + standalone_ids + [
        item["downloadId"] for item in unprotected_splits
    ] + unprotected_standalone_ids
    universal = value.get("generatedUniversalApk")
    if universal is not None:
        if not isinstance(universal, dict) or set(universal) != UNIVERSAL_FIELDS:
            raise GeneratedApksReceiptError("Play GeneratedUniversalApk fields drifted")
        all_download_ids.append(validate_download_id(universal.get("downloadId")))
    if len(all_download_ids) != len(set(all_download_ids)):
        raise GeneratedApksReceiptError("Play generated APK downloadIds contain duplicates")

    asset_slices = value.get("generatedAssetPackSlices", [])
    recovery_modules = value.get("generatedRecoveryModules", [])
    if not isinstance(asset_slices, list) or asset_slices:
        raise GeneratedApksReceiptError(
            "Play generated asset-pack slices conflict with the base-only release contract"
        )
    if not isinstance(recovery_modules, list) or recovery_modules:
        raise GeneratedApksReceiptError(
            "Play generated recovery modules are outside this release contract"
        )
    validate_targeting_info(value.get("targetingInfo"))
    return {
        "certificate": certificate,
        "splitCount": len(validated_splits),
        "baseMasterCount": len(base_masters),
        "downloadCount": len(all_download_ids),
    }


def validate_generated_response(
    response: dict[str, object], expected_fingerprints: tuple[str, ...]
) -> dict[str, object]:
    if set(response) != ROOT_FIELDS:
        raise GeneratedApksReceiptError("Play generated APK response envelope drifted")
    groups = response.get("generatedApks")
    if (
        not isinstance(groups, list)
        or not groups
        or len(groups) > MAX_SIGNING_KEY_GROUPS
    ):
        raise GeneratedApksReceiptError("Play signing-key groups are empty or malformed")
    validated = [validate_signing_key_group(item) for item in groups]
    certificates = [item["certificate"] for item in validated]
    if len(certificates) != len(set(certificates)):
        raise GeneratedApksReceiptError("Play signing-key groups contain duplicate certificates")
    if set(certificates) != set(expected_fingerprints):
        raise GeneratedApksReceiptError(
            "Play generated APK certificates do not match the expected app-signing set"
        )
    return {
        "certificates": sorted(certificates),
        "signingKeyGroups": len(validated),
        "generatedSplitApks": sum(item["splitCount"] for item in validated),
        "baseMasterSplits": sum(item["baseMasterCount"] for item in validated),
        "downloadEntries": sum(item["downloadCount"] for item in validated),
    }


def load_upload_certificate(handoff: Path) -> str:
    evidence = handoff / "play-release-evidence.json"
    if evidence.is_symlink() or not evidence.is_file():
        raise GeneratedApksReceiptError("Retained Play evidence is missing or unsafe")
    try:
        value = json.loads(evidence.read_text(encoding="utf-8"))
        certificate = value["artifact"]["uploadCertificateSha256"]
    except (OSError, UnicodeDecodeError, json.JSONDecodeError, KeyError, TypeError) as error:
        raise GeneratedApksReceiptError(
            "Retained Play upload certificate could not be read"
        ) from error
    return normalize_sha256(certificate, label="Retained Play upload certificate SHA-256")


def load_existing_receipt(path: Path) -> bytes:
    if path.is_symlink() or not path.is_file():
        raise GeneratedApksReceiptError("C116 Internal track receipt is missing or unsafe")
    try:
        receipt_bytes = path.read_bytes()
    except OSError as error:
        raise GeneratedApksReceiptError("C116 Internal track receipt could not be read") from error
    if not receipt_bytes or len(receipt_bytes) > INTERNAL.MAX_RESPONSE_BYTES:
        raise GeneratedApksReceiptError("C116 Internal track receipt size is invalid")
    return receipt_bytes


def write_receipt_atomic(output: Path, receipt: dict[str, object], handoff: Path) -> None:
    expected_name = f"{handoff.name}.play-generated-apks-receipt.json"
    if output.name != expected_name:
        raise GeneratedApksReceiptError(
            f"Play generated APK receipt must be named {expected_name}"
        )
    handoff_resolved = handoff.resolve()
    output_parent = output.parent
    if output_parent.is_symlink():
        raise GeneratedApksReceiptError("Play generated APK receipt parent is unsafe")
    output_parent.mkdir(parents=True, exist_ok=True)
    output_resolved = output_parent.resolve() / output.name
    if output_resolved == handoff_resolved or output_resolved.is_relative_to(handoff_resolved):
        raise GeneratedApksReceiptError(
            "Play generated APK receipt must remain outside the three-file handoff"
        )
    receipt_bytes = canonical_json_bytes(receipt)
    if output.exists() or output.is_symlink():
        if output.is_symlink() or not output.is_file():
            raise GeneratedApksReceiptError("Existing Play generated APK receipt is unsafe")
        if output.read_bytes() != receipt_bytes:
            raise GeneratedApksReceiptError(
                "Existing Play generated APK receipt conflicts with the verified response"
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


def verify_play_generated_apks_receipt(
    handoff: Path,
    bundle_response: Path,
    upload_receipt: Path,
    release_list_response: Path,
    track_response: Path,
    internal_track_receipt: Path,
    generated_apks_response: Path,
    expected_app_signing_sha256: str,
    output: Path,
    repository_root: Path,
) -> dict[str, object]:
    internal_receipt_bytes = load_existing_receipt(internal_track_receipt)
    internal_receipt = INTERNAL.verify_play_internal_track_receipt(
        handoff,
        bundle_response,
        upload_receipt,
        release_list_response,
        track_response,
        internal_track_receipt,
        repository_root,
    )
    if internal_track_receipt.read_bytes() != internal_receipt_bytes:
        raise GeneratedApksReceiptError("C116 Internal track receipt changed during verification")

    expected = parse_expected_fingerprints(expected_app_signing_sha256)
    upload_certificate = load_upload_certificate(handoff)
    if upload_certificate in expected:
        raise GeneratedApksReceiptError(
            "Play app-signing certificate set must be distinct from the upload certificate"
        )
    response, response_bytes = load_json_response(generated_apks_response)
    generated = validate_generated_response(response, expected)
    receipt = {
        "schemaVersion": 1,
        "sources": [
            "google-play-developer-api-v3-generatedapks.list",
            "c116-play-internal-track-receipt",
        ],
        "applicationId": internal_receipt["applicationId"],
        "handoffDirectory": internal_receipt["handoffDirectory"],
        "commitSha": internal_receipt["commitSha"],
        "versionName": internal_receipt["versionName"],
        "versionCode": internal_receipt["versionCode"],
        "aabSha256": internal_receipt["aabSha256"],
        "internalTrackReceiptSha256": hashlib.sha256(internal_receipt_bytes).hexdigest(),
        "apiResponseSha256": hashlib.sha256(response_bytes).hexdigest(),
        "appSigningCertificateSha256Fingerprints": generated["certificates"],
        "uploadCertificateSeparated": True,
        "signingKeyGroups": generated["signingKeyGroups"],
        "generatedSplitApks": generated["generatedSplitApks"],
        "baseMasterSplits": generated["baseMasterSplits"],
        "downloadEntries": generated["downloadEntries"],
    }
    if output.resolve() == internal_track_receipt.resolve():
        raise GeneratedApksReceiptError(
            "Play generated APK receipt must not replace the C116 receipt"
        )
    write_receipt_atomic(output, receipt, handoff.resolve())
    return receipt


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Bind Google Play generated APK signing metadata to an existing C116 Internal receipt."
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
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository-root", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        receipt = verify_play_generated_apks_receipt(
            args.handoff,
            args.bundle_response,
            args.upload_receipt,
            args.release_list_response,
            args.track_response,
            args.internal_track_receipt,
            args.generated_apks_response,
            args.expected_app_signing_sha256,
            args.output,
            args.repository_root,
        )
    except (
        GeneratedApksReceiptError,
        INTERNAL.TrackReceiptError,
        INTERNAL.UPLOAD.ReceiptError,
        INTERNAL.UPLOAD.HANDOFF.HANDOFF.HandoffError,
        OSError,
    ) as error:
        print(str(error), file=sys.stderr)
        return 1
    print(f"Google Play generated APK receipt verified: {args.output}")
    print(
        f"VERSION_CODE={receipt['versionCode']} SIGNING_KEYS={receipt['signingKeyGroups']} "
        f"SPLIT_APKS={receipt['generatedSplitApks']}"
    )
    print(f"COMMIT={receipt['commitSha']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
