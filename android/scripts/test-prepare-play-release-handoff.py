#!/usr/bin/env python3

import importlib.util
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


sys.dont_write_bytecode = True
SCRIPT_PATH = Path(__file__).with_name("prepare-play-release-handoff.py")
SPEC = importlib.util.spec_from_file_location("prepare_play_release_handoff", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)
VERIFIER_PATH = Path(__file__).with_name("verify-prepared-play-release-handoff.py")
VERIFIER_SPEC = importlib.util.spec_from_file_location(
    "verify_prepared_play_release_handoff", VERIFIER_PATH
)
assert VERIFIER_SPEC is not None and VERIFIER_SPEC.loader is not None
VERIFIER = importlib.util.module_from_spec(VERIFIER_SPEC)
sys.modules[VERIFIER_SPEC.name] = VERIFIER
VERIFIER_SPEC.loader.exec_module(VERIFIER)


def create_store_inputs(root: Path) -> dict[str, object]:
    asset_root = root / "docs/android/play-store-assets"
    phone = asset_root / "phone-ja-JP"
    phone.mkdir(parents=True)
    listing = root / "docs/android/play-store-listing-ja.md"
    listing.write_text("listing\n", encoding="utf-8")
    source_map = phone / "sources.tsv"
    source_map.write_text(
        "".join(f"{name}\tdocs/android/evidence/source.png\n" for name in MODULE.EXPECTED_STORE_SCREENSHOTS),
        encoding="utf-8",
    )
    icon = asset_root / "icon-512.png"
    feature = asset_root / "feature-graphic-1024x500.jpg"
    icon.write_bytes(b"icon")
    feature.write_bytes(b"feature")
    screenshots = []
    for index, name in enumerate(MODULE.EXPECTED_STORE_SCREENSHOTS):
        path = phone / name
        path.write_bytes(f"screenshot-{index}".encode())
        screenshots.append({"fileName": name, "sha256": MODULE.file_sha256(path)})
    return {
        "locale": "ja-JP",
        "listingSha256": MODULE.file_sha256(listing),
        "screenshotSourceMapSha256": MODULE.file_sha256(source_map),
        "icon512Sha256": MODULE.file_sha256(icon),
        "featureGraphicSha256": MODULE.file_sha256(feature),
        "screenshots": screenshots,
    }


def valid_report(aab: Path, store_listing: dict[str, object]) -> dict[str, object]:
    return {
        "schemaVersion": 2,
        "source": {
            "commitSha": "b" * 40,
            "branch": "android-dev",
            "releaseInputTreeClean": True,
        },
        "application": {
            "applicationId": MODULE.EXPECTED_APPLICATION_ID,
            "versionCode": 1,
            "versionName": "1.0.6",
            "minSdk": 26,
            "targetSdk": 35,
        },
        "artifact": {
            "fileName": aab.name,
            "sha256": MODULE.file_sha256(aab),
            "uploadCertificateSha256": "a" * 64,
            "modules": ["base"],
        },
        "storeListing": store_listing,
        "verifiedGates": list(MODULE.EXPECTED_GATES),
    }


