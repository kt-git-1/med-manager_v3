#!/usr/bin/env python3
"""Synthetic accepted/rejected fixtures for verify-release-gates.py."""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
from pathlib import Path
from typing import Callable


SCRIPT = Path(__file__).with_name("verify-release-gates.py")
REPOSITORY_ROOT = SCRIPT.parents[2]
SOURCE_MANIFEST = REPOSITORY_ROOT / "docs/android/release-gates.json"
SPEC = importlib.util.spec_from_file_location("verify_release_gates", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load release gate verifier")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def canonical(value: dict) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def write(path: Path, content: str = "fixture\n") -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def create_fixture(root: Path) -> dict[str, Path]:
    manifest = json.loads(SOURCE_MANIFEST.read_text(encoding="utf-8"))
    manifest_path = root / "docs/android/release-gates.json"
    requirements_path = root / "docs/android/parity-requirements.md"
    backlog_path = root / "docs/android/execution-backlog.md"
    readme_path = root / "docs/android/README.md"
    master_plan_path = root / "docs/android/android-port-master-plan.md"
    write(manifest_path, canonical(manifest))
    requirement_lines = [
        "# Requirements",
        "Published baseline main@432b34c.",
        "| ID | Requirement | Android status | Missing |",
        "|---|---|---|---|",
    ]
    partial_requirements = set(manifest["partialRequirementIds"])
    all_requirements = sorted(
        {requirement for gate in manifest["gates"] for requirement in gate["requirements"]}
    )
    for requirement in all_requirements:
        status = "PARTIAL" if requirement in partial_requirements else "VERIFIED"
        requirement_lines.append(f"| {requirement} | Fixture | {status} | External |")
    write(requirements_path, "\n".join(requirement_lines) + "\n")

    prerequisites = sorted(
        {item for gate in manifest["gates"] for item in gate["prerequisites"]},
        key=lambda value: int(value[1:]),
    )
    checkpoint = manifest["baseline"]["checkpoint"]
    backlog_lines = ["# Backlog"]
    backlog_lines.extend(f"- [x] {item} completed fixture" for item in prerequisites)
    backlog_lines.append(f"- [x] {checkpoint} current checkpoint fixture")
    for gate in manifest["gates"]:
        checked = "x" if gate["status"] == "VERIFIED" else " "
        backlog_lines.append(f"- [{checked}] {gate['id']} {gate['backlogTitle']}")
    write(backlog_path, "\n".join(backlog_lines) + "\n")
    write(readme_path, f"current implementation checkpoint: {checkpoint}\n")
    write(
        master_plan_path,
        f"**Status:** physical/release gates open\n\n### Current checkpoint — {checkpoint} (fixture)\n",
    )
    for gate in manifest["gates"]:
        for source in gate["evidenceSources"]:
            write(root / source)
    return {
        "manifest": manifest_path,
        "requirements": requirements_path,
        "backlog": backlog_path,
        "readme": readme_path,
        "master_plan": master_plan_path,
    }


def verify(root: Path, paths: dict[str, Path]):
    return MODULE.verify_release_gates(
        root,
        paths["manifest"],
        paths["requirements"],
        paths["backlog"],
        paths["readme"],
        paths["master_plan"],
    )


def edit_manifest(paths: dict[str, Path], mutate: Callable[[dict], None]) -> None:
    value = json.loads(paths["manifest"].read_text(encoding="utf-8"))
    mutate(value)
    write(paths["manifest"], canonical(value))


def rejected(label: str, mutate: Callable[[Path, dict[str, Path]], None]) -> None:
    with tempfile.TemporaryDirectory(prefix=f"release-gate-{label}-") as directory:
        root = Path(directory)
        paths = create_fixture(root)
        mutate(root, paths)
        try:
            verify(root, paths)
        except MODULE.ReleaseGateError:
            return
        raise AssertionError(f"Rejected fixture unexpectedly passed: {label}")


with tempfile.TemporaryDirectory(prefix="release-gate-valid-") as directory:
    root = Path(directory)
    paths = create_fixture(root)
    summary = verify(root, paths)
    assert summary.gates == 10
    assert summary.ready == 2
    assert summary.blocked == 7
    assert summary.verified == 1
    assert summary.partial_requirements == 5


rejected("schema", lambda _root, paths: edit_manifest(paths, lambda value: value.update(schemaVersion=2)))
rejected(
    "baseline",
    lambda _root, paths: edit_manifest(
        paths, lambda value: value["baseline"].update(mainCommit="deadbee")
    ),
)
rejected(
    "partial-inventory",
    lambda _root, paths: edit_manifest(paths, lambda value: value["partialRequirementIds"].pop()),
)
rejected(
    "gate-id",
    lambda _root, paths: edit_manifest(
        paths, lambda value: value["gates"][1].update(id="RG-001")
    ),
)
rejected(
    "status",
    lambda _root, paths: edit_manifest(
        paths, lambda value: value["gates"][0].update(status="COMPLETE")
    ),
)
rejected(
    "future-dependency",
    lambda _root, paths: edit_manifest(
        paths, lambda value: value["gates"][0].update(dependencies=["RG-010"])
    ),
)
rejected(
    "dependency-status",
    lambda _root, paths: edit_manifest(
        paths, lambda value: value["gates"][2].update(status="READY_FOR_OWNER_ACTION")
    ),
)
rejected(
    "authority",
    lambda _root, paths: edit_manifest(
        paths, lambda value: value["gates"][0].update(authorities=["unbounded_write"])
    ),
)
rejected(
    "prerequisite",
    lambda _root, paths: write(
        paths["backlog"],
        paths["backlog"].read_text(encoding="utf-8").replace("- [x] C75 ", "- [ ] C75 "),
    ),
)
rejected(
    "evidence",
    lambda root, _paths: (root / "docs/android/firebase-analytics.md").unlink(),
)
rejected(
    "backlog-title",
    lambda _root, paths: write(
        paths["backlog"],
        paths["backlog"]
        .read_text(encoding="utf-8")
        .replace("RG-001 Firebase Analytics Explore verification", "RG-001 Drifted title"),
    ),
)
rejected(
    "backlog-checkbox",
    lambda _root, paths: write(
        paths["backlog"],
        paths["backlog"]
        .read_text(encoding="utf-8")
        .replace("- [x] RG-001", "- [ ] RG-001"),
    ),
)
rejected(
    "checkpoint",
    lambda _root, paths: write(paths["readme"], "current implementation checkpoint: C98\n"),
)
rejected(
    "stale-sh-row",
    lambda _root, paths: write(
        paths["backlog"],
        paths["backlog"].read_text(encoding="utf-8")
        + "- [ ] Record physical-device evidence for `SH-007/SH-009`.\n",
    ),
)
rejected(
    "requirement-coverage",
    lambda _root, paths: edit_manifest(
        paths, lambda value: value["gates"][7].update(requirements=["XP-008"])
    ),
)
rejected(
    "production-control-plane-authority",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][1].update(
            authorities=[
                item
                for item in value["gates"][1]["authorities"]
                if item != "github_repository_admin"
            ]
        ),
    ),
)
rejected(
    "production-control-plane-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][1].update(
            prerequisites=[item for item in value["gates"][1]["prerequisites"] if item != "C120"]
        ),
    ),
)
rejected(
    "production-control-plane-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][1].update(
            evidenceSources=[
                item
                for item in value["gates"][1]["evidenceSources"]
                if item != "docs/android/evidence/c120-20260817/README.md"
            ]
        ),
    ),
)
rejected(
    "production-control-plane-condition",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][1].update(
            doneWhen=[
                item.replace("self-review prevention", "self-review allowed")
                for item in value["gates"][1]["doneWhen"]
            ]
        ),
    ),
)
rejected(
    "production-api-runtime-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][1].update(
            prerequisites=[item for item in value["gates"][1]["prerequisites"] if item != "C121"]
        ),
    ),
)
rejected(
    "production-api-runtime-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][1].update(
            evidenceSources=[
                item
                for item in value["gates"][1]["evidenceSources"]
                if item != "docs/android/evidence/c121-20260817/README.md"
            ]
        ),
    ),
)
rejected(
    "production-api-runtime-condition",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][1].update(
            doneWhen=[
                item.replace("zero action-runtime deprecation annotations", "runtime warnings ignored")
                for item in value["gates"][1]["doneWhen"]
            ]
        ),
    ),
)
rejected(
    "play-organization-ownership",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            doneWhen=[
                item.replace("Play Organization", "Play Personal")
                for item in value["gates"][4]["doneWhen"]
            ]
        ),
    ),
)

