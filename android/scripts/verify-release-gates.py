#!/usr/bin/env python3
"""Fail-closed validation for the machine-readable Android residual release gates."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any, Sequence


class ReleaseGateError(RuntimeError):
    pass


ALLOWED_ROOT_FIELDS = {"schemaVersion", "baseline", "partialRequirementIds", "gates"}
ALLOWED_BASELINE_FIELDS = {"iosRelease", "mainCommit", "androidBranch", "checkpoint"}
ALLOWED_GATE_FIELDS = {
    "id",
    "title",
    "status",
    "requirements",
    "dependencies",
    "authorities",
    "owner",
    "prerequisites",
    "doneWhen",
    "evidenceSources",
    "backlogTitle",
}
ALLOWED_STATUSES = {"READY_FOR_OWNER_ACTION", "BLOCKED_BY_DEPENDENCIES", "VERIFIED"}
ALLOWED_AUTHORITIES = {
    "branch_merge",
    "firebase_console_write",
    "firebase_delivery",
    "manual_accessibility",
    "physical_device",
    "play_console_read",
    "play_console_write",
    "production_database_configuration",
    "production_database_read",
    "production_deploy",
    "production_test_data_write",
    "release_key_access",
}
ALLOWED_OWNERS = {"analytics_owner", "qa_owner", "release_owner"}
REQUIREMENT_ID = re.compile(r"^XP-\d{3}$")
GATE_ID = re.compile(r"^RG-\d{3}$")
CHECKPOINT_ID = re.compile(r"^C\d+$")
MAIN_COMMIT = re.compile(r"^[0-9a-f]{7,40}$")
REQUIREMENT_ROW = re.compile(r"^\| (XP-\d{3}) \|.*\| (IMPLEMENTED|PARTIAL|VERIFIED) \|")
BACKLOG_GATE = re.compile(r"^- \[([ x])\] (RG-\d{3}) (.+)$")
COMPLETED_CHECKPOINT = re.compile(r"^- \[x\] (C\d+)\b")


@dataclass(frozen=True)
class ReleaseGateSummary:
    gates: int
    ready: int
    blocked: int
    verified: int
    partial_requirements: int


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ReleaseGateError(message)


def _exact_fields(value: dict[str, Any], expected: set[str], label: str) -> None:
    actual = set(value)
    _require(actual == expected, f"{label} fields drifted: {sorted(actual ^ expected)}")


def _string_list(value: Any, label: str, *, minimum: int = 1) -> list[str]:
    _require(isinstance(value, list), f"{label} must be a list")
    _require(len(value) >= minimum, f"{label} must contain at least {minimum} item(s)")
    _require(all(isinstance(item, str) and item.strip() == item and item for item in value),
             f"{label} must contain nonblank trimmed strings")
    _require(len(value) == len(set(value)), f"{label} contains duplicates")
    return value


def _load_json(path: Path) -> dict[str, Any]:
    try:
        raw = path.read_text(encoding="utf-8")
        value = json.loads(raw)
    except (OSError, json.JSONDecodeError) as error:
        raise ReleaseGateError(f"Cannot read release gate manifest: {error}") from error
    _require(isinstance(value, dict), "Release gate manifest root must be an object")
    canonical = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    _require(raw == canonical, "Release gate manifest must use canonical two-space JSON formatting")
    return value


def _requirement_statuses(path: Path) -> dict[str, str]:
    statuses: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = REQUIREMENT_ROW.match(line)
        if match:
            requirement, status = match.groups()
            _require(requirement not in statuses, f"Duplicate requirement row: {requirement}")
            statuses[requirement] = status
    _require(statuses, "No cross-platform requirement rows were found")
    return statuses


def _backlog_state(path: Path) -> tuple[dict[str, tuple[bool, str]], set[str], str]:
    text = path.read_text(encoding="utf-8")
    gates: dict[str, tuple[bool, str]] = {}
    checkpoints: set[str] = set()
    for line in text.splitlines():
        gate_match = BACKLOG_GATE.match(line)
        if gate_match:
            checked, gate_id, title = gate_match.groups()
            _require(gate_id not in gates, f"Duplicate release gate backlog row: {gate_id}")
            gates[gate_id] = (checked == "x", title)
        checkpoint_match = COMPLETED_CHECKPOINT.match(line)
        if checkpoint_match:
            checkpoints.add(checkpoint_match.group(1))
    _require(
        "- [ ] Record physical-device evidence for `SH-007/SH-009`." not in text,
        "Stale SH-007/SH-009 backlog row must be reconciled with C67/C88 evidence",
    )
    return gates, checkpoints, text


def _evidence_path(repository_root: Path, source: str) -> Path:
    pure = PurePosixPath(source)
    _require(not pure.is_absolute() and ".." not in pure.parts, f"Unsafe evidence path: {source}")
    _require(pure.parts[:2] == ("docs", "android"), f"Evidence must stay under docs/android: {source}")
    path = repository_root.joinpath(*pure.parts)
    _require(path.is_file(), f"Evidence source does not exist: {source}")
    return path


def verify_release_gates(
    repository_root: Path,
    manifest_path: Path,
    requirements_path: Path,
    backlog_path: Path,
    readme_path: Path,
    master_plan_path: Path,
) -> ReleaseGateSummary:
    root = repository_root.resolve()
    manifest = _load_json(manifest_path)
    _exact_fields(manifest, ALLOWED_ROOT_FIELDS, "manifest")
    _require(manifest["schemaVersion"] == 1, "Unsupported release gate schema version")

    baseline = manifest["baseline"]
    _require(isinstance(baseline, dict), "baseline must be an object")
    _exact_fields(baseline, ALLOWED_BASELINE_FIELDS, "baseline")
    _require(baseline["iosRelease"] == "1.0.6 Build 51", "Pinned iOS release drifted")
    _require(MAIN_COMMIT.fullmatch(baseline["mainCommit"]) is not None, "Invalid main commit")
    _require(baseline["androidBranch"] == "android-dev", "Android branch must remain android-dev")
    _require(CHECKPOINT_ID.fullmatch(baseline["checkpoint"]) is not None, "Invalid checkpoint")

    requirement_statuses = _requirement_statuses(requirements_path)
    requirements_text = requirements_path.read_text(encoding="utf-8")
    _require(
        f"main@{baseline['mainCommit']}" in requirements_text,
        "Manifest main commit differs from parity requirements",
    )
    actual_partial = {key for key, value in requirement_statuses.items() if value == "PARTIAL"}
    declared_partial = set(_string_list(manifest["partialRequirementIds"], "partialRequirementIds"))
    _require(declared_partial == actual_partial, "Partial requirement inventory drifted")

    readme = readme_path.read_text(encoding="utf-8")
    master_plan = master_plan_path.read_text(encoding="utf-8")
    checkpoint = baseline["checkpoint"]
    _require(
        f"current implementation checkpoint: {checkpoint}" in readme,
        "README checkpoint differs from manifest",
    )
    _require(
        f"### Current checkpoint — {checkpoint} " in master_plan,
        "Master-plan checkpoint differs from manifest",
    )
    _require("physical/release gates open" in master_plan, "Master plan must keep release gates open")

    backlog_gates, completed_checkpoints, _ = _backlog_state(backlog_path)
    gates = manifest["gates"]
    _require(isinstance(gates, list) and gates, "gates must be a nonempty list")
    expected_ids = [f"RG-{index:03d}" for index in range(1, len(gates) + 1)]
    actual_ids = [gate.get("id") if isinstance(gate, dict) else None for gate in gates]
    _require(actual_ids == expected_ids, "Release gate IDs/order must be contiguous and stable")
    _require(set(backlog_gates) == set(expected_ids), "Backlog release gate inventory drifted")

    statuses: dict[str, str] = {}
    unresolved_requirements: set[str] = set()
    for index, gate in enumerate(gates):
        _require(isinstance(gate, dict), f"Gate {index + 1} must be an object")
        gate_id = expected_ids[index]
        _exact_fields(gate, ALLOWED_GATE_FIELDS, gate_id)
        _require(GATE_ID.fullmatch(gate["id"]) is not None, f"Invalid gate ID: {gate_id}")
        _require(isinstance(gate["title"], str) and gate["title"].strip() == gate["title"],
                 f"{gate_id} title is invalid")
        _require(gate["backlogTitle"] == gate["title"], f"{gate_id} backlog title drifted")

        requirements = _string_list(gate["requirements"], f"{gate_id}.requirements")
        _require(all(REQUIREMENT_ID.fullmatch(item) for item in requirements),
                 f"{gate_id} has an invalid requirement ID")
        _require(all(item in requirement_statuses for item in requirements),
                 f"{gate_id} references a missing requirement")

        dependencies = _string_list(gate["dependencies"], f"{gate_id}.dependencies", minimum=0)
        prior_ids = set(expected_ids[:index])
        _require(set(dependencies) <= prior_ids, f"{gate_id} dependencies must reference earlier gates")

        status = gate["status"]
        _require(status in ALLOWED_STATUSES, f"{gate_id} has an invalid status")
        dependency_statuses = [statuses[dependency] for dependency in dependencies]
        if status == "VERIFIED":
            _require(all(value == "VERIFIED" for value in dependency_statuses),
                     f"{gate_id} cannot be verified before its dependencies")
        else:
            expected_status = (
                "READY_FOR_OWNER_ACTION"
                if all(value == "VERIFIED" for value in dependency_statuses)
                else "BLOCKED_BY_DEPENDENCIES"
            )
            _require(status == expected_status, f"{gate_id} status does not match dependency state")
            unresolved_requirements.update(requirements)
        statuses[gate_id] = status

        authorities = _string_list(gate["authorities"], f"{gate_id}.authorities")
        _require(set(authorities) <= ALLOWED_AUTHORITIES, f"{gate_id} has an unknown authority")
        _require(gate["owner"] in ALLOWED_OWNERS, f"{gate_id} has an unknown owner")

        prerequisites = _string_list(gate["prerequisites"], f"{gate_id}.prerequisites")
        _require(all(CHECKPOINT_ID.fullmatch(item) for item in prerequisites),
                 f"{gate_id} has an invalid prerequisite")
        missing_prerequisites = set(prerequisites) - completed_checkpoints
        _require(not missing_prerequisites,
                 f"{gate_id} prerequisites are not completed: {sorted(missing_prerequisites)}")

        _string_list(gate["doneWhen"], f"{gate_id}.doneWhen", minimum=2)
        sources = _string_list(gate["evidenceSources"], f"{gate_id}.evidenceSources")
        for source in sources:
            _evidence_path(root, source)

        backlog_checked, backlog_title = backlog_gates[gate_id]
        _require(backlog_title == gate["backlogTitle"], f"{gate_id} backlog text differs")
        _require(backlog_checked == (status == "VERIFIED"), f"{gate_id} backlog checkbox differs")

    _require(unresolved_requirements == declared_partial,
             "Unresolved release gates do not exactly cover PARTIAL requirements")
    _require(checkpoint in completed_checkpoints, "Current checkpoint is not completed in backlog")

    return ReleaseGateSummary(
        gates=len(gates),
        ready=sum(value == "READY_FOR_OWNER_ACTION" for value in statuses.values()),
        blocked=sum(value == "BLOCKED_BY_DEPENDENCIES" for value in statuses.values()),
        verified=sum(value == "VERIFIED" for value in statuses.values()),
        partial_requirements=len(declared_partial),
    )


def _arguments(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify Android residual release gate inventory.")
    parser.add_argument("--repository-root", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--requirements", type=Path, required=True)
    parser.add_argument("--backlog", type=Path, required=True)
    parser.add_argument("--readme", type=Path, required=True)
    parser.add_argument("--master-plan", type=Path, required=True)
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _arguments(argv or sys.argv[1:])
    try:
        summary = verify_release_gates(
            arguments.repository_root,
            arguments.manifest,
            arguments.requirements,
            arguments.backlog,
            arguments.readme,
            arguments.master_plan,
        )
    except (OSError, ReleaseGateError) as error:
        print(f"Release gate verification failed: {error}", file=sys.stderr)
        return 1
    print(
        "Release gate verification passed: "
        f"gates={summary.gates} ready={summary.ready} blocked={summary.blocked} "
        f"verified={summary.verified} partialRequirements={summary.partial_requirements}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
