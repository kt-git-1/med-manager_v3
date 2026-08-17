#!/usr/bin/env python3
"""Fail-closed audit of the committed android-dev surface that will enter main."""

from __future__ import annotations

import argparse
import subprocess
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Sequence


MAX_CHANGED_FILES = 1_250
MAX_SINGLE_BLOB_BYTES = 2 * 1024 * 1024
MAX_CHANGED_TREE_BYTES = 400 * 1024 * 1024

EXPECTED_TOP_LEVEL = {".github", ".gitignore", "android", "api", "docs"}
EXPECTED_WORKFLOW_PATHS = {".github/workflows/android-ci.yml"}
EXPECTED_ROOT_PATHS = {".gitignore"}
EXPECTED_API_PATHS = {
    "api/app/.well-known/assetlinks.json/route.ts",
    "api/app/api/patients/[patientId]/medications/[medicationId]/inventory/adjust/route.ts",
    "api/app/api/patients/[patientId]/prn-dose-records/route.ts",
    "api/app/privacy/page.tsx",
    "api/prisma/migrations/20260817090000_android_mutation_idempotency/migration.sql",
    "api/prisma/schema.prisma",
    "api/scripts/release-security-check.mjs",
    "api/src/repositories/doseRecordRepo.ts",
    "api/src/repositories/prnDoseRecordRepo.ts",
    "api/src/services/doseRecordService.ts",
    "api/src/services/fcmService.ts",
    "api/src/services/medicationService.ts",
    "api/src/services/prnDoseRecordService.ts",
    "api/src/services/pushNotificationService.ts",
    "api/src/validators/inventory.ts",
    "api/src/validators/prnDoseRecord.ts",
    "api/src/validators/pushRegister.ts",
    "api/tests/integration/android-assetlinks.test.ts",
    "api/tests/integration/dose-record-event.test.ts",
    "api/tests/integration/dose-recording-caregiver.test.ts",
    "api/tests/integration/dose-recording-patient.test.ts",
    "api/tests/integration/inventory-adjust-idempotency.test.ts",
    "api/tests/integration/inventory-taken-create.test.ts",
    "api/tests/integration/inventory-taken-delete.test.ts",
    "api/tests/integration/prn-dose-records.test.ts",
    "api/tests/integration/push-register.test.ts",
    "api/tests/integration/push-send-trigger.test.ts",
    "api/tests/unit/client-mutation-id-validation.test.ts",
    "api/tests/unit/dose-record-create-ownership.test.ts",
    "api/tests/unit/prn-dose-record-idempotency.test.ts",
}
REQUIRED_ANDROID_PATHS = {
    "android/app/build.gradle.kts",
    "android/app/src/main/AndroidManifest.xml",
    "android/gradlew",
    "android/settings.gradle.kts",
}
REQUIRED_AUTHORITY_DOCS = {
    "docs/android/README.md",
    "docs/android/android-port-master-plan.md",
    "docs/android/parity-requirements.md",
    "docs/android/play-release-runbook.md",
}
FORBIDDEN_NAMES = {
    ".env",
    ".ds_store",
    "google-services.json",
    "local.properties",
    "secrets.properties",
    "service-account.json",
}
FORBIDDEN_SUFFIXES = {
    ".aab",
    ".apk",
    ".apks",
    ".jks",
    ".key",
    ".keystore",
    ".p8",
    ".p12",
    ".pem",
}
FORBIDDEN_SEGMENTS = {".gradle", ".idea", "build", "deriveddata", "node_modules"}
REQUIRED_GITIGNORE_LINES = {
    "!docs/android/**",
    "android/.gradle/",
    "android/.kotlin/",
    "android/local.properties",
    "android/**/build/",
    "*.jks",
    "*.keystore",
}
EXPECTED_GITIGNORE_ADDED_LINES = (
    "!docs/",
    "docs/*",
    "!docs/android/",
    "!docs/android/**",
    "output/",
    "",
    "# Android",
    "android/.gradle/",
    "android/.kotlin/",
    "android/local.properties",
    "android/**/build/",
    "*.jks",
    "*.keystore",
)
EXPECTED_GITIGNORE_REMOVED_LINES = ("output/",)


class MergeSurfaceError(ValueError):
    """Raised when a committed merge surface is unsafe or unreviewed."""


@dataclass(frozen=True)
class Blob:
    mode: str
    size: int


