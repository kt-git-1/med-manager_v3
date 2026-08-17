#!/usr/bin/env python3

from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import warnings
import zipfile


C117_TEST_PATH = Path(__file__).with_name("test-verify-play-generated-apks-receipt.py")
C117_TEST_SPEC = importlib.util.spec_from_file_location(
    "downloaded_base_apks_c117_fixtures", C117_TEST_PATH
)
assert C117_TEST_SPEC is not None and C117_TEST_SPEC.loader is not None
C117_FIXTURES = importlib.util.module_from_spec(C117_TEST_SPEC)
sys.modules[C117_TEST_SPEC.name] = C117_FIXTURES
C117_TEST_SPEC.loader.exec_module(C117_FIXTURES)

SCRIPT_PATH = Path(__file__).with_name("verify-play-downloaded-base-apks-receipt.py")
SPEC = importlib.util.spec_from_file_location(
    "verify_play_downloaded_base_apks_receipt", SCRIPT_PATH
)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def write_json(path: Path, value: object) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, separators=(",", ":")), encoding="utf-8")
    return path


def expect_failure(label: str, action) -> None:
    try:
        action()
    except (
        MODULE.DownloadedBaseApksReceiptError,
        MODULE.GENERATED.GeneratedApksReceiptError,
        MODULE.GENERATED.INTERNAL.TrackReceiptError,
        MODULE.GENERATED.INTERNAL.UPLOAD.ReceiptError,
        MODULE.GENERATED.INTERNAL.UPLOAD.HANDOFF.HANDOFF.HandoffError,
        OSError,
    ):
        return
    raise AssertionError(f"Expected rejection: {label}")


def create_fake_sdk(root: Path) -> Path:
    tools = root / "build-tools" / "99.0.0"
    tools.mkdir(parents=True)
    tool_source = """#!/usr/bin/env python3
from pathlib import Path
import sys

apk = Path(sys.argv[-1])
suffix = '.badging' if Path(sys.argv[0]).name == 'aapt2' else '.signing'
control = Path(str(apk) + suffix)
if not control.is_file():
    raise SystemExit(9)
text = control.read_text(encoding='utf-8')
if text.startswith('__EXIT_1__'):
    raise SystemExit(1)
if text.startswith('__MUTATE_APK__\\n'):
    with apk.open('ab') as stream:
        stream.write(b'changed-during-tool-verification')
    text = text.removeprefix('__MUTATE_APK__\\n')
sys.stdout.write(text)
"""
    for name in ("aapt2", "apksigner"):
        path = tools / name
        path.write_text(tool_source, encoding="utf-8")
        path.chmod(0o755)
    return root


def badging(
    *,
    package: str = MODULE.EXPECTED_APPLICATION_ID,
    version_code: str = "1",
    version_name: str = "1.0.6",
    suffix: str = "",
) -> str:
    return (
        f"package: name='{package}' versionCode='{version_code}' "
        f"versionName='{version_name}'{suffix}\n"
        "sdkVersion:'26'\ntargetSdkVersion:'35'\n"
    )


def signing(certificate: str, *, schemes: str | None = None, signers: int = 1) -> str:
    scheme_rows = schemes or (
        "Verified using v1 scheme (JAR signing): false\n"
        "Verified using v2 scheme (APK Signature Scheme v2): true\n"
        "Verified using v3 scheme (APK Signature Scheme v3): false\n"
        "Verified using v3.1 scheme (APK Signature Scheme v3.1): false\n"
    )
    return (
        "Verifies\n"
        f"{scheme_rows}"
        f"Number of signers: {signers}\n"
        f"Signer #1 certificate SHA-256 digest: {certificate}\n"
    )


