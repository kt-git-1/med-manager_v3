#!/usr/bin/env python3

from dataclasses import replace
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import zipfile


sys.dont_write_bytecode = True
SCRIPT_PATH = Path(__file__).with_name("generate-release-evidence.py")
SPEC = importlib.util.spec_from_file_location("generate_release_evidence", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)
HANDOFF_PATH = Path(__file__).with_name("prepare-play-release-handoff.py")
HANDOFF_SPEC = importlib.util.spec_from_file_location("prepare_play_release_handoff", HANDOFF_PATH)
assert HANDOFF_SPEC is not None and HANDOFF_SPEC.loader is not None
HANDOFF = importlib.util.module_from_spec(HANDOFF_SPEC)
sys.modules[HANDOFF_SPEC.name] = HANDOFF
HANDOFF_SPEC.loader.exec_module(HANDOFF)


def run(command: list[str], cwd: Path) -> None:
    subprocess.run(
        command,
        cwd=cwd,
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def write_store_inputs(root: Path) -> tuple[Path, Path, Path, Path, Path]:
    listing = root / "docs/android/play-store-listing-ja.md"
    asset_root = root / "docs/android/play-store-assets"
    phone = asset_root / "phone-ja-JP"
    source_map = phone / "sources.tsv"
    icon = asset_root / "icon-512.png"
    feature = asset_root / "feature-graphic-1024x500.jpg"
    phone.mkdir(parents=True)
    listing.write_text("listing\n", encoding="utf-8")
    icon.write_bytes(b"icon")
    feature.write_bytes(b"feature")
    source_map.write_text(
        "".join(
            f"{name}\tdocs/android/evidence/source.png\n"
            for name in MODULE.EXPECTED_STORE_SCREENSHOTS
        ),
        encoding="utf-8",
    )
    for index, name in enumerate(MODULE.EXPECTED_STORE_SCREENSHOTS):
        (phone / name).write_bytes(f"screenshot-{index}".encode())
    return listing, source_map, icon, feature, phone


def verify_integrated_report(root: Path) -> None:
    repository = root / "repository"
    repository.mkdir()
    listing, source_map, icon, feature, phone = write_store_inputs(repository)
    android_input = repository / "android/fixture.txt"
    android_input.parent.mkdir()
    android_input.write_text("fixture\n", encoding="utf-8")
    run(["git", "init", "-q"], repository)
    run(["git", "config", "user.name", "Release Evidence Fixture"], repository)
    run(["git", "config", "user.email", "fixture@example.invalid"], repository)
    run(["git", "config", "commit.gpgsign", "false"], repository)
    run(["git", "add", "android", "docs"], repository)
    run(["git", "commit", "-qm", "fixture"], repository)

    build = root / "build"
    build.mkdir()
    aab = build / "app-release.aab"
    with zipfile.ZipFile(aab, "w") as bundle:
        bundle.writestr("base/manifest/AndroidManifest.xml", b"manifest")
        bundle.writestr("base/dex/classes.dex", b"dex")
        bundle.writestr("base/lib/arm64-v8a/libfixture.so", b"native")
    keystore = build / "upload.p12"
    password = "fixture-password"
    run(
        [
            "keytool",
            "-genkeypair",
            "-alias",
            "upload",
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-validity",
            "1",
            "-dname",
            "CN=Med Manager Release Evidence Fixture",
            "-storetype",
            "PKCS12",
            "-keystore",
            str(keystore),
            "-storepass",
            password,
            "-keypass",
            password,
            "-noprompt",
        ],
        repository,
    )
    run(
        [
            "jarsigner",
            "-keystore",
            str(keystore),
            "-storepass",
            password,
            "-keypass",
            password,
            str(aab),
            "upload",
        ],
        repository,
    )
    certificate_output = MODULE.run_command(
        repository, ["keytool", "-printcert", "-jarfile", str(aab)]
    )
    fingerprint_match = re.search(r"SHA256:\s*([0-9A-Fa-f:]+)", certificate_output)
    assert fingerprint_match is not None
    expected_signer = MODULE.normalized_sha256_fingerprint(fingerprint_match.group(1))
    assert expected_signer is not None

    manifest = build / "AndroidManifest.xml"
    dependency_lock = build / "releaseRuntimeClasspath.lockfile"
    inventory = build / "release-sdk-inventory.txt"
    manifest.write_bytes(b"manifest")
    dependency_lock.write_text("fixture:module:1.0=releaseRuntimeClasspath\n", encoding="utf-8")
    inventory.write_text("Resolved modules: 1\n", encoding="utf-8")
    arguments = SimpleNamespace(
        repository_root=repository,
        aab=aab,
        manifest=manifest,
        dependency_lock=dependency_lock,
        inventory=inventory,
        application_id=MODULE.EXPECTED_APPLICATION_ID,
        version_code=1,
        version_name="1.0.6",
        min_sdk=26,
        target_sdk=35,
        baseline_sha="c" * 40,
        expected_signer_sha256=expected_signer,
        bundletool_version="1.18.0",
        store_listing=listing,
        store_source_map=source_map,
        store_icon=icon,
        store_feature_graphic=feature,
        store_phone_directory=phone,
    )
    report = MODULE.generate_report(arguments)
    assert report["schemaVersion"] == 2
    assert report["source"]["commitSha"] == MODULE.run_command(
        repository, ["git", "rev-parse", "HEAD"]
    )
    assert report["artifact"]["uploadCertificateSha256"] == expected_signer
    assert report["storeListing"]["listingSha256"] == MODULE.file_sha256(listing)
    assert [item["fileName"] for item in report["storeListing"]["screenshots"]] == list(
        MODULE.EXPECTED_STORE_SCREENSHOTS
    )

    evidence = build / "play-release-evidence.json"
    MODULE.write_json_atomic(evidence, report)
    handoff_root = build / "handoff"
    handoff = HANDOFF.prepare_handoff(aab, evidence, handoff_root, repository)
    aab_name = f"med-manager-android-{handoff.name}.aab"
    assert {path.name for path in handoff.iterdir()} == {
        aab_name,
        "play-release-evidence.json",
        "SHA256SUMS",
    }
    assert (handoff / "play-release-evidence.json").read_bytes() == evidence.read_bytes()
    assert HANDOFF.prepare_handoff(aab, evidence, handoff_root, repository) == handoff

    icon_bytes = icon.read_bytes()
    icon.write_bytes(b"changed icon")
    try:
        HANDOFF.prepare_handoff(aab, evidence, build / "changed-store-handoff", repository)
    except HANDOFF.HandoffError as error:
        assert "icon512Sha256" in str(error)
    else:
        raise AssertionError("Changed store input unexpectedly passed generated-ledger handoff")
    icon.write_bytes(icon_bytes)

    listing.write_text("dirty listing\n", encoding="utf-8")
    try:
        MODULE.generate_report(arguments)
    except MODULE.EvidenceError as error:
        assert "Release inputs contain uncommitted changes" in str(error)
    else:
        raise AssertionError("Dirty integrated store input unexpectedly passed")


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

        listing, source_map, icon, feature, phone = write_store_inputs(root)
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

        verify_integrated_report(root)

    print(
        "Release evidence policy contract passed "
        "(4 accepted, 14 rejected; generated-ledger handoff and atomic JSON passed)."
    )


if __name__ == "__main__":
    main()
