#!/usr/bin/env python3

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys


MODULE_NAME = "prepare_play_release_handoff"
if MODULE_NAME in sys.modules:
    HANDOFF = sys.modules[MODULE_NAME]
else:
    script = Path(__file__).with_name("prepare-play-release-handoff.py")
    spec = importlib.util.spec_from_file_location(MODULE_NAME, script)
    if spec is None or spec.loader is None:
        raise RuntimeError("Unable to load Play handoff policy")
    HANDOFF = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = HANDOFF
    spec.loader.exec_module(HANDOFF)


def load_canonical_report(evidence: Path) -> tuple[dict[str, object], bytes]:
    try:
        evidence_bytes = evidence.read_bytes()
        report = json.loads(evidence_bytes.decode("utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise HANDOFF.HandoffError("Retained handoff evidence JSON could not be read") from error
    if not isinstance(report, dict):
        raise HANDOFF.HandoffError("Retained handoff evidence root must be an object")
    canonical = (json.dumps(report, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    if evidence_bytes != canonical:
        raise HANDOFF.HandoffError("Retained handoff evidence JSON is not canonical")
    return report, evidence_bytes


def verify_prepared_handoff(target: Path, repository_root: Path) -> dict[str, object]:
    if target.is_symlink() or not target.is_dir():
        raise HANDOFF.HandoffError("Retained handoff target is not a safe directory")
    target = target.resolve()
    repository_root = repository_root.resolve()
    evidence = target / "play-release-evidence.json"
    if evidence.is_symlink() or not evidence.is_file():
        raise HANDOFF.HandoffError("Retained handoff evidence is missing or unsafe")
    report, evidence_bytes = load_canonical_report(evidence)
    source = report.get("source")
    application = report.get("application")
    artifact = report.get("artifact")
    if not isinstance(source, dict) or not isinstance(application, dict) or not isinstance(artifact, dict):
        raise HANDOFF.HandoffError("Retained handoff evidence is missing identity objects")
    commit_sha = source.get("commitSha")
    version_name = application.get("versionName")
    version_code = application.get("versionCode")
    original_aab_name = artifact.get("fileName")
    if not isinstance(commit_sha, str) or not re.fullmatch(r"[0-9a-f]{40}", commit_sha):
        raise HANDOFF.HandoffError("Retained handoff commit is malformed")
    if not isinstance(version_name, str) or not re.fullmatch(
        r"[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?", version_name
    ):
        raise HANDOFF.HandoffError("Retained handoff versionName is malformed")
    if not isinstance(version_code, int) or isinstance(version_code, bool) or version_code <= 0:
        raise HANDOFF.HandoffError("Retained handoff versionCode is malformed")
    if not isinstance(original_aab_name, str):
        raise HANDOFF.HandoffError("Retained handoff source AAB name is missing")

    handoff_name = f"v{version_name}-code{version_code}-{commit_sha[:12]}"
    if target.name != handoff_name:
        raise HANDOFF.HandoffError("Retained handoff directory name does not match its ledger")
    packaged_aab_name = f"med-manager-android-{handoff_name}.aab"
    packaged_aab = target / packaged_aab_name
    _, _, _, aab_sha256 = HANDOFF.handoff_identity(
        report,
        packaged_aab,
        repository_root,
        expected_artifact_name=original_aab_name,
    )
    evidence_sha256 = hashlib.sha256(evidence_bytes).hexdigest()
    HANDOFF.validate_existing_handoff(
        target,
        packaged_aab_name,
        aab_sha256,
        evidence_sha256,
    )
    return {
        "directory": target.name,
        "commitSha": commit_sha,
        "versionName": version_name,
        "versionCode": version_code,
        "aabSha256": aab_sha256,
        "evidenceSha256": evidence_sha256,
        "files": 3,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Reverify a retained Android Play handoff.")
    parser.add_argument("--handoff", type=Path, required=True)
    parser.add_argument("--repository-root", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        summary = verify_prepared_handoff(args.handoff, args.repository_root)
    except (HANDOFF.HandoffError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 1
    print(f"Retained Play handoff verification passed: {args.handoff}")
    print(
        f"VERSION_NAME={summary['versionName']} VERSION_CODE={summary['versionCode']} "
        f"COMMIT={summary['commitSha']}"
    )
    print(f"AAB_SHA256={summary['aabSha256']} EVIDENCE_SHA256={summary['evidenceSha256']}")
    print("FILES=3")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