def create_apk(
    path: Path,
    certificate: str,
    *,
    entries: dict[str, bytes] | None = None,
    package_row: str | None = None,
    signing_output: str | None = None,
) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    inventory = entries or {
        "AndroidManifest.xml": b"synthetic binary manifest",
        "classes.dex": b"dex\n035\x00synthetic",
        "resources.arsc": b"synthetic resources",
        "META-INF/synthetic-cert-sha256.txt": certificate.encode("ascii"),
    }
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED) as archive:
        for name, payload in inventory.items():
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.external_attr = (stat.S_IFREG | 0o644) << 16
            archive.writestr(info, payload)
    Path(str(path) + ".badging").write_text(
        package_row if package_row is not None else badging(), encoding="utf-8"
    )
    Path(str(path) + ".signing").write_text(
        signing_output if signing_output is not None else signing(certificate),
        encoding="utf-8",
    )
    return path


def duplicate_entry_apk(path: Path, certificate: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("AndroidManifest.xml", b"manifest")
            archive.writestr("classes.dex", b"dex")
            archive.writestr("classes.dex", b"duplicate")
    Path(str(path) + ".badging").write_text(badging(), encoding="utf-8")
    Path(str(path) + ".signing").write_text(signing(certificate), encoding="utf-8")
    return path


def zip_symlink_apk(path: Path, certificate: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("AndroidManifest.xml", b"manifest")
        archive.writestr("classes.dex", b"dex")
        info = zipfile.ZipInfo("unsafe-link")
        info.create_system = 3
        info.external_attr = (stat.S_IFLNK | 0o777) << 16
        archive.writestr(info, "../../outside")
    Path(str(path) + ".badging").write_text(badging(), encoding="utf-8")
    Path(str(path) + ".signing").write_text(signing(certificate), encoding="utf-8")
    return path


def run_real_sdk_integration(
    apk: Path,
    repository_root: Path,
    expected_version_code: int,
    expected_version_name: str,
) -> None:
    aapt2, apksigner = MODULE.resolve_android_tools(repository_root, None)
    output = MODULE.run_tool(
        [
            str(apksigner),
            "verify",
            "--verbose",
            "--print-certs",
            "--min-sdk-version",
            "26",
            str(apk),
        ],
        "apksigner",
    )
    matches = [
        MODULE.SIGNER_DIGEST.fullmatch(line.strip()) for line in output.splitlines()
    ]
    certificates = [
        MODULE.GENERATED.normalize_sha256(
            match.group(2), label="Real SDK integration signer SHA-256"
        )
        for match in matches
        if match is not None
    ]
    if len(certificates) != 1:
        raise AssertionError("Real SDK integration requires exactly one signer certificate")
    result = MODULE.inspect_downloaded_apk(
        apk,
        certificates[0],
        expected_version_code,
        expected_version_name,
        repository_root / "android/app/build/tmp/c118-nonexistent-handoff",
        aapt2,
        apksigner,
    )
    assert result["zipEntries"] > 0
    assert result["dexFiles"] > 0
    assert any(item in {"v2", "v3", "v3.1"} for item in result["verifiedSignatureSchemes"])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--real-apk", type=Path)
    parser.add_argument("--repository-root", type=Path)
    parser.add_argument("--expected-version-code", type=int)
    parser.add_argument("--expected-version-name")
    arguments = parser.parse_args()
    real_values = (
        arguments.real_apk,
        arguments.repository_root,
        arguments.expected_version_code,
        arguments.expected_version_name,
    )
    if any(value is not None for value in real_values) and not all(
        value is not None for value in real_values
    ):
        parser.error("real SDK integration requires all four integration arguments")

    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        sdk = create_fake_sdk(root / "sdk")
        aab = root / "app-release.aab"
        evidence = root / "play-release-evidence.json"
        aab.write_bytes(b"synthetic signed AAB contract")
        store_listing = C117_FIXTURES.FIXTURES.FIXTURES.create_store_inputs(root)
        report = C117_FIXTURES.FIXTURES.FIXTURES.valid_report(aab, store_listing)
        evidence.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        target = C117_FIXTURES.FIXTURES.FIXTURES.MODULE.prepare_handoff(
            aab, evidence, root / "handoffs", root
        )
        aab_sha256 = report["artifact"]["sha256"]
        assert isinstance(aab_sha256, str)

        bundle_response = write_json(
            root / "responses/bundle.json",
            {"versionCode": 1, "sha1": "d" * 40, "sha256": aab_sha256},
        )
        upload_name = f"{target.name}.play-upload-receipt.json"
        upload_receipt = root / "receipts/upload" / upload_name
        MODULE.GENERATED.INTERNAL.UPLOAD.verify_play_upload_receipt(
            target, bundle_response, upload_receipt, root
        )
        release_list_response = write_json(
            root / "responses/internal-release-list.json",
            {"releases": [C117_FIXTURES.FIXTURES.release([1])]},
        )
        track_response = write_json(
            root / "responses/internal-track.json",
            {"track": "qa", "releases": [C117_FIXTURES.FIXTURES.track_release(["1"])]},
        )
        internal_name = f"{target.name}.play-internal-track-receipt.json"
        internal_receipt = root / "receipts/internal" / internal_name
        MODULE.GENERATED.INTERNAL.verify_play_internal_track_receipt(
            target,
            bundle_response,
            upload_receipt,
            release_list_response,
            track_response,
            internal_receipt,
            root,
        )

        first_certificate = "b" * 64
        generated_response = write_json(
            root / "responses/generated-apks.json",
            {
                "generatedApks": [
                    C117_FIXTURES.signing_group(first_certificate, prefix="key-one")
                ]
            },
        )
        generated_name = f"{target.name}.play-generated-apks-receipt.json"
        generated_receipt = root / "receipts/generated" / generated_name
        MODULE.GENERATED.verify_play_generated_apks_receipt(
            target,
            bundle_response,
            upload_receipt,
            release_list_response,
            track_response,
            internal_receipt,
            generated_response,
            first_certificate,
            generated_receipt,
            root,
        )
        first_apk = create_apk(
            root / "downloads/key-one-base-master.apk", first_certificate
        )
        output_name = f"{target.name}.play-downloaded-base-apks-receipt.json"
        output = root / "receipts/downloaded" / output_name

        common = (
            target,
            bundle_response,
            upload_receipt,
            release_list_response,
            track_response,
            internal_receipt,
            generated_response,
            first_certificate,
            generated_receipt,
        )

        def verify(
            selections: list[tuple[str, Path]],
            receipt_output: Path,
            *,
            inputs: tuple[Path, Path, Path, Path, Path, Path, Path, str, Path] = common,
            sdk_root: Path | None = sdk,
        ) -> dict[str, object]:
            return MODULE.verify_play_downloaded_base_apks_receipt(
                *inputs,
                selections,
                receipt_output,
                root,
                sdk_root,
            )

        receipt = verify([("key-one-base-master", first_apk)], output)
        assert set(receipt) == {
            "schemaVersion",
            "sources",
            "applicationId",
            "handoffDirectory",
            "commitSha",
            "versionName",
            "versionCode",
            "aabSha256",
            "generatedApksReceiptSha256",
            "androidBuildToolsVersion",
            "completeSigningKeyCoverage",
            "downloadedBaseApks",
        }
        assert receipt["applicationId"] == MODULE.EXPECTED_APPLICATION_ID
        assert receipt["versionCode"] == 1
        assert receipt["versionName"] == "1.0.6"
        assert receipt["androidBuildToolsVersion"] == "99.0.0"
        assert receipt["completeSigningKeyCoverage"] is True
        downloaded = receipt["downloadedBaseApks"]
        assert isinstance(downloaded, list) and len(downloaded) == 1
        assert downloaded[0]["appSigningCertificateSha256Fingerprint"] == ":".join(
            ["BB"] * 32
        )
        assert downloaded[0]["verifiedSignatureSchemes"] == ["v2"]
        receipt_bytes = MODULE.canonical_json_bytes(receipt)
        assert output.read_bytes() == receipt_bytes
        lowered = receipt_bytes.lower()
        assert b"downloadid" not in lowered
        assert str(root).encode().lower() not in lowered
        for forbidden in (b"token", b"authorization", b"credential", b"patient", b"password", b"secret"):
            assert forbidden not in lowered

        assert verify([("key-one-base-master", first_apk)], output) == receipt

        cli = subprocess.run(
            [
                sys.executable,
                str(SCRIPT_PATH),
                "--handoff",
                str(target),
                "--bundle-response",
                str(bundle_response),
                "--upload-receipt",
                str(upload_receipt),
                "--release-list-response",
                str(release_list_response),
                "--track-response",
                str(track_response),
                "--internal-track-receipt",
                str(internal_receipt),
                "--generated-apks-response",
                str(generated_response),
                "--expected-app-signing-sha256",
                first_certificate,
                "--generated-apks-receipt",
                str(generated_receipt),
                "--downloaded-base-apk",
                "key-one-base-master",
                str(first_apk),
                "--output",
                str(output),
                "--repository-root",
                str(root),
                "--android-sdk-root",
                str(sdk),
            ],
            check=False,
            capture_output=True,
            text=True,
        )
        assert cli.returncode == 0, cli.stderr
        assert "VERSION_CODE=1 BASE_APKS=1" in cli.stdout

        second_certificate = "c" * 64
        rotation_response = write_json(
            root / "responses/generated-apks-rotation.json",
            {
                "generatedApks": [
                    C117_FIXTURES.signing_group(first_certificate, prefix="key-one"),
                    C117_FIXTURES.signing_group(second_certificate, prefix="key-two"),
                ]
            },
        )
        rotation_receipt = root / "receipts/generated-rotation" / generated_name
        expected_rotation = f"{first_certificate},{second_certificate}"
        MODULE.GENERATED.verify_play_generated_apks_receipt(
            target,
            bundle_response,
            upload_receipt,
            release_list_response,
            track_response,
            internal_receipt,
            rotation_response,
            expected_rotation,
            rotation_receipt,
            root,
        )
        second_apk = create_apk(
            root / "downloads/key-two-base-master.apk", second_certificate
        )
        rotation_output = root / "receipts/downloaded-rotation" / output_name
        rotation_inputs = (
            target,
            bundle_response,
            upload_receipt,
            release_list_response,
            track_response,
            internal_receipt,
            rotation_response,
            expected_rotation,
            rotation_receipt,
        )
        rotation = verify(
            [
                ("key-two-base-master", second_apk),
                ("key-one-base-master", first_apk),
            ],
            rotation_output,
            inputs=rotation_inputs,
        )
        assert len(rotation["downloadedBaseApks"]) == 2
        assert [
            item["appSigningCertificateSha256Fingerprint"]
            for item in rotation["downloadedBaseApks"]
        ] == sorted(
            [
                ":".join(["BB"] * 32),
                ":".join(["CC"] * 32),
            ]
        )

        rejected_root = root / "rejected"
        rejected_count = 0

        def rejected(
            label: str,
            selections: list[tuple[str, Path]],
            *,
            receipt_output: Path | None = None,
            inputs: tuple[Path, Path, Path, Path, Path, Path, Path, str, Path] = common,
            sdk_root: Path | None = sdk,
        ) -> None:
            nonlocal rejected_count
            rejected_count += 1
            destination = receipt_output or rejected_root / label / output_name
            expect_failure(
                label,
                lambda: verify(
                    selections,
                    destination,
                    inputs=inputs,
                    sdk_root=sdk_root,
                ),
            )

        rejected("no selections", [])
        rejected(
            "duplicate download ids",
            [
                ("key-one-base-master", first_apk),
                ("key-one-base-master", second_apk),
            ],
        )
        rejected("unknown download id", [("unknown", first_apk)])
        rejected("configuration split", [("key-one-base-ja", first_apk)])
        rejected(
            "incomplete signing rotation",
            [("key-one-base-master", first_apk)],
            inputs=rotation_inputs,
        )
        global_duplicate_response_value = {
            "generatedApks": [
                C117_FIXTURES.signing_group(first_certificate, prefix="key-one"),
                C117_FIXTURES.signing_group(second_certificate, prefix="key-two"),
            ]
        }
        global_duplicate_response_value["generatedApks"][0]["generatedSplitApks"][1][
            "downloadId"
        ] = "key-two-base-master"
        global_duplicate_response = write_json(
            rejected_root / "global-duplicate-response.json",
            global_duplicate_response_value,
        )
        global_duplicate_receipt = rejected_root / "global-duplicate" / generated_name
        MODULE.GENERATED.verify_play_generated_apks_receipt(
            target,
            bundle_response,
            upload_receipt,
            release_list_response,
            track_response,
            internal_receipt,
            global_duplicate_response,
            expected_rotation,
            global_duplicate_receipt,
            root,
        )
        global_duplicate_inputs = (
            *common[:6],
            global_duplicate_response,
            expected_rotation,
            global_duplicate_receipt,
        )
        rejected(
            "globally duplicated downloadId",
            [
                ("key-one-base-master", first_apk),
                ("key-two-base-master", second_apk),
            ],
            inputs=global_duplicate_inputs,
        )
        duplicate_group_response = write_json(
            rejected_root / "duplicate-group-response.json",
            {
                "generatedApks": [
                    {
                        **C117_FIXTURES.signing_group(first_certificate),
                        "generatedSplitApks": [
                            C117_FIXTURES.split("master-one"),
                            C117_FIXTURES.split("master-two", variant_id=2),
                        ],
                    }
                ]
            },
        )
        duplicate_group_receipt = rejected_root / "duplicate-group" / generated_name
        MODULE.GENERATED.verify_play_generated_apks_receipt(
            target,
            bundle_response,
            upload_receipt,
            release_list_response,
            track_response,
            internal_receipt,
            duplicate_group_response,
            first_certificate,
            duplicate_group_receipt,
            root,
        )
        duplicate_group_inputs = (*common[:6], duplicate_group_response, first_certificate, duplicate_group_receipt)
        rejected(
            "two masters for one signing key",
            [("master-one", first_apk), ("master-two", second_apk)],
            inputs=duplicate_group_inputs,
        )
        rejected(
            "duplicate local path",
            [
                ("key-one-base-master", first_apk),
                ("key-two-base-master", first_apk),
            ],
            inputs=rotation_inputs,
        )

        missing_apk = rejected_root / "missing.apk"
        rejected("missing APK", [("key-one-base-master", missing_apk)])
        apk_symlink = rejected_root / "apk-symlink.apk"
        apk_symlink.parent.mkdir(parents=True, exist_ok=True)
        apk_symlink.symlink_to(first_apk)
        rejected("APK symlink", [("key-one-base-master", apk_symlink)])
        wrong_suffix = rejected_root / "base-master.zip"
        shutil.copy2(first_apk, wrong_suffix)
        rejected("wrong APK suffix", [("key-one-base-master", wrong_suffix)])
        empty_apk = rejected_root / "empty.apk"
        empty_apk.write_bytes(b"")
        rejected("empty APK", [("key-one-base-master", empty_apk)])
        oversized_apk = rejected_root / "oversized.apk"
        with oversized_apk.open("wb") as stream:
            stream.truncate(MODULE.MAX_APK_BYTES + 1)
        rejected("oversized APK", [("key-one-base-master", oversized_apk)])
        invalid_apk = rejected_root / "invalid.apk"
        invalid_apk.write_bytes(b"not a zip")
        rejected("invalid APK ZIP", [("key-one-base-master", invalid_apk)])
        duplicate_apk = duplicate_entry_apk(
            rejected_root / "duplicate-entry.apk", first_certificate
        )
        rejected("duplicate ZIP entry", [("key-one-base-master", duplicate_apk)])
        traversal_apk = create_apk(
            rejected_root / "traversal.apk",
            first_certificate,
            entries={
                "AndroidManifest.xml": b"manifest",
                "classes.dex": b"dex",
                "../outside": b"unsafe",
            },
        )
        rejected("ZIP traversal", [("key-one-base-master", traversal_apk)])
        linked_apk = zip_symlink_apk(rejected_root / "zip-symlink.apk", first_certificate)
        rejected("ZIP symlink", [("key-one-base-master", linked_apk)])
        private_apk = create_apk(
            rejected_root / "private.apk",
            first_certificate,
            entries={
                "AndroidManifest.xml": b"manifest",
                "classes.dex": b"dex",
                "assets/service-account.json": b"{}",
            },
        )
        rejected("private ZIP entry", [("key-one-base-master", private_apk)])
        no_manifest_apk = create_apk(
            rejected_root / "no-manifest.apk",
            first_certificate,
            entries={"classes.dex": b"dex"},
        )
        rejected("missing manifest", [("key-one-base-master", no_manifest_apk)])
        no_dex_apk = create_apk(
            rejected_root / "no-dex.apk",
            first_certificate,
            entries={"AndroidManifest.xml": b"manifest", "resources.arsc": b"resources"},
        )
        rejected("missing DEX", [("key-one-base-master", no_dex_apk)])

        def tool_case(label: str, *, package_row: str | None = None, signing_output: str | None = None) -> Path:
            return create_apk(
                rejected_root / f"{label}.apk",
                first_certificate,
                package_row=package_row,
                signing_output=signing_output,
            )

        rejected(
            "aapt2 failure",
            [("key-one-base-master", tool_case("aapt2-failure", package_row="__EXIT_1__\n"))],
        )
        rejected(
            "missing package row",
            [("key-one-base-master", tool_case("missing-package", package_row="sdkVersion:'26'\n"))],
        )
        rejected(
            "duplicate package attribute",
            [
                (
                    "key-one-base-master",
                    tool_case(
                        "duplicate-attribute",
                        package_row=badging(suffix=" name='duplicate'"),
                    ),
                )
            ],
        )
        rejected(
            "wrong package",
            [("key-one-base-master", tool_case("wrong-package", package_row=badging(package="invalid.example")))],
        )
        rejected(
            "wrong versionCode",
            [("key-one-base-master", tool_case("wrong-code", package_row=badging(version_code="2")))],
        )
        rejected(
            "noncanonical versionCode",
            [("key-one-base-master", tool_case("bad-code", package_row=badging(version_code="01")))],
        )
        rejected(
            "wrong versionName",
            [("key-one-base-master", tool_case("wrong-name", package_row=badging(version_name="1.0.5")))],
        )
        rejected(
            "non-base split",
            [("key-one-base-master", tool_case("split", package_row=badging(suffix=" split='config.ja'")))],
        )
        rejected(
            "apksigner failure",
            [("key-one-base-master", tool_case("signer-failure", signing_output="__EXIT_1__\n"))],
        )
        rejected(
            "APK mutation during SDK verification",
            [
                (
                    "key-one-base-master",
                    tool_case(
                        "mutating-apk",
                        signing_output="__MUTATE_APK__\n" + signing(first_certificate),
                    ),
                )
            ],
        )
        rejected(
            "missing verifies",
            [("key-one-base-master", tool_case("no-verifies", signing_output=signing(first_certificate).replace("Verifies\n", "")))],
        )
        rejected(
            "multiple signers",
            [("key-one-base-master", tool_case("multi-signer", signing_output=signing(first_certificate, signers=2)))],
        )
        rejected(
            "wrong signer certificate",
            [("key-one-base-master", tool_case("wrong-cert", signing_output=signing("c" * 64)))],
        )
        duplicate_signer_output = signing(first_certificate) + (
            f"Signer #1 certificate SHA-256 digest: {first_certificate}\n"
        )
        rejected(
            "duplicate signer certificate row",
            [("key-one-base-master", tool_case("duplicate-cert", signing_output=duplicate_signer_output))],
        )
        v1_only = (
            "Verified using v1 scheme (JAR signing): true\n"
            "Verified using v2 scheme (APK Signature Scheme v2): false\n"
            "Verified using v3 scheme (APK Signature Scheme v3): false\n"
        )
        rejected(
            "v1-only signature",
            [("key-one-base-master", tool_case("v1-only", signing_output=signing(first_certificate, schemes=v1_only)))],
        )
        duplicate_scheme = (
            "Verified using v2 scheme (APK Signature Scheme v2): true\n"
            "Verified using v2 scheme (APK Signature Scheme v2): true\n"
        )
        rejected(
            "duplicate signature scheme",
            [("key-one-base-master", tool_case("duplicate-scheme", signing_output=signing(first_certificate, schemes=duplicate_scheme)))],
        )

        missing_sdk = rejected_root / "missing-sdk"
        rejected(
            "missing Android SDK tools",
            [("key-one-base-master", first_apk)],
            sdk_root=missing_sdk,
        )
        sdk_symlink = rejected_root / "sdk-symlink"
        sdk_symlink.parent.mkdir(parents=True, exist_ok=True)
        sdk_symlink.symlink_to(sdk, target_is_directory=True)
        # Resolving an SDK-root symlink is allowed, but tool symlinks are not.
        unsafe_sdk = create_fake_sdk(rejected_root / "unsafe-sdk")
        (unsafe_sdk / "build-tools/99.0.0/aapt2").unlink()
        (unsafe_sdk / "build-tools/99.0.0/aapt2").symlink_to(
            sdk / "build-tools/99.0.0/aapt2"
        )
        rejected(
            "symlink Android SDK tool",
            [("key-one-base-master", first_apk)],
            sdk_root=unsafe_sdk,
        )

        copied_apk = rejected_root / "duplicate-bytes.apk"
        shutil.copy2(first_apk, copied_apk)
        Path(str(copied_apk) + ".badging").write_text(badging(), encoding="utf-8")
        Path(str(copied_apk) + ".signing").write_text(
            signing(second_certificate), encoding="utf-8"
        )
        rejected(
            "duplicate APK bytes across signing keys",
            [
                ("key-one-base-master", first_apk),
                ("key-two-base-master", copied_apk),
            ],
            inputs=rotation_inputs,
        )

        missing_c117 = rejected_root / "missing-c117.json"
        missing_c117_inputs = (*common[:8], missing_c117)
        rejected(
            "missing C117 receipt",
            [("key-one-base-master", first_apk)],
            inputs=missing_c117_inputs,
        )
        tampered_c117 = rejected_root / "tampered-c117" / generated_name
        tampered_c117.parent.mkdir(parents=True)
        tampered_c117.write_text("{}\n", encoding="utf-8")
        tampered_c117_inputs = (*common[:8], tampered_c117)
        rejected(
            "tampered C117 receipt",
            [("key-one-base-master", first_apk)],
            inputs=tampered_c117_inputs,
        )
        rejected(
            "wrong output name",
            [("key-one-base-master", first_apk)],
            receipt_output=rejected_root / "wrong-name.json",
        )
        rejected(
            "output replaces C117",
            [("key-one-base-master", first_apk)],
            receipt_output=generated_receipt,
        )
        rejected(
            "output inside handoff",
            [("key-one-base-master", first_apk)],
            receipt_output=target / output_name,
        )
        real_parent = rejected_root / "real-parent"
        real_parent.mkdir(parents=True)
        parent_symlink = rejected_root / "parent-symlink"
        parent_symlink.symlink_to(real_parent, target_is_directory=True)
        rejected(
            "output parent symlink",
            [("key-one-base-master", first_apk)],
            receipt_output=parent_symlink / output_name,
        )
        conflict = rejected_root / "conflict" / output_name
        conflict.parent.mkdir(parents=True)
        conflict.write_text("conflict\n", encoding="utf-8")
        rejected(
            "existing output conflict",
            [("key-one-base-master", first_apk)],
            receipt_output=conflict,
        )

        assert rejected_count == 46, rejected_count
        assert not list(root.rglob("*.tmp"))

    if arguments.real_apk is not None:
        assert arguments.repository_root is not None
        assert arguments.expected_version_code is not None
        assert arguments.expected_version_name is not None
        run_real_sdk_integration(
            arguments.real_apk,
            arguments.repository_root,
            arguments.expected_version_code,
            arguments.expected_version_name,
        )

    print(
        "Play downloaded base APK receipt contract passed "
        f"(4 accepted/idempotent, 46 rejected, real-sdk={int(arguments.real_apk is not None)})."
    )


if __name__ == "__main__":
    main()
