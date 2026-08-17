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


MODULE_NAME = "verify_play_upload_receipt"
if MODULE_NAME in sys.modules:
    UPLOAD = sys.modules[MODULE_NAME]
else:
    script = Path(__file__).with_name("verify-play-upload-receipt.py")
    spec = importlib.util.spec_from_file_location(MODULE_NAME, script)
    if spec is None or spec.loader is None:
        raise RuntimeError("Unable to load Play upload receipt verifier")
    UPLOAD = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = UPLOAD
    spec.loader.exec_module(UPLOAD)


EXPECTED_APPLICATION_ID = "com.afterlifearchive.medmanager"
EXPECTED_TRACK = "qa"
EXPECTED_LIFECYCLE = "RELEASE_LIFECYCLE_STATE_PUBLISHED"
ROOT_FIELDS = {"releases"}
TRACK_ROOT_FIELDS = {"track", "releases"}
RELEASE_FIELDS = {
    "releaseName",
    "track",
    "activeArtifacts",
    "releaseLifecycleState",
}
ARTIFACT_FIELDS = {"versionCode"}
TRACK_RELEASE_FIELDS = {
    "name",
    "versionCodes",
    "releaseNotes",
    "status",
    "userFraction",
    "countryTargeting",
    "inAppUpdatePriority",
}
TRACK_RELEASE_REQUIRED_FIELDS = {"versionCodes", "status"}
ALLOWED_LIFECYCLES = {
    "RELEASE_LIFECYCLE_STATE_DRAFT",
    "RELEASE_LIFECYCLE_STATE_NOT_SENT_FOR_REVIEW",
    "RELEASE_LIFECYCLE_STATE_IN_REVIEW",
    "RELEASE_LIFECYCLE_STATE_APPROVED_NOT_PUBLISHED",
    "RELEASE_LIFECYCLE_STATE_NOT_APPROVED",
    "RELEASE_LIFECYCLE_STATE_PUBLISHED",
}
MAX_RESPONSE_BYTES = 1024 * 1024
MAX_RELEASES = 20
MAX_VERSION_CODE = 2_100_000_000
TRACK_STATUSES = {"draft", "inProgress", "halted", "completed"}


class TrackReceiptError(RuntimeError):
    pass


