#!/usr/bin/env python3
"""Accepted/rejected fixtures for the Android CI runtime policy."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import tempfile


SCRIPT = Path(__file__).with_name("verify-android-ci-runtime.py")
REPOSITORY_ROOT = SCRIPT.parents[2]
SOURCE_WORKFLOW = REPOSITORY_ROOT / ".github/workflows/android-ci.yml"
SPEC = importlib.util.spec_from_file_location("verify_android_ci_runtime", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load Android CI runtime verifier")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def expect_rejected(label: str, content: str) -> None:
    with tempfile.TemporaryDirectory(prefix=f"android-ci-runtime-{label}-") as directory:
        workflow = Path(directory) / "android-ci.yml"
        write(workflow, content)
        try:
            MODULE.verify_android_ci_runtime(workflow)
        except MODULE.WorkflowError:
            return
        raise AssertionError(f"Rejected fixture unexpectedly passed: {label}")


def replace_once(source: str, old: str, new: str) -> str:
    if source.count(old) != 1:
        raise AssertionError(f"Fixture source is not unique: {old}")
    return source.replace(old, new, 1)


def main() -> None:
    source = SOURCE_WORKFLOW.read_text(encoding="utf-8")
    MODULE.verify_android_ci_runtime(SOURCE_WORKFLOW)

    with tempfile.TemporaryDirectory(prefix="android-ci-runtime-symlink-") as directory:
        root = Path(directory)
        target = root / "target.yml"
        link = root / "android-ci.yml"
        write(target, source)
        link.symlink_to(target)
        try:
            MODULE.verify_android_ci_runtime(link)
        except MODULE.WorkflowError:
            pass
        else:
            raise AssertionError("Symlink workflow unexpectedly passed")

    expect_rejected("empty", "")
    expect_rejected("crlf", source.replace("\n", "\r\n"))
    expect_rejected("tab", source.replace("  contents: read", "\tcontents: read"))
    expect_rejected(
        "checkout-sha",
        replace_once(source, "d23441a48e516b6c34aea4fa41551a30e30af803", "0" * 40),
    )
    expect_rejected(
        "checkout-tag",
        replace_once(
            source,
            "actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6.1.0",
            "actions/checkout@v6",
        ),
    )
    expect_rejected("checkout-version-comment", replace_once(source, "# v6.1.0", "# v6.0.0"))
    expect_rejected(
        "java-sha",
        replace_once(source, "b6effb05e454b25005698d916606bdc6ffcbf961", "1" * 40),
    )
    expect_rejected(
        "gradle-sha",
        replace_once(source, "9c971963bec38e04b3d30dcc455b5382be2fdbfb", "2" * 40),
    )
    expect_rejected(
        "action-order",
        replace_once(
            replace_once(source, MODULE.EXPECTED_ACTIONS[0], "ACTION_PLACEHOLDER"),
            MODULE.EXPECTED_ACTIONS[1],
            MODULE.EXPECTED_ACTIONS[0],
        ).replace("ACTION_PLACEHOLDER", MODULE.EXPECTED_ACTIONS[1], 1),
    )
    expect_rejected(
        "extra-action",
        replace_once(
            source,
            f"      {MODULE.EXPECTED_ACTIONS[2]}\n",
            f"      {MODULE.EXPECTED_ACTIONS[2]}\n      - uses: example/unknown@{'3' * 40} # v1.0.0\n",
        ),
    )
    expect_rejected("permissions-missing", replace_once(source, "permissions:\n  contents: read\n\n", ""))
    expect_rejected("permissions-write", replace_once(source, "contents: read", "contents: write"))
    expect_rejected(
        "permissions-extra",
        replace_once(source, "  contents: read\n\n", "  contents: read\n  issues: write\n\n"),
    )
    expect_rejected("runner", replace_once(source, "runs-on: ubuntu-latest", "runs-on: macos-latest"))
    expect_rejected("fetch-depth", replace_once(source, "fetch-depth: 0", "fetch-depth: 1"))
    expect_rejected(
        "persist-credentials",
        replace_once(source, "persist-credentials: false", "persist-credentials: true"),
    )
    expect_rejected("java-distribution", replace_once(source, "distribution: temurin", "distribution: zulu"))
    expect_rejected("java-version", replace_once(source, "java-version: 17", "java-version: 21"))
    expect_rejected(
        "privileged-trigger",
        replace_once(source, "  pull_request:\n", "  pull_request_target:\n"),
    )
    expect_rejected(
        "push-branch",
        replace_once(source, "branches: [android-dev]", "branches: [main]"),
    )
    expect_rejected(
        "pull-request-branches",
        replace_once(source, "branches: [main, staging]", "branches: [main]"),
    )
    expect_rejected("oversized", source + "#" * MODULE.MAX_WORKFLOW_BYTES)

    print("Android CI runtime contract passed (1 accepted, 23 rejected).")


if __name__ == "__main__":
    main()
