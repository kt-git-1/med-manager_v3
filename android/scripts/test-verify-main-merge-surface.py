#!/usr/bin/env python3
"""Repository fixtures for verify-main-merge-surface.py."""

from __future__ import annotations

import importlib.util
import subprocess
import sys
import tempfile
from pathlib import Path


SCRIPT = Path(__file__).with_name("verify-main-merge-surface.py")
SPEC = importlib.util.spec_from_file_location("verify_main_merge_surface", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def git(root: Path, *arguments: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), *arguments],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def write(root: Path, path: str, content: str = "fixture\n") -> None:
    target = root / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def commit_all(root: Path, message: str) -> str:
    git(root, "add", "-A")
    git(root, "commit", "-m", message)
    return git(root, "rev-parse", "HEAD")


def create_repository(root: Path) -> tuple[str, str]:
    git(root, "init", "-b", "main")
    git(root, "config", "user.name", "C96 Fixture")
    git(root, "config", "user.email", "fixture@example.invalid")
    write(root, ".gitignore", "docs/\ntmp/\noutput/")
    write(root, "README.md")
    base = commit_all(root, "base")
    git(root, "update-ref", "refs/remotes/origin/main", base)
    git(root, "checkout", "-b", "android-dev")

    write(
        root,
        ".gitignore",
        "\n".join(
            [
                "docs/",
                "!docs/",
                "docs/*",
                "!docs/android/",
                "!docs/android/**",
                "tmp/",
                "output/",
                "",
                "# Android",
                "android/.gradle/",
                "android/.kotlin/",
                "android/local.properties",
                "android/**/build/",
                "*.jks",
                "*.keystore",
                "",
            ]
        ),
    )
    for path in MODULE.EXPECTED_WORKFLOW_PATHS:
        write(root, path)
    for path in MODULE.REQUIRED_ANDROID_PATHS:
        write(root, path)
    for path in MODULE.REQUIRED_AUTHORITY_DOCS:
        write(root, path)
    for path in MODULE.EXPECTED_API_PATHS:
        write(root, path)
    head = commit_all(root, "android surface")
    return base, head


def rejected(label: str, mutation) -> None:
    with tempfile.TemporaryDirectory(prefix="merge-surface-reject-") as directory:
        root = Path(directory)
        create_repository(root)
        mutation(root)
        commit_all(root, label)
        try:
            MODULE.verify_merge_surface(root, "origin/main", "HEAD")
        except MODULE.MergeSurfaceError:
            return
        raise AssertionError(f"Rejected fixture unexpectedly passed: {label}")


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="merge-surface-accept-") as directory:
        root = Path(directory)
        base, head = create_repository(root)
        summary = MODULE.verify_merge_surface(root, "origin/main", "HEAD")
        assert summary.base_sha == base
        assert summary.head_sha == head
        assert summary.scopes["api"] == len(MODULE.EXPECTED_API_PATHS)
        assert summary.scopes["android"] == len(MODULE.REQUIRED_ANDROID_PATHS)

    cases = [
        ("iOS drift", lambda root: write(root, "ios/MedicationApp/Unexpected.swift")),
        ("unreviewed API", lambda root: write(root, "api/app/api/unreviewed/route.ts")),
        ("unreviewed workflow", lambda root: write(root, ".github/workflows/ios-ci.yml")),
        ("private signing material", lambda root: write(root, "android/upload.p8")),
        ("generated IDE output", lambda root: write(root, "android/.idea/output.txt")),
        ("oversized evidence", lambda root: write(root, "docs/android/evidence/large.png", "x" * (MODULE.MAX_SINGLE_BLOB_BYTES + 1))),
        ("deleted main file", lambda root: (root / "README.md").unlink()),
        ("unexpected root file", lambda root: write(root, "release-note.txt")),
        ("unreviewed ignore override", lambda root: write(root, ".gitignore", (root / ".gitignore").read_text(encoding="utf-8") + "/docs\n")),
    ]
    for label, mutation in cases:
        rejected(label, mutation)

    with tempfile.TemporaryDirectory(prefix="merge-surface-diverged-") as directory:
        root = Path(directory)
        create_repository(root)
        git(root, "checkout", "--orphan", "new-main")
        for child in root.iterdir():
            if child.name != ".git":
                if child.is_dir():
                    subprocess.run(["find", str(child), "-depth", "-delete"], check=True)
                else:
                    child.unlink()
        write(root, "README.md", "diverged\n")
        divergent = commit_all(root, "diverged main")
        git(root, "update-ref", "refs/remotes/origin/main", divergent)
        git(root, "checkout", "android-dev")
        try:
            MODULE.verify_merge_surface(root, "origin/main", "HEAD")
        except MODULE.MergeSurfaceError:
            pass
        else:
            raise AssertionError("Diverged main unexpectedly passed")

    print(f"Main merge surface contract passed: accepted=1 rejected={len(cases) + 1}")


if __name__ == "__main__":
    main()
