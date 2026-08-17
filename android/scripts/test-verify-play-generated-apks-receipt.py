#!/usr/bin/env python3

import base64
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


FIXTURE_PATH = Path(__file__).with_name("test-verify-play-internal-track-receipt.py")
FIXTURE_SPEC = importlib.util.spec_from_file_location(
    "generated_apks_internal_fixtures", FIXTURE_PATH
)
assert FIXTURE_SPEC is not None and FIXTURE_SPEC.loader is not None
FIXTURES = importlib.util.module_from_spec(FIXTURE_SPEC)
sys.modules[FIXTURE_SPEC.name] = FIXTURES
FIXTURE_SPEC.loader.exec_module(FIXTURES)

SCRIPT_PATH = Path(__file__).with_name("verify-play-generated-apks-receipt.py")
SPEC = importlib.util.spec_from_file_location("verify_play_generated_apks_receipt", SCRIPT_PATH)
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
        MODULE.GeneratedApksReceiptError,
        MODULE.INTERNAL.TrackReceiptError,
        MODULE.INTERNAL.UPLOAD.ReceiptError,
        MODULE.INTERNAL.UPLOAD.HANDOFF.HANDOFF.HandoffError,
        OSError,
    ):
        return
    raise AssertionError(f"Expected rejection: {label}")


def split(
    download_id: str,
    *,
    variant_id: int = 0,
    module_name: str = "base",
    split_id: str = "",
) -> dict[str, object]:
    return {
        "downloadId": download_id,
        "variantId": variant_id,
        "moduleName": module_name,
        "splitId": split_id,
    }


def signing_group(
    certificate: str,
    *,
    prefix: str = "key-one",
    package_name: str = MODULE.EXPECTED_APPLICATION_ID,
) -> dict[str, object]:
    return {
        "certificateSha256Hash": certificate,
        "generatedSplitApks": [
            split(f"{prefix}-base-master"),
            split(f"{prefix}-base-ja", split_id="config.ja"),
        ],
        "generatedAssetPackSlices": [],
        "generatedStandaloneApks": [],
        "generatedRecoveryModules": [],
        "unprotectedGeneratedSplitApks": [],
        "unprotectedGeneratedStandaloneApks": [],
        "targetingInfo": {
            "packageName": package_name,
            "variant": [{"variantNumber": 0}],
            "assetSliceSet": [],
        },
    }