@dataclass(frozen=True)
class MergeSummary:
    base_sha: str
    head_sha: str
    commits: int
    files: int
    bytes: int
    scopes: Counter[str]


def _git(root: Path, *arguments: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        ["git", "-C", str(root), *arguments],
        check=False,
        capture_output=True,
        text=True,
    )
    if check and result.returncode != 0:
        operation = " ".join(arguments[:2])
        raise MergeSurfaceError(f"git operation failed ({operation})")
    return result


def _resolve_commit(root: Path, reference: str, label: str) -> str:
    result = _git(root, "rev-parse", "--verify", f"{reference}^{{commit}}", check=False)
    if result.returncode != 0:
        raise MergeSurfaceError(f"{label} commit is unavailable: {reference}")
    return result.stdout.strip()


def _changed_paths(root: Path, base: str, head: str) -> set[str]:
    result = _git(root, "diff", "--name-status", f"{base}...{head}")
    paths: set[str] = set()
    for line in result.stdout.splitlines():
        fields = line.split("\t")
        if len(fields) != 2 or fields[0] not in {"A", "M"}:
            raise MergeSurfaceError("merge surface may contain only added or modified paths")
        path = fields[1]
        if path in paths:
            raise MergeSurfaceError(f"duplicate changed path: {path}")
        paths.add(path)
    if not paths:
        raise MergeSurfaceError("merge surface is empty")
    return paths


def _head_blobs(root: Path, head: str) -> dict[str, Blob]:
    result = subprocess.run(
        ["git", "-C", str(root), "ls-tree", "-r", "-l", "-z", head],
        check=False,
        capture_output=True,
    )
    if result.returncode != 0:
        raise MergeSurfaceError("head tree could not be inspected")
    blobs: dict[str, Blob] = {}
    for record in result.stdout.split(b"\0"):
        if not record:
            continue
        metadata, raw_path = record.split(b"\t", 1)
        mode, object_type, _object_id, raw_size = metadata.decode("ascii").split()
        path = raw_path.decode("utf-8")
        if object_type != "blob" or raw_size == "-":
            raise MergeSurfaceError(f"non-blob entry is not allowed in the merge tree: {path}")
        blobs[path] = Blob(mode=mode, size=int(raw_size))
    return blobs


def _validate_path_policy(paths: set[str], blobs: dict[str, Blob]) -> Counter[str]:
    if len(paths) > MAX_CHANGED_FILES:
        raise MergeSurfaceError(f"changed file count exceeds {MAX_CHANGED_FILES}")
    top_level = {PurePosixPath(path).parts[0] for path in paths}
    if top_level != EXPECTED_TOP_LEVEL:
        raise MergeSurfaceError(f"top-level merge scopes are not exact: {sorted(top_level)}")

    workflows = {path for path in paths if path.startswith(".github/")}
    if workflows != EXPECTED_WORKFLOW_PATHS:
        raise MergeSurfaceError("only the reviewed Android CI workflow may change")
    roots = {path for path in paths if "/" not in path}
    if roots != EXPECTED_ROOT_PATHS:
        raise MergeSurfaceError("only the reviewed root .gitignore may change")
    api_paths = {path for path in paths if path.startswith("api/")}
    if api_paths != EXPECTED_API_PATHS:
        missing = sorted(EXPECTED_API_PATHS - api_paths)
        extra = sorted(api_paths - EXPECTED_API_PATHS)
        raise MergeSurfaceError(
            "API merge allowlist drifted "
            f"(missing={len(missing)} extra={len(extra)})"
        )
    if any(not path.startswith("docs/android/") for path in paths if path.startswith("docs/")):
        raise MergeSurfaceError("documentation changes must remain under docs/android")
    if not REQUIRED_ANDROID_PATHS.issubset(paths):
        raise MergeSurfaceError("required Android project roots are missing from the merge surface")
    if not REQUIRED_AUTHORITY_DOCS.issubset(paths):
        raise MergeSurfaceError("required Android authority documents are missing from the merge surface")

    scopes: Counter[str] = Counter()
    for path in sorted(paths):
        pure = PurePosixPath(path)
        lower_name = pure.name.lower()
        lower_parts = {part.lower() for part in pure.parts}
        if lower_name in FORBIDDEN_NAMES or pure.suffix.lower() in FORBIDDEN_SUFFIXES:
            raise MergeSurfaceError(f"private/generated artifact is forbidden: {path}")
        if lower_parts.intersection(FORBIDDEN_SEGMENTS):
            raise MergeSurfaceError(f"generated directory is forbidden: {path}")
        blob = blobs.get(path)
        if blob is None:
            raise MergeSurfaceError(f"changed path is missing from the head tree: {path}")
        if blob.mode not in {"100644", "100755"}:
            raise MergeSurfaceError(f"unsupported file mode {blob.mode}: {path}")
        if blob.size > MAX_SINGLE_BLOB_BYTES:
            raise MergeSurfaceError(f"changed blob exceeds {MAX_SINGLE_BLOB_BYTES} bytes: {path}")
        if path.startswith("docs/android/"):
            scopes["docs/android"] += 1
        else:
            scopes[pure.parts[0]] += 1
    return scopes


