#!/usr/bin/env python3

from dataclasses import replace
import importlib.util
import json
from pathlib import Path
import sys
import tempfile


sys.dont_write_bytecode = True
SCRIPT_PATH = Path(__file__).with_name("generate-release-evidence.py")
SPEC = importlib.util.spec_from_file_location("generate_release_evidence", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def main() -> None:
    sha = "a" * 64
    valid = MODULE.PolicyInput(
        application_id=MODULE.EXPECTED_APPLICATION_ID,
        version_code=1,
        version_name="1.0.6",
        source_commit="b" * 40,
        release_input_changes=(),
        aab_sha256=sha,
        actual_signer_sha256=sha,
        expected_signer_sha256=sha,
        dependency_lock_sha256=sha,
        sdk_inventory_sha256=sha,
        modules=("base",),
        dex_file_count=4,
        resolved_module_count=175,
        dependency_lock_coordinate_count=187,
    )
    assert MODULE.evidence_failures(valid) == []
    invalid = (
        replace(valid, application_id="com.example.debug"),
        replace(valid, version_code=0),
        replace(valid, version_name="development"),
        replace(valid, source_commit="short"),
        replace(valid, release_input_changes=("android/app/build.gradle.kts",)),
        replace(valid, aab_sha256="malformed"),
        replace(valid, actual_signer_sha256="c" * 64),
        replace(valid, modules=("base", "feature")),
        replace(valid, dex_file_count=0),
        replace(valid, resolved_module_count=0),
        replace(valid, dependency_lock_coordinate_count=0),
    )
    assert all(MODULE.evidence_failures(fixture) for fixture in invalid)
    assert MODULE.normalized_sha256_fingerprint(":".join(["AA"] * 32)) == "aa" * 32
    assert MODULE.normalized_sha256_fingerprint("malformed") is None
    assert MODULE.dependency_lock_coordinate_count(
        "# generated\nalpha:one:1.0=release\nbeta:two:2.0=release,bundle\nempty=\n"
    ) == 2

    with tempfile.TemporaryDirectory(prefix="medmanager-release-evidence-test.") as directory:
        root = Path(directory)
        output = root / "evidence.json"
        report = {"schemaVersion": 2, "source": {"commitSha": "b" * 40}}
        MODULE.write_json_atomic(output, report)
        assert json.loads(output.read_text(encoding="utf-8")) == report
        first_bytes = output.read_bytes()
        MODULE.write_json_atomic(output, report)
        assert output.read_bytes() == first_bytes
        assert not list(output.parent.glob("*.tmp"))

        listing = root / "play-store-listing-ja.md"
        phone = root / "phone-ja-JP"
        source_map = phone / "sources.tsv"
        icon = root / "icon-512.png"
        feature = root / "feature-graphic-1024x500.jpg"
        phone.mkdir()
        listing.write_text("listing\n", encoding="utf-8")
        icon.write_bytes(b"icon")
        feature.write_bytes(b"feature")
        source_map.write_text(
            "".join(f"{name}\tdocs/android/evidence/source.png\n" for name in MODULE.EXPECTED_STORE_SCREENSHOTS),
            encoding="utf-8",
        )
        for index, name in enumerate(MODULE.EXPECTED_STORE_SCREENSHOTS):
            (phone / name).write_bytes(f"screenshot-{index}".encode())
        store = MODULE.store_listing_evidence(listing, source_map, icon, feature, phone)
        assert store["locale"] == "ja-JP"
        assert [item["fileName"] for item in store["screenshots"]] == list(
            MODULE.EXPECTED_STORE_SCREENSHOTS
        )
        assert store["listingSha256"] == MODULE.file_sha256(listing)

        source_map.write_text("08-caregiver-settings.jpg\tsource.png\n", encoding="utf-8")
        try:
            MODULE.store_listing_evidence(listing, source_map, icon, feature, phone)
        except MODULE.EvidenceError:
            pass
        else:
            raise AssertionError("Drifted screenshot source map unexpectedly passed")

    print("Release evidence policy contract passed (2 accepted, 12 rejected; atomic JSON passed).")


if __name__ == "__main__":
    main()