def main() -> None:
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        aab = root / "app-release.aab"
        evidence = root / "play-release-evidence.json"
        output_root = root / "handoffs"
        aab.write_bytes(b"synthetic signed AAB contract")
        store_listing = FIXTURES.FIXTURES.create_store_inputs(root)
        report = FIXTURES.FIXTURES.valid_report(aab, store_listing)
        evidence.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        target = FIXTURES.FIXTURES.MODULE.prepare_handoff(
            aab, evidence, output_root, root
        )
        aab_sha256 = report["artifact"]["sha256"]
        assert isinstance(aab_sha256, str)

        bundle_response = write_json(
            root / "responses/bundle.json",
            {"versionCode": 1, "sha1": "d" * 40, "sha256": aab_sha256},
        )
        upload_name = f"{target.name}.play-upload-receipt.json"
        upload_receipt = root / "receipts/upload" / upload_name
        MODULE.INTERNAL.UPLOAD.verify_play_upload_receipt(
            target, bundle_response, upload_receipt, root
        )
        release_list_response = write_json(
            root / "responses/internal-release-list.json",
            {"releases": [FIXTURES.release([1])]},
        )
        track_response = write_json(
            root / "responses/internal-track.json",
            {"track": "qa", "releases": [FIXTURES.track_release(["1"])]},
        )
        internal_name = f"{target.name}.play-internal-track-receipt.json"
        internal_receipt = root / "receipts/internal" / internal_name
        MODULE.INTERNAL.verify_play_internal_track_receipt(
            target,
            bundle_response,
            upload_receipt,
            release_list_response,
            track_response,
            internal_receipt,
            root,
        )

        app_certificate = "b" * 64
        generated_response = write_json(
            root / "responses/generated-apks.json",
            {"generatedApks": [signing_group(app_certificate)]},
        )
        output_name = f"{target.name}.play-generated-apks-receipt.json"
        output = root / "receipts/generated" / output_name
        receipt = MODULE.verify_play_generated_apks_receipt(
            target,
            bundle_response,
            upload_receipt,
            release_list_response,
            track_response,
            internal_receipt,
            generated_response,
            app_certificate,
            output,
            root,
        )
        assert set(receipt) == {
            "schemaVersion",
            "sources",
            "applicationId",
            "handoffDirectory",
            "commitSha",
            "versionName",
            "versionCode",
            "aabSha256",
            "internalTrackReceiptSha256",
            "apiResponseSha256",
            "appSigningCertificateSha256Fingerprints",
            "uploadCertificateSeparated",
            "signingKeyGroups",
            "generatedSplitApks",
            "baseMasterSplits",
            "downloadEntries",
        }
        expected_fingerprint = ":".join(["BB"] * 32)
        assert receipt["appSigningCertificateSha256Fingerprints"] == [expected_fingerprint]
        assert receipt["uploadCertificateSeparated"] is True
        assert receipt["signingKeyGroups"] == 1
        assert receipt["generatedSplitApks"] == 2
        assert receipt["baseMasterSplits"] == 1
        receipt_bytes = MODULE.canonical_json_bytes(receipt)
        assert output.read_bytes() == receipt_bytes
        lowered = receipt_bytes.lower()
        for forbidden in (
            b"token",
            b"authorization",
            b"credential",
            b"editid",
            b"account",
            b"patient",
            b"password",
            b"secret",
            b"downloadid",
        ):
            assert forbidden not in lowered

        assert MODULE.verify_play_generated_apks_receipt(
            target,
            bundle_response,
            upload_receipt,
            release_list_response,
            track_response,
            internal_receipt,
            generated_response,
            app_certificate,
            output,
            root,
        ) == receipt

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
                app_certificate,
                "--output",
                str(output),
                "--repository-root",
                str(root),
            ],
            check=False,
            capture_output=True,
            text=True,
        )
        assert cli.returncode == 0
        assert "VERSION_CODE=1 SIGNING_KEYS=1 SPLIT_APKS=2" in cli.stdout

        second_certificate = "c" * 64
        second_base64 = base64.urlsafe_b64encode(bytes.fromhex(second_certificate)).decode().rstrip("=")
        rotation_response = write_json(
            root / "responses/generated-apks-rotation.json",
            {
                "generatedApks": [
                    signing_group(app_certificate),
                    signing_group(second_base64, prefix="key-two"),
                ]
            },
        )
        rotation_output = root / "receipts/generated-rotation" / output_name
        rotation = MODULE.verify_play_generated_apks_receipt(
            target,
            bundle_response,
            upload_receipt,
            release_list_response,
            track_response,
            internal_receipt,
            rotation_response,
            f"{app_certificate},{second_certificate}",
            rotation_output,
            root,
        )
        assert rotation["signingKeyGroups"] == 2
        assert rotation["generatedSplitApks"] == 4

        rejected_root = root / "rejected"

        def rejected(
            label: str,
            value: object,
            *,
            expected: str = app_certificate,
            receipt_output: Path | None = None,
        ) -> None:
            response = write_json(rejected_root / label / "response.json", value)
            rejected_output = receipt_output or rejected_root / label / "receipt" / output_name
            expect_failure(
                label,
                lambda: MODULE.verify_play_generated_apks_receipt(
                    target,
                    bundle_response,
                    upload_receipt,
                    release_list_response,
                    track_response,
                    internal_receipt,
                    response,
                    expected,
                    rejected_output,
                    root,
                ),
            )

        expect_failure(
            "missing generated response",
            lambda: MODULE.verify_play_generated_apks_receipt(
                target,
                bundle_response,
                upload_receipt,
                release_list_response,
                track_response,
                internal_receipt,
                rejected_root / "missing.json",
                app_certificate,
                rejected_root / "missing" / output_name,
                root,
            ),
        )
        response_symlink = rejected_root / "response-symlink.json"
        response_symlink.parent.mkdir(parents=True, exist_ok=True)
        response_symlink.symlink_to(generated_response)
        expect_failure(
            "generated response symlink",
            lambda: MODULE.verify_play_generated_apks_receipt(
                target,
                bundle_response,
                upload_receipt,
                release_list_response,
                track_response,
                internal_receipt,
                response_symlink,
                app_certificate,
                rejected_root / "symlink" / output_name,
                root,
            ),
        )
        malformed = rejected_root / "malformed.json"
        malformed.write_text("{", encoding="utf-8")
        expect_failure(
            "malformed generated response",
            lambda: MODULE.verify_play_generated_apks_receipt(
                target,
                bundle_response,
                upload_receipt,
                release_list_response,
                track_response,
                internal_receipt,
                malformed,
                app_certificate,
                rejected_root / "malformed" / output_name,
                root,
            ),
        )

        valid_group = signing_group(app_certificate)
        rejected("response root", [valid_group])
        rejected("response fields", {"generatedApks": [valid_group], "kind": "x"})
        rejected("empty groups", {"generatedApks": []})
        rejected("groups type", {"generatedApks": {}})
        rejected(
            "too many groups",
            {
                "generatedApks": [
                    signing_group(f"{index + 1:064x}", prefix=f"key-{index}")
                    for index in range(MODULE.MAX_SIGNING_KEY_GROUPS + 1)
                ]
            },
            expected=",".join(
                f"{index + 1:064x}" for index in range(MODULE.MAX_SIGNING_KEY_GROUPS + 1)
            ),
        )
        rejected("group fields", {"generatedApks": [{**valid_group, "extra": True}]})
        rejected(
            "missing certificate",
            {"generatedApks": [{key: value for key, value in valid_group.items() if key != "certificateSha256Hash"}]},
        )
        rejected(
            "bad certificate",
            {"generatedApks": [{**valid_group, "certificateSha256Hash": "bad"}]},
        )
        rejected(
            "bad colon certificate",
            {
                "generatedApks": [
                    {**valid_group, "certificateSha256Hash": f"{app_certificate[:-1]}:{app_certificate[-1]}"}
                ]
            },
        )
        rejected(
            "duplicate certificates",
            {"generatedApks": [valid_group, signing_group(app_certificate, prefix="again")]},
        )
        rejected(
            "expected certificate mismatch",
            {"generatedApks": [valid_group]},
            expected="c" * 64,
        )
        rejected(
            "upload certificate reused",
            {"generatedApks": [signing_group("a" * 64)]},
            expected="a" * 64,
        )
        rejected("empty expected certificates", {"generatedApks": [valid_group]}, expected="")
        rejected(
            "duplicate expected certificates",
            {"generatedApks": [valid_group]},
            expected=f"{app_certificate},{app_certificate}",
        )
        rejected(
            "splits type",
            {"generatedApks": [{**valid_group, "generatedSplitApks": {}}]},
        )
        rejected(
            "empty splits",
            {"generatedApks": [{**valid_group, "generatedSplitApks": []}]},
        )
        rejected(
            "split fields",
            {
                "generatedApks": [
                    {**valid_group, "generatedSplitApks": [{**split("one"), "extra": True}]}
                ]
            },
        )
        rejected(
            "empty download id",
            {"generatedApks": [{**valid_group, "generatedSplitApks": [split("")]}]},
        )
        rejected(
            "boolean variant id",
            {
                "generatedApks": [
                    {**valid_group, "generatedSplitApks": [split("one", variant_id=True)]}
                ]
            },
        )
        rejected(
            "bad module name",
            {
                "generatedApks": [
                    {**valid_group, "generatedSplitApks": [split("one", module_name="bad name")]}
                ]
            },
        )
        rejected(
            "duplicate download id",
            {
                "generatedApks": [
                    {
                        **valid_group,
                        "generatedSplitApks": [
                            split("same"),
                            split("same", split_id="config.ja"),
                        ],
                    }
                ]
            },
        )
        rejected(
            "no base master",
            {
                "generatedApks": [
                    {
                        **valid_group,
                        "generatedSplitApks": [split("one", split_id="config.ja")],
                    }
                ]
            },
        )
        rejected(
            "wrong production package",
            {"generatedApks": [signing_group(app_certificate, package_name="example.invalid")]},
        )
        rejected(
            "targeting fields",
            {
                "generatedApks": [
                    {
                        **valid_group,
                        "targetingInfo": {
                            **valid_group["targetingInfo"],
                            "extra": True,
                        },
                    }
                ]
            },
        )
        rejected(
            "empty targeting variants",
            {
                "generatedApks": [
                    {**valid_group, "targetingInfo": {"packageName": MODULE.EXPECTED_APPLICATION_ID, "variant": []}}
                ]
            },
        )
        rejected(
            "asset slices",
            {
                "generatedApks": [
                    {**valid_group, "generatedAssetPackSlices": [{"downloadId": "asset"}]}
                ]
            },
        )
        rejected(
            "recovery modules",
            {
                "generatedApks": [
                    {**valid_group, "generatedRecoveryModules": [{"downloadId": "recovery"}]}
                ]
            },
        )
        rejected(
            "bad universal",
            {"generatedApks": [{**valid_group, "generatedUniversalApk": {"id": "x"}}]},
        )

        missing_internal = rejected_root / "missing-internal.json"
        expect_failure(
            "missing C116 receipt",
            lambda: MODULE.verify_play_generated_apks_receipt(
                target,
                bundle_response,
                upload_receipt,
                release_list_response,
                track_response,
                missing_internal,
                generated_response,
                app_certificate,
                rejected_root / "missing-internal" / output_name,
                root,
            ),
        )
        tampered_internal = rejected_root / "tampered-internal" / internal_name
        tampered_internal.parent.mkdir(parents=True)
        tampered_internal.write_text("{}\n", encoding="utf-8")
        expect_failure(
            "tampered C116 receipt",
            lambda: MODULE.verify_play_generated_apks_receipt(
                target,
                bundle_response,
                upload_receipt,
                release_list_response,
                track_response,
                tampered_internal,
                generated_response,
                app_certificate,
                rejected_root / "tampered-internal-output" / output_name,
                root,
            ),
        )
        rejected(
            "wrong output name",
            {"generatedApks": [valid_group]},
            receipt_output=rejected_root / "wrong-name.json",
        )
        rejected(
            "inside handoff",
            {"generatedApks": [valid_group]},
            receipt_output=target / output_name,
        )
        real_parent = rejected_root / "real-parent"
        real_parent.mkdir(parents=True)
        parent_symlink = rejected_root / "parent-symlink"
        parent_symlink.symlink_to(real_parent, target_is_directory=True)
        rejected(
            "output parent symlink",
            {"generatedApks": [valid_group]},
            receipt_output=parent_symlink / output_name,
        )
        conflict = rejected_root / "conflict" / output_name
        conflict.parent.mkdir(parents=True)
        conflict.write_text("conflict\n", encoding="utf-8")
        rejected(
            "existing conflict",
            {"generatedApks": [valid_group]},
            receipt_output=conflict,
        )
        tampered_target = rejected_root / "tampered-handoff" / target.name
        tampered_target.parent.mkdir(parents=True)
        shutil.copytree(target, tampered_target)
        next(tampered_target.glob("*.aab")).write_bytes(b"tampered")
        expect_failure(
            "tampered handoff",
            lambda: MODULE.verify_play_generated_apks_receipt(
                tampered_target,
                bundle_response,
                upload_receipt,
                release_list_response,
                track_response,
                internal_receipt,
                generated_response,
                app_certificate,
                rejected_root / "tampered-handoff-output" / output_name,
                root,
            ),
        )
        oversized = rejected_root / "oversized.json"
        oversized.write_bytes(b" " * (MODULE.MAX_RESPONSE_BYTES + 1))
        expect_failure(
            "oversized generated response",
            lambda: MODULE.verify_play_generated_apks_receipt(
                target,
                bundle_response,
                upload_receipt,
                release_list_response,
                track_response,
                internal_receipt,
                oversized,
                app_certificate,
                rejected_root / "oversized" / output_name,
                root,
            ),
        )
        assert not list(root.rglob("*.tmp"))

    print("Play generated APK receipt contract passed (4 accepted/idempotent, 36 rejected).")


if __name__ == "__main__":
    main()