rejected(
    "play-organization-ledger-integration-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            prerequisites=[item for item in value["gates"][4]["prerequisites"] if item != "C111"]
        ),
    ),
)

rejected(
    "play-organization-store-ledger-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            evidenceSources=[
                item
                for item in value["gates"][4]["evidenceSources"]
                if item != "docs/android/evidence/c110-20260817/README.md"
            ]
        ),
    ),
)

rejected(
    "play-organization-generated-handoff-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            prerequisites=[item for item in value["gates"][4]["prerequisites"] if item != "C112"]
        ),
    ),
)

rejected(
    "play-organization-generated-handoff-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            evidenceSources=[
                item
                for item in value["gates"][4]["evidenceSources"]
                if item != "docs/android/evidence/c112-20260817/README.md"
            ]
        ),
    ),
)

rejected(
    "play-organization-retained-handoff-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            prerequisites=[item for item in value["gates"][4]["prerequisites"] if item != "C113"]
        ),
    ),
)

rejected(
    "play-organization-retained-handoff-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            evidenceSources=[
                item
                for item in value["gates"][4]["evidenceSources"]
                if item != "docs/android/evidence/c113-20260817/README.md"
            ]
        ),
    ),
)

rejected(
    "play-organization-upload-receipt-authority",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            authorities=[
                item
                for item in value["gates"][4]["authorities"]
                if item != "play_developer_api_write"
            ]
        ),
    ),
)

