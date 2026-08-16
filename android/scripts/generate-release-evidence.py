#!/usr/bin/env python3

from __future__ import annotations

import argparse
import hashlib
import json
import os
from dataclasses import dataclass
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile


EXPECTED_APPLICATION_ID = "com.afterlifearchive.medmanager"
RELEASE_INPUT_PATHS = (
    "android",
    "ios/MedicationApp/Assets.xcassets/RolePatient.imageset",
    "ios/MedicationApp/Assets.xcassets/RoleFamily.imageset",
    "ios/MedicationApp/Assets.xcassets/AppImage.imageset",
    "ios/MedicationApp/Assets.xcassets/AppIcon.appiconset",
    "docs/android/play-store-listing-ja.md",
    "docs/android/play-store-assets",
)


class EvidenceError(RuntimeError):
    pass


@dataclass(frozen=True)
class PolicyInput:
    application_id: str
    version_code: int
    version_name: str
    source_commit: str
    release_input_changes: tuple[str, ...]
    aab_sha256: str
    actual_signer_sha256: str
    expected_signer_sha256: str
    dependency_lock_sha256: str
    sdk_inventory_sha256: str
    modules: tuple[str, ...]
    dex_file_count: int
    resolved_module_count: int
    dependency_lock_coordinate_count: int


def evidence_failures(value: PolicyInput) -> list[str]:
    failures: list[str] = []
    if value.application_id != EXPECTED_APPLICATION_ID:
        failures.append("Unexpected application ID")
    if value.version_code <= 0:
        failures.append("versionCode must be positive")
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?", value.version_name):
        failures.append("versionName must be a semantic public version")
    if not re.fullmatch(r"[0-9a-f]{40}", value.source_commit):
        failures.append("Source commit must be a full Git SHA")
    if value.release_input_changes:
        failures.append("Release inputs contain uncommitted changes")
    for label, digest in (
        ("AAB", value.aab_sha256),
        ("actual signer", value.actual_signer_sha256),
        ("expected signer", value.expected_signer_sha256),
        ("dependency lock", value.dependency_lock_sha256),
        ("SDK inventory", value.sdk_inventory_sha256),
    ):
        if not re.fullmatch(r"[0-9a-f]{64}", digest):
            failures.append(f"{label} SHA-256 is malformed")
    if value.actual_signer_sha256 != value.expected_signer_sha256:
        failures.append("AAB signer does not match upload certificate")
    if value.modules != ("base",):
        failures.append("Only the reviewed base module may be released")
    if value.dex_file_count <= 0:
        failures.append("Release AAB contains no DEX")
    if value.resolved_module_count <= 0:
        failures.append("Release SDK inventory is empty")
    if value.dependency_lock_coordinate_count <= 0:
        failures.append("Dependency lock is empty")
    return failures


def normalized_sha256_fingerprint(value: str) -> str | None:
    normalized = re.sub(r"[:\s]", "", value).lower()
    return normalized if re.fullmatch(r"[0-9a-f]{64}", normalized) else None


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def dependency_lock_coordinate_count(contents: str) -> int:
    return sum(
        bool(line.strip()) and not line.lstrip().startswith("#") and line.strip() != "empty="
        for line in contents.splitlines()
    )


def run_command(repository_root: Path, command: list[str], *, allow_failure: bool = False) -> str:
    environment = os.environ.copy()
    environment["LC_ALL"] = "C"
    completed = subprocess.run(
        command,
        cwd=repository_root,
        env=environment,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    output = completed.stdout.strip()
    if completed.returncode != 0 and not allow_failure:
        raise EvidenceError(f"Command failed: {command[0]}")
    return output if completed.returncode == 0 else ""


def write_json_atomic(output_path: Path, report: dict[str, object]) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=output_path.parent,
            prefix=f"{output_path.name}.",
            suffix=".tmp",
            delete=False,
        ) as temporary:
            temporary_name = temporary.name
            json.dump(report, temporary, ensure_ascii=False, indent=2)
            temporary.write("\n")
            temporary.flush()
            os.fsync(temporary.fileno())
        os.replace(temporary_name, output_path)
    except BaseException:
        if temporary_name:
            Path(temporary_name).unlink(missing_ok=True)
        raise


