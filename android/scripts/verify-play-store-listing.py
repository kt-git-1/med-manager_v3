#!/usr/bin/env python3
"""Fail-closed contract for the Japanese Google Play listing handoff."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Sequence


class PlayStoreListingError(RuntimeError):
    pass


PACKAGE = "com.afterlifearchive.medmanager"
APP_NAME = "お薬見守り"
EXPECTED_SCREENSHOTS = [
    "01-mode-select.jpg",
    "02-patient-today.jpg",
    "03-patient-history.jpg",
    "04-caregiver-today.jpg",
    "05-caregiver-medications.jpg",
    "06-caregiver-inventory.jpg",
    "07-caregiver-history.jpg",
    "08-caregiver-settings.jpg",
]
EXPECTED_URLS = [
    "https://www.okusuri-mimamori.com/",
    "https://www.okusuri-mimamori.com/privacy",
    "https://www.okusuri-mimamori.com/terms",
    "https://www.okusuri-mimamori.com/support",
    "https://www.okusuri-mimamori.com/account-deletion",
]
TEXT_LIMITS = (30, 80, 4_000, 500)
SCREENSHOT_ROW = re.compile(
    r"^\|\s*(\d+)\s*\|\s*`([^`]+\.jpg)`\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|$"
)


@dataclass(frozen=True)
class ListingSummary:
    text_blocks: int
    screenshots: int
    source_mappings: int
    public_urls: int


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise PlayStoreListingError(message)


def _read(path: Path, label: str) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except OSError as error:
        raise PlayStoreListingError(f"Cannot read {label}: {error}") from error


def _verify_source_map(repository_root: Path, source_map_path: Path) -> int:
    lines = [line for line in _read(source_map_path, "screenshot source map").splitlines() if line]
    _require(len(lines) == len(EXPECTED_SCREENSHOTS), "Source map must contain exactly eight rows")
    parsed: list[tuple[str, str]] = []
    for index, line in enumerate(lines, start=1):
        fields = line.split("\t")
        _require(len(fields) == 2 and all(value and value == value.strip() for value in fields),
                 f"Invalid source map row {index}")
        parsed.append((fields[0], fields[1]))
    _require([output for output, _source in parsed] == EXPECTED_SCREENSHOTS,
             "Source map output names/order drifted")
    source_paths = [source for _output, source in parsed]
    _require(len(source_paths) == len(set(source_paths)), "Screenshot evidence sources must be unique")
    for source in source_paths:
        pure = PurePosixPath(source)
        _require(
            not pure.is_absolute()
            and ".." not in pure.parts
            and pure.parts[:3] == ("docs", "android", "evidence")
            and pure.suffix == ".png",
            f"Unsafe or non-evidence screenshot source: {source}",
        )
        _require(repository_root.joinpath(*pure.parts).is_file(),
                 f"Screenshot evidence source does not exist: {source}")
    return len(parsed)


def verify_play_store_listing(
    repository_root: Path,
    listing_path: Path,
    source_map_path: Path,
    manifest_path: Path,
    strings_path: Path,
    build_file_path: Path,
) -> ListingSummary:
    root = repository_root.resolve()
    listing = _read(listing_path, "Play listing")
    manifest = _read(manifest_path, "Android manifest")
    strings = _read(strings_path, "Android strings")
    build_file = _read(build_file_path, "Android build configuration")

    _require(f"**デフォルト言語:** 日本語（`ja-JP`）" in listing,
             "Play default locale must remain ja-JP")
    _require(f"**パッケージ:** `{PACKAGE}`" in listing, "Play package declaration drifted")

    blocks = re.findall(r"```text\n(.*?)\n```", listing, flags=re.DOTALL)
    _require(len(blocks) == 4, "Listing must contain exactly four ordered text blocks")
    for index, (block, limit) in enumerate(zip(blocks, TEXT_LIMITS), start=1):
        _require(len(block) <= limit, f"Play text block {index} exceeds {limit} characters")
        _require(block == block.strip(), f"Play text block {index} has surrounding whitespace")
    _require(blocks[0] == APP_NAME, "Play app name differs from the shipping app name")

    full_description = blocks[2]
    required_copy = [
        "利用状況データの送信は、アプリ内で同意した場合のみ有効",
        "患者名、薬名、服薬日時、服薬状態、在庫数、メールアドレス",
        "医療・個人情報は含めません",
        "アカウントと関連データはアプリ内から削除",
        "医療機器ではなく",
        "診断、治療、処方または医療上の判断を行うものではありません",
        "医師・薬剤師",
        "通知は端末の設定や利用状況により届かない場合があります",
    ]
    for marker in required_copy:
        _require(marker in full_description, f"Required Play disclosure copy is missing: {marker}")

    for url in EXPECTED_URLS:
        _require(url in listing, f"Canonical public URL is missing: {url}")
    _require("/support#section-3" not in listing, "Obsolete support-anchor deletion URL must not return")
    _require(listing.count("BILLING_ENABLED=false") == 2,
             "Initial-release billing declaration/checklist drifted")
    _require("BILLING_ENABLED=true" not in listing, "Enabled billing must not appear in the initial listing")
    _require("| 広告 | 広告なし |" in listing, "No-ads declaration drifted")
    _require("| 医療機器 | いいえ |" in listing, "Non-medical-device declaration drifted")

    screenshot_rows: list[tuple[int, str, str, str]] = []
    for line in listing.splitlines():
        match = SCREENSHOT_ROW.match(line)
        if match:
            number, filename, purpose, alt_text = match.groups()
            screenshot_rows.append((int(number), filename, purpose.strip(), alt_text.strip()))
    _require([row[0] for row in screenshot_rows] == list(range(1, 9)),
             "Screenshot listing must contain the exact 1-8 order")
    _require([row[1] for row in screenshot_rows] == EXPECTED_SCREENSHOTS,
             "Screenshot filenames/order drifted")
    _require(all(1 <= len(row[3]) <= 140 for row in screenshot_rows),
             "Every screenshot needs Japanese alt text of at most 140 characters")
    _require("次の服薬" not in screenshot_rows[3][3],
             "Caregiver Today alt text describes the obsolete next-dose card")

    _require(f'val releaseApplicationId = "{PACKAGE}"' in build_file,
             "Shipping application ID differs from the Play package")
    _require('runtimeConfig("BILLING_ENABLED", "false")' in build_file,
             "Shipping billing default must remain false")
    _require('android:label="@string/app_name"' in manifest,
             "Manifest must use the canonical app-name resource")
    _require('android:usesCleartextTraffic="false"' in manifest,
             "Shipping manifest must reject cleartext traffic")
    _require("tools:node=\"remove\"" in manifest and "permission.AD_ID" in manifest,
             "Shipping manifest must retain advertising-ID removal")
    _require(f'<string name="app_name">{APP_NAME}</string>' in strings,
             "Shipping app-name resource differs from the Play listing")

    mappings = _verify_source_map(root, source_map_path)
    return ListingSummary(
        text_blocks=len(blocks),
        screenshots=len(screenshot_rows),
        source_mappings=mappings,
        public_urls=len(EXPECTED_URLS),
    )


def _arguments(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify Japanese Google Play listing handoff.")
    parser.add_argument("--repository-root", type=Path, required=True)
    parser.add_argument("--listing", type=Path)
    parser.add_argument("--source-map", type=Path)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--strings", type=Path)
    parser.add_argument("--build-file", type=Path)
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _arguments(argv or sys.argv[1:])
    root = arguments.repository_root.resolve()
    try:
        summary = verify_play_store_listing(
            root,
            arguments.listing or root / "docs/android/play-store-listing-ja.md",
            arguments.source_map or root / "docs/android/play-store-assets/phone-ja-JP/sources.tsv",
            arguments.manifest or root / "android/app/src/main/AndroidManifest.xml",
            arguments.strings or root / "android/app/src/main/res/values/strings.xml",
            arguments.build_file or root / "android/app/build.gradle.kts",
        )
    except (OSError, PlayStoreListingError) as error:
        print(f"Play store listing verification failed: {error}", file=sys.stderr)
        return 1
    print(
        "Play store listing verification passed: "
        f"textBlocks={summary.text_blocks} screenshots={summary.screenshots} "
        f"sourceMappings={summary.source_mappings} publicUrls={summary.public_urls}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
