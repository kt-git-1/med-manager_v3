#!/usr/bin/env python3

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile


EXPECTED_APPLICATION_ID = "com.afterlifearchive.medmanager"
EXPECTED_STORE_SCREENSHOTS = (
    "01-mode-select.jpg",
    "02-patient-today.jpg",
    "03-patient-history.jpg",
    "04-caregiver-today.jpg",
    "05-caregiver-medications.jpg",
    "06-caregiver-inventory.jpg",
    "07-caregiver-history.jpg",
    "08-caregiver-settings.jpg",
)
EXPECTED_GATES = (
    "production-runtime",
    "upload-keystore",
    "release-sdk-policy",
    "release-apk-compatibility",
    "release-aab-content",
    "bundle-install-surface",
    "device-split-install-surface",
    "play-store-assets",
    "signed-aab-certificate",
    "release-evidence-policy",
)


class HandoffError(RuntimeError):
    pass


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_store_listing(report: object, repository_root: Path) -> list[str]:
    if not isinstance(report, dict):
        return ["Release evidence is missing store listing hashes"]
    expected_fields = {
        "locale",
        "listingSha256",
        "screenshotSourceMapSha256",
        "icon512Sha256",
        "featureGraphicSha256",
        "screenshots",
    }
    if set(report) != expected_fields:
        return ["Release evidence store listing fields drifted"]
    asset_root = repository_root / "docs/android/play-store-assets"
    phone_directory = asset_root / "phone-ja-JP"
    required_paths = (
        repository_root / "docs/android/play-store-listing-ja.md",
        phone_directory / "sources.tsv",
        asset_root / "icon-512.png",
        asset_root / "feature-graphic-1024x500.jpg",
        *(phone_directory / name for name in EXPECTED_STORE_SCREENSHOTS),
    )
    expected_phone_entries = tuple(sorted((*EXPECTED_STORE_SCREENSHOTS, "sources.tsv")))
    try:
        actual_phone_entries = tuple(sorted(path.name for path in phone_directory.iterdir()))
    except OSError:
        return ["Repository Play phone asset directory could not be read"]
    if actual_phone_entries != expected_phone_entries:
        return ["Repository Play phone asset directory contains unexpected or missing entries"]
    if any(not path.is_file() or path.is_symlink() for path in required_paths):
        return ["Repository Play store input is missing or unsafe"]
    expected_hashes = {
        "listingSha256": file_sha256(required_paths[0]),
        "screenshotSourceMapSha256": file_sha256(required_paths[1]),
        "icon512Sha256": file_sha256(required_paths[2]),
        "featureGraphicSha256": file_sha256(required_paths[3]),
    }
    failures: list[str] = []
    if report.get("locale") != "ja-JP":
        failures.append("Release evidence store listing locale drifted")
    for field, expected in expected_hashes.items():
        if report.get(field) != expected:
            failures.append(f"Release evidence {field} does not match the repository input")
    screenshots = report.get("screenshots")
    if not isinstance(screenshots, list) or len(screenshots) != len(EXPECTED_STORE_SCREENSHOTS):
        failures.append("Release evidence screenshot hash inventory is incomplete")
        return failures
    for item, name in zip(screenshots, EXPECTED_STORE_SCREENSHOTS, strict=True):
        expected_sha = file_sha256(phone_directory / name)
        if not isinstance(item, dict) or set(item) != {"fileName", "sha256"}:
            failures.append("Release evidence screenshot hash entry is malformed")
        elif item.get("fileName") != name or item.get("sha256") != expected_sha:
            failures.append(f"Release evidence screenshot does not match repository input: {name}")
    return failures