def canonical_json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def load_track_response(path: Path) -> tuple[dict[str, object], bytes]:
    if path.is_symlink() or not path.is_file():
        raise TrackReceiptError("Play Internal track response is missing or unsafe")
    try:
        size = path.stat().st_size
        if size <= 0 or size > MAX_RESPONSE_BYTES:
            raise TrackReceiptError("Play Internal track response size is invalid")
        response_bytes = path.read_bytes()
        value = json.loads(response_bytes.decode("utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise TrackReceiptError("Play Internal track response JSON could not be read") from error
    if not isinstance(value, dict):
        raise TrackReceiptError("Play Internal track response root must be an object")
    return value, response_bytes


def validate_version_code(value: object) -> int:
    if (
        not isinstance(value, int)
        or isinstance(value, bool)
        or value <= 0
        or value > MAX_VERSION_CODE
    ):
        raise TrackReceiptError("Play Internal artifact versionCode is invalid")
    return value


def validate_release(value: object) -> dict[str, object]:
    if not isinstance(value, dict) or set(value) != RELEASE_FIELDS:
        raise TrackReceiptError("Play Internal ReleaseSummary fields drifted")
    release_name = value.get("releaseName")
    track = value.get("track")
    artifacts = value.get("activeArtifacts")
    lifecycle = value.get("releaseLifecycleState")
    if not isinstance(release_name, str) or not release_name or len(release_name) > 1024:
        raise TrackReceiptError("Play Internal releaseName is invalid")
    if track != EXPECTED_TRACK:
        raise TrackReceiptError("Play Internal response contains a non-qa track")
    if not isinstance(artifacts, list) or not artifacts:
        raise TrackReceiptError("Play Internal activeArtifacts is empty or malformed")
    version_codes: list[int] = []
    for artifact in artifacts:
        if not isinstance(artifact, dict) or set(artifact) != ARTIFACT_FIELDS:
            raise TrackReceiptError("Play Internal ArtifactSummary fields drifted")
        version_codes.append(validate_version_code(artifact.get("versionCode")))
    if len(version_codes) != len(set(version_codes)):
        raise TrackReceiptError("Play Internal release contains duplicate versionCode values")
    if lifecycle not in ALLOWED_LIFECYCLES:
        raise TrackReceiptError("Play Internal release lifecycle is invalid")
    return {
        "releaseName": release_name,
        "track": track,
        "versionCodes": version_codes,
        "releaseLifecycleState": lifecycle,
    }


def select_published_release(
    response: dict[str, object], expected_version_code: int
) -> dict[str, object]:
    if set(response) != ROOT_FIELDS:
        raise TrackReceiptError("Play Internal releases-list envelope drifted")
    releases = response.get("releases")
    if not isinstance(releases, list) or not releases or len(releases) > MAX_RELEASES:
        raise TrackReceiptError("Play Internal releases list is empty or malformed")
    validated = [validate_release(item) for item in releases]
    matching = [
        item for item in validated if expected_version_code in item["versionCodes"]
    ]
    if len(matching) != 1:
        raise TrackReceiptError(
            "Play Internal releases must contain exactly one matching versionCode"
        )
    selected = matching[0]
    if selected["releaseLifecycleState"] != EXPECTED_LIFECYCLE:
        raise TrackReceiptError("Matching Play Internal release is not published")
    return selected


def validate_track_release(value: object) -> dict[str, object]:
    if not isinstance(value, dict):
        raise TrackReceiptError("Play Internal Track release must be an object")
    fields = set(value)
    if not TRACK_RELEASE_REQUIRED_FIELDS <= fields or not fields <= TRACK_RELEASE_FIELDS:
        raise TrackReceiptError("Play Internal Track release fields drifted")
    name = value.get("name")
    if name is not None and (not isinstance(name, str) or not name or len(name) > 1024):
        raise TrackReceiptError("Play Internal Track release name is invalid")
    raw_version_codes = value.get("versionCodes")
    if not isinstance(raw_version_codes, list) or not raw_version_codes:
        raise TrackReceiptError("Play Internal Track versionCodes is empty or malformed")
    version_codes: list[int] = []
    for raw_version_code in raw_version_codes:
        if not isinstance(raw_version_code, str) or not re.fullmatch(r"[1-9][0-9]{0,18}", raw_version_code):
            raise TrackReceiptError("Play Internal Track versionCode is invalid")
        version_codes.append(validate_version_code(int(raw_version_code)))
    if len(version_codes) != len(set(version_codes)):
        raise TrackReceiptError("Play Internal Track release contains duplicate versionCode values")
    status = value.get("status")
    if status not in TRACK_STATUSES:
        raise TrackReceiptError("Play Internal Track release status is invalid")
    if "userFraction" in value or "countryTargeting" in value:
        raise TrackReceiptError("Play Internal qa release must not use staged/country targeting")
    release_notes = value.get("releaseNotes")
    if release_notes is not None:
        if not isinstance(release_notes, list):
            raise TrackReceiptError("Play Internal Track releaseNotes is malformed")
        for note in release_notes:
            if not isinstance(note, dict) or set(note) != {"language", "text"}:
                raise TrackReceiptError("Play Internal Track release note fields drifted")
            if not isinstance(note.get("language"), str) or not note["language"]:
                raise TrackReceiptError("Play Internal Track release note language is invalid")
            if not isinstance(note.get("text"), str):
                raise TrackReceiptError("Play Internal Track release note text is invalid")
    priority = value.get("inAppUpdatePriority")
    if priority is not None and (
        not isinstance(priority, int)
        or isinstance(priority, bool)
        or priority < 0
        or priority > 5
    ):
        raise TrackReceiptError("Play Internal Track update priority is invalid")
    return {"versionCodes": version_codes, "status": status}


def select_completed_track_release(
    response: dict[str, object], expected_version_code: int
) -> dict[str, object]:
    if set(response) != TRACK_ROOT_FIELDS or response.get("track") != EXPECTED_TRACK:
        raise TrackReceiptError("Play Internal Track resource envelope drifted")
    releases = response.get("releases")
    if not isinstance(releases, list) or not releases or len(releases) > MAX_RELEASES:
        raise TrackReceiptError("Play Internal Track releases is empty or malformed")
    validated = [validate_track_release(item) for item in releases]
    matching = [item for item in validated if expected_version_code in item["versionCodes"]]
    if len(matching) != 1:
        raise TrackReceiptError(
            "Play Internal Track must contain exactly one matching versionCode"
        )
    selected = matching[0]
    if selected["status"] != "completed":
        raise TrackReceiptError("Matching Play Internal Track release is not completed")
    return selected


def load_existing_upload_receipt(path: Path) -> bytes:
    if path.is_symlink() or not path.is_file():
        raise TrackReceiptError("C114 Play upload receipt is missing or unsafe")
    try:
        receipt_bytes = path.read_bytes()
    except OSError as error:
        raise TrackReceiptError("C114 Play upload receipt could not be read") from error
    if not receipt_bytes or len(receipt_bytes) > MAX_RESPONSE_BYTES:
        raise TrackReceiptError("C114 Play upload receipt size is invalid")
    return receipt_bytes


def build_receipt(
    upload_receipt: dict[str, object],
    upload_receipt_bytes: bytes,
    release: dict[str, object],
    release_list_response_bytes: bytes,
    track_release: dict[str, object],
    track_response_bytes: bytes,
) -> dict[str, object]:
    if upload_receipt.get("applicationId") != EXPECTED_APPLICATION_ID:
        raise TrackReceiptError("C114 upload receipt application ID is not production")
    version_code = upload_receipt.get("versionCode")
    if not isinstance(version_code, int) or isinstance(version_code, bool):
        raise TrackReceiptError("C114 upload receipt versionCode is invalid")
    if version_code not in release["versionCodes"]:
        raise TrackReceiptError("Play Internal release does not match the C114 versionCode")
    return {
        "schemaVersion": 1,
        "sources": [
            "google-play-developer-api-v3-applications.tracks.releases.list",
            "google-play-developer-api-v3-edits.tracks.get",
        ],
        "applicationId": upload_receipt["applicationId"],
        "handoffDirectory": upload_receipt["handoffDirectory"],
        "commitSha": upload_receipt["commitSha"],
        "versionName": upload_receipt["versionName"],
        "versionCode": version_code,
        "aabSha256": upload_receipt["aabSha256"],
        "uploadReceiptSha256": hashlib.sha256(upload_receipt_bytes).hexdigest(),
        "releaseListResponseSha256": hashlib.sha256(release_list_response_bytes).hexdigest(),
        "trackResponseSha256": hashlib.sha256(track_response_bytes).hexdigest(),
        "track": EXPECTED_TRACK,
        "releaseLifecycleState": release["releaseLifecycleState"],
        "trackStatus": track_release["status"],
        "activeVersionCodes": sorted(release["versionCodes"]),
    }


def write_receipt_atomic(output: Path, receipt: dict[str, object], handoff: Path) -> None:
    expected_name = f"{handoff.name}.play-internal-track-receipt.json"
    if output.name != expected_name:
        raise TrackReceiptError(f"Play Internal track receipt must be named {expected_name}")
    handoff_resolved = handoff.resolve()
    output_parent = output.parent
    if output_parent.is_symlink():
        raise TrackReceiptError("Play Internal track receipt parent is unsafe")
    output_parent.mkdir(parents=True, exist_ok=True)
    output_resolved = output_parent.resolve() / output.name
    if output_resolved == handoff_resolved or output_resolved.is_relative_to(handoff_resolved):
        raise TrackReceiptError(
            "Play Internal track receipt must remain outside the three-file handoff"
        )
    receipt_bytes = canonical_json_bytes(receipt)
    if output.exists() or output.is_symlink():
        if output.is_symlink() or not output.is_file():
            raise TrackReceiptError("Existing Play Internal track receipt is unsafe")
        if output.read_bytes() != receipt_bytes:
            raise TrackReceiptError(
                "Existing Play Internal track receipt conflicts with the verified response"
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


def verify_play_internal_track_receipt(
    handoff: Path,
    bundle_response: Path,
    upload_receipt_path: Path,
    release_list_response: Path,
    track_response: Path,
    output: Path,
    repository_root: Path,
) -> dict[str, object]:
    upload_receipt_bytes = load_existing_upload_receipt(upload_receipt_path)
    upload_receipt = UPLOAD.verify_play_upload_receipt(
        handoff, bundle_response, upload_receipt_path, repository_root
    )
    if upload_receipt_path.read_bytes() != upload_receipt_bytes:
        raise TrackReceiptError("C114 Play upload receipt changed during verification")
    response, release_list_response_bytes = load_track_response(release_list_response)
    track, track_response_bytes = load_track_response(track_response)
    version_code = upload_receipt.get("versionCode")
    if not isinstance(version_code, int) or isinstance(version_code, bool):
        raise TrackReceiptError("C114 Play upload receipt versionCode is invalid")
    release = select_published_release(response, version_code)
    track_release = select_completed_track_release(track, version_code)
    receipt = build_receipt(
        upload_receipt,
        upload_receipt_bytes,
        release,
        release_list_response_bytes,
        track_release,
        track_response_bytes,
    )
    if output.resolve() == upload_receipt_path.resolve():
        raise TrackReceiptError("Play Internal track receipt must not replace the C114 receipt")
    write_receipt_atomic(output, receipt, handoff.resolve())
    return receipt


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Bind a Google Play published Internal-track response to an existing C114 upload receipt."
        )
    )
    parser.add_argument("--handoff", type=Path, required=True)
    parser.add_argument("--bundle-response", type=Path, required=True)
    parser.add_argument("--upload-receipt", type=Path, required=True)
    parser.add_argument("--release-list-response", type=Path, required=True)
    parser.add_argument("--track-response", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository-root", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        receipt = verify_play_internal_track_receipt(
            args.handoff,
            args.bundle_response,
            args.upload_receipt,
            args.release_list_response,
            args.track_response,
            args.output,
            args.repository_root,
        )
    except (
        TrackReceiptError,
        UPLOAD.ReceiptError,
        UPLOAD.HANDOFF.HANDOFF.HandoffError,
        OSError,
    ) as error:
        print(str(error), file=sys.stderr)
        return 1
    print(f"Google Play Internal track receipt verified: {args.output}")
    print(
        f"TRACK={receipt['track']} VERSION_CODE={receipt['versionCode']} "
        f"STATE={receipt['releaseLifecycleState']} TRACK_STATUS={receipt['trackStatus']}"
    )
    print(f"COMMIT={receipt['commitSha']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
