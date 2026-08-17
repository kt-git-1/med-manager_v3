#!/usr/bin/env python3

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import tempfile


MODULE_NAME = "verify_prepared_play_release_handoff"
if MODULE_NAME in sys.modules:
    HANDOFF = sys.modules[MODULE_NAME]
else:
    script = Path(__file__).with_name("verify-prepared-play-release-handoff.py")
    spec = importlib.util.spec_from_file_location(MODULE_NAME, script)
    if spec is None or spec.loader is None:
        raise RuntimeError("Unable to load retained Play handoff verifier")
    HANDOFF = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = HANDOFF
    spec.loader.exec_module(HANDOFF)


EXPECTED_APPLICATION_ID = "com.afterlifearchive.medmanager"
EXPECTED_LIST_KIND = "androidpublisher#bundlesListResponse"
BUNDLE_FIELDS = {"versionCode", "sha1", "sha256"}
MAX_RESPONSE_BYTES = 1024 * 1024


class ReceiptError(RuntimeError):
    pass


def canonical_json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def load_response(path: Path) -> tuple[dict[str, object], bytes]:
    if path.is_symlink() or not path.is_file():
        raise ReceiptError("Play bundle response is missing or unsafe")
    try:
        size = path.stat().st_size
        if size <= 0 or size > MAX_RESPONSE_BYTES:
            raise ReceiptError("Play bundle response size is invalid")
        response_bytes = path.read_bytes()
        value = json.loads(response_bytes.decode("utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ReceiptError("Play bundle response JSON could not be read") from error
    if not isinstance(value, dict):
        raise ReceiptError("Play bundle response root must be an object")
    return value, response_bytes


def validate_bundle(value: object) -> dict[str, object]:
    if not isinstance(value, dict) or set(value) != BUNDLE_FIELDS:
        raise ReceiptError("Play Bundle resource fields drifted")
    version_code = value.get("versionCode")
    sha1 = value.get("sha1")
    sha256 = value.get("sha256")
    if not isinstance(version_code, int) or isinstance(version_code, bool) or version_code <= 0:
        raise ReceiptError("Play Bundle versionCode is invalid")
    if not isinstance(sha1, str) or not re.fullmatch(r"[0-9a-f]{40}", sha1):
        raise ReceiptError("Play Bundle SHA-1 is invalid")
    if not isinstance(sha256, str) or not re.fullmatch(r"[0-9a-f]{64}", sha256):
        raise ReceiptError("Play Bundle SHA-256 is invalid")
    return {"versionCode": version_code, "sha1": sha1, "sha256": sha256}


def select_bundle(response: dict[str, object], expected_version_code: int) -> dict[str, object]:
    if set(response) == BUNDLE_FIELDS:
        bundle = validate_bundle(response)
        if bundle["versionCode"] != expected_version_code:
            raise ReceiptError("Play upload response versionCode does not match the handoff")
        return bundle

    if set(response) != {"kind", "bundles"} or response.get("kind") != EXPECTED_LIST_KIND:
        raise ReceiptError("Play bundle response envelope drifted")
    bundles = response.get("bundles")
    if not isinstance(bundles, list) or not bundles:
        raise ReceiptError("Play bundle list is empty or malformed")
    validated = [validate_bundle(item) for item in bundles]
    matching = [item for item in validated if item["versionCode"] == expected_version_code]
    if len(matching) != 1:
        raise ReceiptError("Play bundle list must contain exactly one matching versionCode")
    return matching[0]


def build_receipt(
    handoff: dict[str, object], bundle: dict[str, object], response_bytes: bytes
) -> dict[str, object]:
    if handoff.get("applicationId") != EXPECTED_APPLICATION_ID:
        raise ReceiptError("Retained handoff application ID is not production")
    if bundle["versionCode"] != handoff.get("versionCode"):
        raise ReceiptError("Play Bundle versionCode does not match the retained handoff")
    if bundle["sha256"] != handoff.get("aabSha256"):
        raise ReceiptError("Play Bundle SHA-256 does not match the uploaded handoff AAB")
    return {
        "schemaVersion": 1,
        "source": "google-play-developer-api-v3-edits.bundles",
        "applicationId": handoff["applicationId"],
        "handoffDirectory": handoff["directory"],
        "commitSha": handoff["commitSha"],
        "versionName": handoff["versionName"],
        "versionCode": handoff["versionCode"],
        "aabSha256": handoff["aabSha256"],
        "apiResponseSha256": hashlib.sha256(response_bytes).hexdigest(),
        "playBundle": bundle,
    }


def write_receipt_atomic(output: Path, receipt: dict[str, object], handoff: Path) -> None:
    expected_name = f"{handoff.name}.play-upload-receipt.json"
    if output.name != expected_name:
        raise ReceiptError(f"Play upload receipt must be named {expected_name}")
    handoff_resolved = handoff.resolve()
    output_parent = output.parent
    if output_parent.is_symlink():
        raise ReceiptError("Play upload receipt parent is unsafe")
    output_parent.mkdir(parents=True, exist_ok=True)
    output_resolved = output_parent.resolve() / output.name
    if output_resolved == handoff_resolved or output_resolved.is_relative_to(handoff_resolved):
        raise ReceiptError("Play upload receipt must remain outside the three-file handoff")
    receipt_bytes = canonical_json_bytes(receipt)
    if output.exists() or output.is_symlink():
        if output.is_symlink() or not output.is_file():
            raise ReceiptError("Existing Play upload receipt is unsafe")
        if output.read_bytes() != receipt_bytes:
            raise ReceiptError("Existing Play upload receipt conflicts with the verified response")
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


def verify_play_upload_receipt(
    handoff: Path, response: Path, output: Path, repository_root: Path
) -> dict[str, object]:
    retained = HANDOFF.verify_prepared_handoff(handoff, repository_root)
    response_value, response_bytes = load_response(response)
    version_code = retained.get("versionCode")
    if not isinstance(version_code, int) or isinstance(version_code, bool):
        raise ReceiptError("Retained handoff versionCode is invalid")
    bundle = select_bundle(response_value, version_code)
    receipt = build_receipt(retained, bundle, response_bytes)
    write_receipt_atomic(output, receipt, handoff.resolve())
    return receipt


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compare a Google Play Bundle API response with a retained release handoff."
    )
    parser.add_argument("--handoff", type=Path, required=True)
    parser.add_argument("--bundle-response", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository-root", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        receipt = verify_play_upload_receipt(
            args.handoff, args.bundle_response, args.output, args.repository_root
        )
    except (ReceiptError, HANDOFF.HANDOFF.HandoffError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 1
    print(f"Google Play upload receipt verified: {args.output}")
    print(f"VERSION_CODE={receipt['versionCode']} AAB_SHA256={receipt['aabSha256']}")
    print(f"COMMIT={receipt['commitSha']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