def handoff_identity(
    report: dict[str, object],
    aab: Path,
    repository_root: Path,
    *,
    expected_artifact_name: str | None = None,
) -> tuple[str, str, int, str]:
    failures: list[str] = []
    source = report.get("source")
    application = report.get("application")
    artifact = report.get("artifact")
    gates = report.get("verifiedGates")
    if report.get("schemaVersion") != 2:
        failures.append("Unsupported release evidence schema")
    if not isinstance(source, dict) or not isinstance(application, dict) or not isinstance(artifact, dict):
        raise HandoffError("Release evidence is missing source/application/artifact objects")

    commit_sha = source.get("commitSha")
    version_code = application.get("versionCode")
    version_name = application.get("versionName")
    expected_aab_sha = artifact.get("sha256")
    if not isinstance(commit_sha, str) or not re.fullmatch(r"[0-9a-f]{40}", commit_sha):
        failures.append("Release evidence source commit is malformed")
    if source.get("releaseInputTreeClean") is not True:
        failures.append("Release evidence does not prove a clean input tree")
    if application.get("applicationId") != EXPECTED_APPLICATION_ID:
        failures.append("Release evidence application ID is not production")
    if not isinstance(version_code, int) or isinstance(version_code, bool) or version_code <= 0:
        failures.append("Release evidence versionCode is invalid")
    if not isinstance(version_name, str) or not re.fullmatch(
        r"[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?", version_name
    ):
        failures.append("Release evidence versionName is invalid")
    artifact_name = artifact.get("fileName")
    if not isinstance(artifact_name, str) or not re.fullmatch(r"[A-Za-z0-9._-]+\.aab", artifact_name):
        failures.append("Release evidence source AAB name is unsafe")
    if artifact_name != (expected_artifact_name or aab.name):
        failures.append("Release evidence names a different source AAB")
    if not isinstance(expected_aab_sha, str) or not re.fullmatch(r"[0-9a-f]{64}", expected_aab_sha):
        failures.append("Release evidence AAB SHA-256 is malformed")
    elif file_sha256(aab) != expected_aab_sha:
        failures.append("Release evidence does not match the supplied AAB")
    signer = artifact.get("uploadCertificateSha256")
    if not isinstance(signer, str) or not re.fullmatch(r"[0-9a-f]{64}", signer):
        failures.append("Release evidence upload certificate is malformed")
    if artifact.get("modules") != ["base"]:
        failures.append("Release evidence is not base-module-only")
    if gates != list(EXPECTED_GATES):
        failures.append("Release evidence gate set is incomplete or reordered")
    try:
        failures.extend(validate_store_listing(report.get("storeListing"), repository_root))
    except OSError as error:
        raise HandoffError("Repository Play store inputs could not be read") from error
    if failures:
        raise HandoffError("Play release handoff policy failed:\n - " + "\n - ".join(failures))
    assert isinstance(commit_sha, str)
    assert isinstance(version_code, int)
    assert isinstance(version_name, str)
    assert isinstance(expected_aab_sha, str)
    return commit_sha, version_name, version_code, expected_aab_sha


def expected_checksum_text(aab_name: str, aab_sha256: str, evidence_sha256: str) -> str:
    return f"{aab_sha256}  {aab_name}\n{evidence_sha256}  play-release-evidence.json\n"


def validate_existing_handoff(
    target: Path,
    aab_name: str,
    aab_sha256: str,
    evidence_sha256: str,
) -> None:
    expected_names = {aab_name, "play-release-evidence.json", "SHA256SUMS"}
    actual_names = {entry.name for entry in target.iterdir()}
    if actual_names != expected_names or any(
        entry.is_symlink() or not entry.is_file() for entry in target.iterdir()
    ):
        raise HandoffError("Existing handoff directory has missing or unexpected entries")
    packaged_aab = target / aab_name
    packaged_evidence = target / "play-release-evidence.json"
    if file_sha256(packaged_aab) != aab_sha256:
        raise HandoffError("Existing handoff AAB differs from the verified artifact hash")
    if file_sha256(packaged_evidence) != evidence_sha256:
        raise HandoffError("Existing handoff evidence differs from the verified ledger bytes")
    expected_checksums = expected_checksum_text(aab_name, aab_sha256, evidence_sha256)
    if (target / "SHA256SUMS").read_text(encoding="utf-8") != expected_checksums:
        raise HandoffError("Existing handoff checksum manifest is invalid")


def prepare_handoff(aab: Path, evidence: Path, output_root: Path, repository_root: Path) -> Path:
    try:
        evidence_bytes = evidence.read_bytes()
        report = json.loads(evidence_bytes.decode("utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise HandoffError("Release evidence JSON could not be read") from error
    if not isinstance(report, dict):
        raise HandoffError("Release evidence root must be an object")
    commit_sha, version_name, version_code, aab_sha256 = handoff_identity(
        report, aab, repository_root.resolve()
    )
    handoff_name = f"v{version_name}-code{version_code}-{commit_sha[:12]}"
    aab_name = f"med-manager-android-{handoff_name}.aab"
    evidence_sha256 = hashlib.sha256(evidence_bytes).hexdigest()
    output_root.mkdir(parents=True, exist_ok=True)
    target = output_root / handoff_name
    if target.exists():
        if target.is_symlink() or not target.is_dir():
            raise HandoffError("Existing handoff target is not a directory")
        validate_existing_handoff(target, aab_name, aab_sha256, evidence_sha256)
        return target

    temporary = Path(tempfile.mkdtemp(prefix=".handoff-", dir=output_root))
    try:
        packaged_aab = temporary / aab_name
        packaged_evidence = temporary / "play-release-evidence.json"
        shutil.copyfile(aab, packaged_aab)
        packaged_evidence.write_bytes(evidence_bytes)
        (temporary / "SHA256SUMS").write_text(
            expected_checksum_text(aab_name, aab_sha256, evidence_sha256),
            encoding="utf-8",
        )
        validate_existing_handoff(temporary, aab_name, aab_sha256, evidence_sha256)
        os.replace(temporary, target)
    except BaseException:
        shutil.rmtree(temporary, ignore_errors=True)
        raise
    return target


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Prepare an exact Android Play release handoff directory.")
    parser.add_argument("--aab", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--repository-root", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        target = prepare_handoff(args.aab, args.evidence, args.output_root, args.repository_root)
    except (HandoffError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 1
    print(f"Play release handoff prepared: {target}")
    print(f"AAB_SHA256={file_sha256(next(target.glob('*.aab')))}")
    print("FILES=3")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
