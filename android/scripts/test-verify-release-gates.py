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
    for requirement in manifest["partialRequirementIds"]:
        requirement_lines.append(f"| {requirement} | Fixture | PARTIAL | External |")
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
    assert summary.ready == 3
    assert summary.blocked == 7
    assert summary.verified == 0
    assert summary.partial_requirements == 6


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
        .replace("- [ ] RG-001", "- [x] RG-001"),
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
        paths, lambda value: value["gates"][0].update(requirements=["XP-005"])
    ),
)

print("Release gate contract passed: accepted=1 rejected=15")