def generate_report(args: argparse.Namespace) -> dict[str, object]:
    repository_root = args.repository_root.resolve()
    source_commit = run_command(repository_root, ["git", "rev-parse", "HEAD"])
    source_branch = os.environ.get("GITHUB_REF_NAME") or run_command(
        repository_root,
        ["git", "symbolic-ref", "--short", "-q", "HEAD"],
        allow_failure=True,
    ) or "detached"
    changed_output = run_command(
        repository_root,
        ["git", "status", "--porcelain=v1", "--untracked-files=all", "--", *RELEASE_INPUT_PATHS],
    )
    release_input_changes = tuple(line for line in changed_output.splitlines() if line)

    with zipfile.ZipFile(args.aab) as bundle:
        entries = set(bundle.namelist())
    modules = tuple(
        sorted(
            entry.split("/", 1)[0]
            for entry in entries
            if re.fullmatch(r"[^/]+/manifest/AndroidManifest\.xml", entry)
        )
    )
    dex_file_count = sum(bool(re.fullmatch(r"base/dex/classes[0-9]*\.dex", entry)) for entry in entries)
    native_library_count = sum(
        bool(re.fullmatch(r"base/lib/[^/]+/[^/]+\.so", entry)) for entry in entries
    )

    inventory_text = args.inventory.read_text(encoding="utf-8")
    module_match = re.search(r"^Resolved modules: ([0-9]+)$", inventory_text, re.MULTILINE)
    resolved_module_count = int(module_match.group(1)) if module_match else 0
    lock_coordinate_count = dependency_lock_coordinate_count(
        args.dependency_lock.read_text(encoding="utf-8")
    )

    certificate_output = run_command(
        repository_root,
        ["keytool", "-printcert", "-jarfile", str(args.aab.resolve())],
    )
    signer_fingerprints = {
        normalized
        for match in re.finditer(r"SHA256:\s*([0-9A-Fa-f:]+)", certificate_output)
        if (normalized := normalized_sha256_fingerprint(match.group(1))) is not None
    }
    if len(signer_fingerprints) != 1:
        raise EvidenceError("Expected exactly one signed AAB certificate fingerprint")

    expected_signer = normalized_sha256_fingerprint(args.expected_signer_sha256)
    if expected_signer is None:
        raise EvidenceError("Expected upload certificate SHA-256 is malformed")
    policy_input = PolicyInput(
        application_id=args.application_id,
        version_code=args.version_code,
        version_name=args.version_name,
        source_commit=source_commit,
        release_input_changes=release_input_changes,
        aab_sha256=file_sha256(args.aab),
        actual_signer_sha256=next(iter(signer_fingerprints)),
        expected_signer_sha256=expected_signer,
        dependency_lock_sha256=file_sha256(args.dependency_lock),
        sdk_inventory_sha256=file_sha256(args.inventory),
        modules=modules,
        dex_file_count=dex_file_count,
        resolved_module_count=resolved_module_count,
        dependency_lock_coordinate_count=lock_coordinate_count,
    )
    failures = evidence_failures(policy_input)
    if failures:
        details = "\n - ".join(failures)
        if release_input_changes:
            details += "\nChanged release inputs:\n - " + "\n - ".join(release_input_changes)
        raise EvidenceError(f"Signed release evidence policy failed:\n - {details}")

    return {
        "schemaVersion": 1,
        "source": {
            "commitSha": source_commit,
            "branch": source_branch,
            "releaseInputTreeClean": True,
            "publishedIosApiBaselineSha": args.baseline_sha,
        },
        "application": {
            "applicationId": args.application_id,
            "versionCode": args.version_code,
            "versionName": args.version_name,
            "minSdk": args.min_sdk,
            "targetSdk": args.target_sdk,
        },
        "artifact": {
            "fileName": args.aab.name,
            "sizeBytes": args.aab.stat().st_size,
            "sha256": policy_input.aab_sha256,
            "uploadCertificateSha256": policy_input.actual_signer_sha256,
            "modules": list(modules),
            "dexFileCount": dex_file_count,
            "nativeLibraryCount": native_library_count,
            "baseManifestSha256": file_sha256(args.manifest),
        },
        "dependencies": {
            "resolvedModuleCount": resolved_module_count,
            "sdkInventorySha256": policy_input.sdk_inventory_sha256,
            "lockCoordinateCount": lock_coordinate_count,
            "gradleLockSha256": policy_input.dependency_lock_sha256,
            "bundletoolVersion": args.bundletool_version,
        },
        "verifiedGates": [
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
        ],
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate exact signed Android release evidence.")
    parser.add_argument("--repository-root", type=Path, required=True)
    parser.add_argument("--aab", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--dependency-lock", type=Path, required=True)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--application-id", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--min-sdk", type=int, required=True)
    parser.add_argument("--target-sdk", type=int, required=True)
    parser.add_argument("--baseline-sha", required=True)
    parser.add_argument("--expected-signer-sha256", required=True)
    parser.add_argument("--bundletool-version", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        report = generate_report(args)
        write_json_atomic(args.output, report)
    except (EvidenceError, OSError, ValueError, zipfile.BadZipFile) as error:
        print(str(error), file=sys.stderr)
        return 1
    print(f"Signed Play release evidence written: {args.output}")
    print(f"SOURCE_COMMIT={report['source']['commitSha']}")
    print(f"VERSION_CODE={report['application']['versionCode']} VERSION_NAME={report['application']['versionName']}")
    print(f"AAB_SHA256={report['artifact']['sha256']}")
    print(f"UPLOAD_CERT_SHA256={report['artifact']['uploadCertificateSha256']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