def expect_failure(label: str, action) -> None:
    try:
        action()
    except MODULE.HandoffError:
        return
    raise AssertionError(f"{label} unexpectedly passed")


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="medmanager-play-handoff-test.") as directory:
        root = Path(directory)
        aab = root / "app-release.aab"
        evidence = root / "play-release-evidence.json"
        output_root = root / "handoff"
        aab.write_bytes(b"synthetic signed AAB contract")
        store_listing = create_store_inputs(root)
        report = valid_report(aab, store_listing)
        evidence.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        target = MODULE.prepare_handoff(aab, evidence, output_root, root)
        assert target.name == "v1.0.6-code1-" + "b" * 12
        aab_name = f"med-manager-android-{target.name}.aab"
        assert {entry.name for entry in target.iterdir()} == {
            aab_name,
            "play-release-evidence.json",
            "SHA256SUMS",
        }
        assert MODULE.prepare_handoff(aab, evidence, output_root, root) == target
        assert not list(output_root.glob(".handoff-*"))
        retained = VERIFIER.verify_prepared_handoff(target, root)
        assert retained["directory"] == target.name
        assert retained["files"] == 3
        cli = subprocess.run(
            [
                sys.executable,
                str(VERIFIER_PATH),
                "--handoff",
                str(target),
                "--repository-root",
                str(root),
            ],
            check=False,
            capture_output=True,
            text=True,
        )
        assert cli.returncode == 0
        assert "FILES=3" in cli.stdout

        retained_fixtures = root / "retained-fixtures"

        def copied_target(label: str) -> Path:
            copy = retained_fixtures / label / target.name
            copy.parent.mkdir(parents=True)
            shutil.copytree(target, copy)
            return copy

        extra = copied_target("extra")
        (extra / "unexpected.txt").write_text("unexpected\n", encoding="utf-8")
        expect_failure("Retained extra file", lambda: VERIFIER.verify_prepared_handoff(extra, root))

        checksum = copied_target("checksum")
        (checksum / "SHA256SUMS").write_text("tampered\n", encoding="utf-8")
        expect_failure(
            "Retained checksum tamper", lambda: VERIFIER.verify_prepared_handoff(checksum, root)
        )

        noncanonical = copied_target("noncanonical")
        noncanonical_evidence = noncanonical / "play-release-evidence.json"
        noncanonical_bytes = json.dumps(report, separators=(",", ":")).encode("utf-8")
        noncanonical_evidence.write_bytes(noncanonical_bytes)
        noncanonical_aab = noncanonical / aab_name
        (noncanonical / "SHA256SUMS").write_text(
            MODULE.expected_checksum_text(
                aab_name,
                MODULE.file_sha256(noncanonical_aab),
                hashlib.sha256(noncanonical_bytes).hexdigest(),
            ),
            encoding="utf-8",
        )
        expect_failure(
            "Retained noncanonical evidence",
            lambda: VERIFIER.verify_prepared_handoff(noncanonical, root),
        )

        icon = root / "docs/android/play-store-assets/icon-512.png"
        icon_bytes = icon.read_bytes()
        icon.write_bytes(b"changed icon")
        expect_failure("Retained store drift", lambda: VERIFIER.verify_prepared_handoff(target, root))
        icon.write_bytes(icon_bytes)

        renamed = root / "retained-wrong-name"
        shutil.copytree(target, renamed)
        expect_failure("Retained wrong name", lambda: VERIFIER.verify_prepared_handoff(renamed, root))

        symlink = root / "retained-symlink"
        symlink.symlink_to(target, target_is_directory=True)
        expect_failure("Retained symlink", lambda: VERIFIER.verify_prepared_handoff(symlink, root))

        original_aab = aab.read_bytes()
        aab.write_bytes(b"tampered")
        expect_failure(
            "Tampered source AAB",
            lambda: MODULE.prepare_handoff(aab, evidence, root / "tampered", root),
        )
        aab.write_bytes(original_aab)

        for label, mutate in (
            ("Dirty source", lambda value: value["source"].update(releaseInputTreeClean=False)),
            ("Wrong application", lambda value: value["application"].update(applicationId="com.example.debug")),
            ("Malformed signer", lambda value: value["artifact"].update(uploadCertificateSha256="bad")),
            ("Feature module", lambda value: value["artifact"].update(modules=["base", "feature"])),
            ("Missing gate", lambda value: value.update(verifiedGates=list(MODULE.EXPECTED_GATES[:-1]))),
        ):
            fixture = valid_report(aab, store_listing)
            mutate(fixture)
            fixture_path = root / f"{label.replace(' ', '-').lower()}.json"
            fixture_path.write_text(json.dumps(fixture), encoding="utf-8")
            expect_failure(
                label,
                lambda path=fixture_path: MODULE.prepare_handoff(aab, path, root / path.stem, root),
            )

        feature = root / "docs/android/play-store-assets/feature-graphic-1024x500.jpg"
        feature_bytes = feature.read_bytes()
        feature.unlink()
        feature.symlink_to(root / "docs/android/play-store-listing-ja.md")
        expect_failure(
            "Symlinked store input",
            lambda: MODULE.prepare_handoff(aab, evidence, root / "symlinked-store-input", root),
        )
        feature.unlink()
        feature.write_bytes(feature_bytes)

        for label, mutate in (
            ("Missing store listing", lambda value: value.pop("storeListing")),
            (
                "Tampered listing hash",
                lambda value: value["storeListing"].update(listingSha256="c" * 64),
            ),
            (
                "Reordered screenshots",
                lambda value: value["storeListing"]["screenshots"].reverse(),
            ),
        ):
            fixture = valid_report(aab, json.loads(json.dumps(store_listing)))
            mutate(fixture)
            fixture_path = root / f"{label.replace(' ', '-').lower()}.json"
            fixture_path.write_text(json.dumps(fixture), encoding="utf-8")
            expect_failure(
                label,
                lambda path=fixture_path: MODULE.prepare_handoff(aab, path, root / path.stem, root),
            )

        (target / aab_name).write_bytes(b"tampered packaged AAB")
        expect_failure(
            "Tampered existing handoff",
            lambda: MODULE.prepare_handoff(aab, evidence, output_root, root),
        )

    print("Play release handoff contract passed (3 accepted/idempotent, 17 rejected).")


if __name__ == "__main__":
    main()