def verify_merge_surface(root: Path, base_ref: str, head_ref: str) -> MergeSummary:
    if not (root / ".git").exists() and not (root / ".git").is_file():
        raise MergeSurfaceError("repository root must contain .git")
    base_sha = _resolve_commit(root, base_ref, "base")
    head_sha = _resolve_commit(root, head_ref, "head")
    if _git(root, "merge-base", "--is-ancestor", base_sha, head_sha, check=False).returncode != 0:
        raise MergeSurfaceError("latest origin/main is not an ancestor of android-dev")
    merge_base = _git(root, "merge-base", base_sha, head_sha).stdout.strip()
    if merge_base != base_sha:
        raise MergeSurfaceError("merge base does not equal latest origin/main")

    commits = int(_git(root, "rev-list", "--count", f"{base_sha}..{head_sha}").stdout.strip())
    if commits <= 0:
        raise MergeSurfaceError("android-dev must contain committed work after origin/main")
    paths = _changed_paths(root, base_sha, head_sha)
    blobs = _head_blobs(root, head_sha)
    scopes = _validate_path_policy(paths, blobs)
    total_bytes = sum(blobs[path].size for path in paths)
    if total_bytes > MAX_CHANGED_TREE_BYTES:
        raise MergeSurfaceError(f"changed tree exceeds {MAX_CHANGED_TREE_BYTES} bytes")

    gitignore = _git(root, "show", f"{head_sha}:.gitignore").stdout.splitlines()
    missing_ignores = REQUIRED_GITIGNORE_LINES - set(gitignore)
    if missing_ignores:
        raise MergeSurfaceError(f"Android ignore policy is incomplete ({len(missing_ignores)} missing)")
    ignore_diff = _git(root, "diff", "--unified=0", base_sha, head_sha, "--", ".gitignore").stdout
    added_lines = tuple(
        line[1:]
        for line in ignore_diff.splitlines()
        if line.startswith("+") and not line.startswith("+++")
    )
    removed_lines = tuple(
        line[1:]
        for line in ignore_diff.splitlines()
        if line.startswith("-") and not line.startswith("---")
    )
    if (
        added_lines != EXPECTED_GITIGNORE_ADDED_LINES
        or removed_lines != EXPECTED_GITIGNORE_REMOVED_LINES
    ):
        raise MergeSurfaceError("committed .gitignore delta is not the exact reviewed Android policy")

    return MergeSummary(
        base_sha=base_sha,
        head_sha=head_sha,
        commits=commits,
        files=len(paths),
        bytes=total_bytes,
        scopes=scopes,
    )


def _arguments(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify the committed android-dev to main merge surface.")
    parser.add_argument("--repository-root", type=Path, required=True)
    parser.add_argument("--base-ref", default="origin/main")
    parser.add_argument("--head-ref", default="HEAD")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _arguments(argv or sys.argv[1:])
    try:
        summary = verify_merge_surface(
            arguments.repository_root.resolve(), arguments.base_ref, arguments.head_ref
        )
    except MergeSurfaceError as error:
        print(f"Main merge surface verification failed: {error}", file=sys.stderr)
        return 1
    scope_text = ",".join(f"{key}={summary.scopes[key]}" for key in sorted(summary.scopes))
    print("Main merge surface verification passed.")
    print(
        f"base={summary.base_sha} head={summary.head_sha} commits={summary.commits} "
        f"files={summary.files} bytes={summary.bytes} {scope_text} ios=0"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