rejected(
    "play-organization-upload-receipt-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            prerequisites=[item for item in value["gates"][4]["prerequisites"] if item != "C114"]
        ),
    ),
)

rejected(
    "play-organization-upload-receipt-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            evidenceSources=[
                item
                for item in value["gates"][4]["evidenceSources"]
                if item != "docs/android/evidence/c114-20260817/README.md"
            ]
        ),
    ),
)

rejected(
    "play-organization-upload-receipt-condition",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            doneWhen=[
                item.replace("Play Developer API Bundle response", "local bundle report")
                for item in value["gates"][4]["doneWhen"]
            ]
        ),
    ),
)
rejected(
    "play-organization-generated-apk-read-authority",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            authorities=[
                item
                for item in value["gates"][4]["authorities"]
                if item != "play_developer_api_read"
            ]
        ),
    ),
)
rejected(
    "play-organization-generated-apk-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            prerequisites=[item for item in value["gates"][4]["prerequisites"] if item != "C117"]
        ),
    ),
)
rejected(
    "play-organization-generated-apk-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            evidenceSources=[
                item
                for item in value["gates"][4]["evidenceSources"]
                if item != "docs/android/evidence/c117-20260817/README.md"
            ]
        ),
    ),
)
rejected(
    "play-organization-generated-apk-condition",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            doneWhen=[
                item.replace("App Links app-signing certificate set", "unverified signing key")
                for item in value["gates"][4]["doneWhen"]
            ]
        ),
    ),
)
rejected(
    "play-organization-downloaded-apk-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            prerequisites=[item for item in value["gates"][4]["prerequisites"] if item != "C118"]
        ),
    ),
)
rejected(
    "play-organization-downloaded-apk-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            evidenceSources=[
                item
                for item in value["gates"][4]["evidenceSources"]
                if item != "docs/android/evidence/c118-20260817/README.md"
            ]
        ),
    ),
)
rejected(
    "play-organization-downloaded-apk-condition",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][4].update(
            doneWhen=[
                item.replace("package/version/signature bytes", "metadata only")
                for item in value["gates"][4]["doneWhen"]
            ]
        ),
    ),
)
rejected(
    "play-internal-track-api-authority",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            authorities=[
                item
                for item in value["gates"][5]["authorities"]
                if item != "play_developer_api_read"
            ]
        ),
    ),
)
rejected(
    "play-internal-track-inspection-edit-authority",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            authorities=[
                item
                for item in value["gates"][5]["authorities"]
                if item != "play_developer_api_write"
            ]
        ),
    ),
)
rejected(
    "play-internal-track-receipt-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            prerequisites=[item for item in value["gates"][5]["prerequisites"] if item != "C116"]
        ),
    ),
)
rejected(
    "play-internal-track-receipt-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            evidenceSources=[
                item
                for item in value["gates"][5]["evidenceSources"]
                if item != "docs/android/evidence/c116-20260817/README.md"
            ]
        ),
    ),
)
rejected(
    "play-internal-track-receipt-condition",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            doneWhen=[
                item.replace("RELEASE_LIFECYCLE_STATE_PUBLISHED", "RELEASE_LIFECYCLE_STATE_DRAFT")
                for item in value["gates"][5]["doneWhen"]
            ]
        ),
    ),
)
rejected(
    "play-internal-track-completed-condition",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            doneWhen=[
                item.replace("completed status", "halted status")
                for item in value["gates"][5]["doneWhen"]
            ]
        ),
    ),
)
rejected(
    "play-internal-generated-apk-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            prerequisites=[item for item in value["gates"][5]["prerequisites"] if item != "C117"]
        ),
    ),
)
rejected(
    "play-internal-generated-apk-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            evidenceSources=[
                item
                for item in value["gates"][5]["evidenceSources"]
                if item != "docs/android/evidence/c117-20260817/README.md"
            ]
        ),
    ),
)
rejected(
    "play-internal-generated-apk-condition",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            doneWhen=[
                item.replace("base-master split metadata", "local synthetic splits")
                for item in value["gates"][5]["doneWhen"]
            ]
        ),
    ),
)
rejected(
    "play-internal-downloaded-apk-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            prerequisites=[item for item in value["gates"][5]["prerequisites"] if item != "C118"]
        ),
    ),
)
rejected(
    "play-internal-downloaded-apk-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            evidenceSources=[
                item
                for item in value["gates"][5]["evidenceSources"]
                if item != "docs/android/evidence/c118-20260817/README.md"
            ]
        ),
    ),
)
rejected(
    "play-internal-downloaded-apk-condition",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            doneWhen=[
                item.replace("embedded-signature verification", "metadata-only verification")
                for item in value["gates"][5]["doneWhen"]
            ]
        ),
    ),
)
rejected(
    "play-internal-installed-package-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            prerequisites=[item for item in value["gates"][5]["prerequisites"] if item != "C119"]
        ),
    ),
)
rejected(
    "play-internal-installed-package-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            evidenceSources=[
                item
                for item in value["gates"][5]["evidenceSources"]
                if item != "docs/android/evidence/c119-20260817/README.md"
            ]
        ),
    ),
)
rejected(
    "play-internal-installed-package-condition",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][5].update(
            doneWhen=[
                item.replace("C118 base.apk byte match", "unbound installed metadata")
                for item in value["gates"][5]["doneWhen"]
            ]
        ),
    ),
)
rejected(
    "play-policy-readiness-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][8].update(
            prerequisites=[item for item in value["gates"][8]["prerequisites"] if item != "C106"]
        ),
    ),
)

