#!/usr/bin/env python3
"""Synthetic accepted/rejected fixtures for verify-play-store-listing.py."""

from __future__ import annotations

import importlib.util
import sys
import tempfile
from pathlib import Path
from typing import Callable


SCRIPT = Path(__file__).with_name("verify-play-store-listing.py")
REPOSITORY_ROOT = SCRIPT.parents[2]
SPEC = importlib.util.spec_from_file_location("verify_play_store_listing", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load Play store listing verifier")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def create_fixture(root: Path) -> dict[str, Path]:
    paths = {
        "listing": root / "docs/android/play-store-listing-ja.md",
        "source_map": root / "docs/android/play-store-assets/phone-ja-JP/sources.tsv",
        "manifest": root / "android/app/src/main/AndroidManifest.xml",
        "strings": root / "android/app/src/main/res/values/strings.xml",
        "build_file": root / "android/app/build.gradle.kts",
    }
    for key, path in paths.items():
        source = {
            "listing": REPOSITORY_ROOT / "docs/android/play-store-listing-ja.md",
            "source_map": REPOSITORY_ROOT / "docs/android/play-store-assets/phone-ja-JP/sources.tsv",
            "manifest": REPOSITORY_ROOT / "android/app/src/main/AndroidManifest.xml",
            "strings": REPOSITORY_ROOT / "android/app/src/main/res/values/strings.xml",
            "build_file": REPOSITORY_ROOT / "android/app/build.gradle.kts",
        }[key]
        write(path, source.read_text(encoding="utf-8"))
    for line in paths["source_map"].read_text(encoding="utf-8").splitlines():
        _output, source = line.split("\t")
        evidence = root / source
        evidence.parent.mkdir(parents=True, exist_ok=True)
        evidence.touch()
    return paths


def verify(root: Path, paths: dict[str, Path]):
    return MODULE.verify_play_store_listing(
        root,
        paths["listing"],
        paths["source_map"],
        paths["manifest"],
        paths["strings"],
        paths["build_file"],
    )


def replace(paths: dict[str, Path], key: str, old: str, new: str) -> None:
    path = paths[key]
    content = path.read_text(encoding="utf-8")
    if old not in content:
        raise AssertionError(f"Fixture marker missing for {key}: {old}")
    write(path, content.replace(old, new, 1))


def rejected(label: str, mutate: Callable[[Path, dict[str, Path]], None]) -> None:
    with tempfile.TemporaryDirectory(prefix=f"play-listing-{label}-") as directory:
        root = Path(directory)
        paths = create_fixture(root)
        mutate(root, paths)
        try:
            verify(root, paths)
        except MODULE.PlayStoreListingError:
            return
        raise AssertionError(f"Rejected fixture unexpectedly passed: {label}")


with tempfile.TemporaryDirectory(prefix="play-listing-valid-") as directory:
    root = Path(directory)
    paths = create_fixture(root)
    summary = verify(root, paths)
    assert summary.text_blocks == 4
    assert summary.screenshots == 8
    assert summary.source_mappings == 8
    assert summary.public_urls == 5


rejected("locale", lambda _root, paths: replace(paths, "listing", "`ja-JP`", "`en-US`"))
rejected("package", lambda _root, paths: replace(paths, "listing", MODULE.PACKAGE, "example.invalid"))
rejected("app-name", lambda _root, paths: replace(paths, "listing", "お薬見守り\n```", "別の名前\n```"))
rejected("text-limit", lambda _root, paths: replace(paths, "listing", "お薬見守り\n```", "薬" * 31 + "\n```"))
rejected("analytics-consent", lambda _root, paths: replace(paths, "listing", "同意した場合のみ有効", "常に有効"))
rejected("medical-data", lambda _root, paths: replace(paths, "listing", "医療・個人情報は含めません", "情報を送信します"))
rejected("medical-disclaimer", lambda _root, paths: replace(paths, "listing", "医療機器ではなく", "医療機器であり"))
rejected("notification-caveat", lambda _root, paths: replace(paths, "listing", "通知は端末の設定や利用状況により届かない場合があります", "通知は必ず届きます"))
rejected("deletion-url", lambda _root, paths: replace(paths, "listing", "/account-deletion", "/support#section-3"))
rejected("billing", lambda _root, paths: replace(paths, "listing", "`BILLING_ENABLED=false`", "`BILLING_ENABLED=true`"))
rejected("ads", lambda _root, paths: replace(paths, "listing", "| 広告 | 広告なし |", "| 広告 | 広告あり |"))
rejected("screenshot-order", lambda _root, paths: replace(paths, "listing", "01-mode-select.jpg", "08-caregiver-settings.jpg"))
rejected("stale-caregiver-copy", lambda _root, paths: replace(paths, "listing", "家族が見守る方の飲み忘れ", "家族が見守る方の次の服薬"))
rejected("source-output", lambda _root, paths: replace(paths, "source_map", "01-mode-select.jpg", "01-other.jpg"))
rejected("unsafe-source", lambda _root, paths: replace(paths, "source_map", "docs/android/evidence/", "../evidence/"))
rejected(
    "missing-source",
    lambda root, paths: (root / paths["source_map"].read_text(encoding="utf-8").splitlines()[0].split("\t")[1]).unlink(),
)
rejected("shipping-package", lambda _root, paths: replace(paths, "build_file", MODULE.PACKAGE, "example.invalid"))
rejected("shipping-billing", lambda _root, paths: replace(paths, "build_file", 'runtimeConfig("BILLING_ENABLED", "false")', 'runtimeConfig("BILLING_ENABLED", "true")'))
rejected("app-resource", lambda _root, paths: replace(paths, "strings", "お薬見守り</string>", "別の名前</string>"))
rejected("cleartext", lambda _root, paths: replace(paths, "manifest", 'android:usesCleartextTraffic="false"', 'android:usesCleartextTraffic="true"'))

print("Play store listing contract passed: accepted=1 rejected=20")
