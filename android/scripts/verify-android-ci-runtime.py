#!/usr/bin/env python3
"""Fail closed if Android CI drifts from the reviewed Node 24 action runtime."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys


MAX_WORKFLOW_BYTES = 64 * 1024
EXPECTED_ACTIONS = (
    "- uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6.1.0",
    "- uses: actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961 # v5.7.0",
    "- uses: gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb # v6.3.0",
)


class WorkflowError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise WorkflowError(message)


def verify_android_ci_runtime(workflow: Path) -> None:
    require(not workflow.is_symlink() and workflow.is_file(), "Android CI workflow is missing or unsafe")
    try:
        workflow_bytes = workflow.read_bytes()
    except OSError as error:
        raise WorkflowError("Android CI workflow could not be read") from error
    require(0 < len(workflow_bytes) <= MAX_WORKFLOW_BYTES, "Android CI workflow size is invalid")
    require(b"\r" not in workflow_bytes and b"\t" not in workflow_bytes,
            "Android CI workflow must use canonical LF/space formatting")
    require(workflow_bytes.endswith(b"\n"), "Android CI workflow must end with a newline")
    try:
        text = workflow_bytes.decode("utf-8")
    except UnicodeDecodeError as error:
        raise WorkflowError("Android CI workflow is not UTF-8") from error

    action_lines = tuple(
        line.strip() for line in text.splitlines() if re.fullmatch(r"\s*- uses: .+", line)
    )
    require(action_lines == EXPECTED_ACTIONS,
            "Android CI action inventory, order, release or immutable SHA drifted")
    for line in action_lines:
        require(
            re.search(r"@[0-9a-f]{40} # v[0-9]+\.[0-9]+\.[0-9]+$", line) is not None,
            "Android CI action is not pinned to a documented full commit SHA",
        )

    require(text.count("\npermissions:\n  contents: read\n\ndefaults:\n") == 1,
            "Android CI must grant only top-level contents: read")
    require(len(re.findall(r"(?m)^\s*permissions:\s*$", text)) == 1,
            "Android CI contains an extra permission scope")
    require("contents: write" not in text and "pull-requests: write" not in text,
            "Android CI contains an unreviewed write permission")
    require(text.count("    runs-on: ubuntu-latest\n") == 1,
            "Android CI hosted runner identity drifted")
    require("pull_request_target:" not in text,
            "Android CI must not execute privileged pull_request_target code")
    require("  push:\n    branches: [android-dev]\n" in text,
            "Android CI push branch coverage drifted")
    require("  pull_request:\n    branches: [main, staging]\n" in text,
            "Android CI pull-request branch coverage drifted")

    checkout_block = (
        f"      {EXPECTED_ACTIONS[0]}\n"
        "        with:\n"
        "          fetch-depth: 0\n"
        "          persist-credentials: false\n"
    )
    require(text.count(checkout_block) == 1,
            "Android CI checkout must keep full ancestry without persisted credentials")
    java_block = (
        f"      {EXPECTED_ACTIONS[1]}\n"
        "        with:\n"
        "          distribution: temurin\n"
        "          java-version: 17\n"
    )
    require(text.count(java_block) == 1,
            "Android CI Java runtime must remain Temurin 17")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify the pinned Android CI action runtime.")
    parser.add_argument("--workflow", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        verify_android_ci_runtime(args.workflow)
    except (WorkflowError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 1
    print("Android CI runtime verification passed: actions=3 node24=3 permissions=contents:read")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