rejected(
    "talkback-physical-rerun-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][7].update(
            prerequisites=[
                item for item in value["gates"][7]["prerequisites"] if item != "C109"
            ]
        ),
    ),
)
rejected(
    "play-review-access-contract",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][9].update(
            doneWhen=[
                item.replace("reusable", "temporary") for item in value["gates"][9]["doneWhen"]
            ]
        ),
    ),
)

rejected(
    "play-listing-readiness-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][8].update(
            prerequisites=[item for item in value["gates"][8]["prerequisites"] if item != "C107"]
        ),
    ),
)

rejected(
    "play-listing-preview-contract",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][8].update(
            doneWhen=[
                item.replace("Play preview", "local preview")
                for item in value["gates"][8]["doneWhen"]
            ]
        ),
    ),
)

rejected(
    "play-renderer-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][8].update(
            prerequisites=[item for item in value["gates"][8]["prerequisites"] if item != "C108"]
        ),
    ),
)

rejected(
    "play-renderer-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][9].update(
            evidenceSources=[
                item
                for item in value["gates"][9]["evidenceSources"]
                if item != "docs/android/evidence/c108-20260817/README.md"
            ]
        ),
    ),
)

rejected(
    "play-store-ledger-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][8].update(
            prerequisites=[item for item in value["gates"][8]["prerequisites"] if item != "C110"]
        ),
    ),
)

rejected(
    "closed-test-store-ledger-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][9].update(
            evidenceSources=[
                item
                for item in value["gates"][9]["evidenceSources"]
                if item != "docs/android/evidence/c110-20260817/README.md"
            ]
        ),
    ),
)

rejected(
    "closed-test-ci-runtime-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][9].update(
            prerequisites=[item for item in value["gates"][9]["prerequisites"] if item != "C115"]
        ),
    ),
)

rejected(
    "closed-test-ci-runtime-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][9].update(
            evidenceSources=[
                item
                for item in value["gates"][9]["evidenceSources"]
                if item != "docs/android/evidence/c115-20260817/README.md"
            ]
        ),
    ),
)

rejected(
    "closed-test-ci-runtime-condition",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][9].update(
            doneWhen=[
                item.replace("Node 24 Android CI runtime contract", "legacy Android CI runtime")
                for item in value["gates"][9]["doneWhen"]
            ]
        ),
    ),
)

rejected(
    "closed-test-api-runtime-prerequisite",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][9].update(
            prerequisites=[item for item in value["gates"][9]["prerequisites"] if item != "C121"]
        ),
    ),
)

rejected(
    "closed-test-api-runtime-evidence",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][9].update(
            evidenceSources=[
                item
                for item in value["gates"][9]["evidenceSources"]
                if item != "docs/android/evidence/c121-20260817/README.md"
            ]
        ),
    ),
)

rejected(
    "closed-test-api-runtime-condition",
    lambda _root, paths: edit_manifest(
        paths,
        lambda value: value["gates"][9].update(
            doneWhen=[
                item.replace("zero action-runtime deprecation annotations", "warning annotations accepted")
                for item in value["gates"][9]["doneWhen"]
            ]
        ),
    ),
)

print("Release gate contract passed: accepted=1 rejected=70")
