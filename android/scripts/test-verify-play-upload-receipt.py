#!/usr/bin/env python3

import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


FIXTURE_PATH = Path(__file__).with_name("test-prepare-play-release-handoff.py")
FIXTURE_SPEC = importlib.util.spec_from_file_location("play_handoff_test_fixtures", FIXTURE_PATH)
assert FIXTURE_SPEC is not None and FIXTURE_SPEC.loader is not None
FIXTURES = importlib.util.module_from_spec(FIXTURE_SPEC)
sys.modules[FIXTURE_SPEC.name] = FIXTURES
FIXTURE_SPEC.loader.exec_module(FIXTURES)

SCRIPT_PATH = Path(__file__).with_name("verify-play-upload-receipt.py")
SPEC = importlib.util.spec_from_file_location("verify_play_upload_receipt", SCRIPT_PATH)
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
    except (MODULE.ReceiptError, MODULE.HANDOFF.HANDOFF.HandoffError, OSError):
        return
    raise AssertionError(f"Expected rejection: {label}")


def main() -> None:
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        aab = root / "app-release.aab"
        evidence = root / "play-release-evidence.json"
        output_root = root / "handoffs"
        aab.write_bytes(b"synthetic signed AAB contract")
        store_listing = FIXTURES.create_store_inputs(root)
        report = FIXTURES.valid_report(aab, store_listing)
        evidence.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        target = FIXTURES.MODULE.prepare_handoff(aab, evidence, output_root, root)
        expected_name = f"{target.name}.play-upload-receipt.json"
        aab_sha256 = report["artifact"]["sha256"]
        assert isinstance(aab_sha256, str)
        direct_bundle = {
            "versionCode": 1,
            "sha1": "a" * 40,
            "sha256": aab_sha256,
        }
        direct_response = write_json(root / "responses/direct.json", direct_bundle)
        direct_output = root / "receipts/direct" / expected_name

        receipt = MODULE.verify_play_upload_receipt(target, direct_response, direct_output, root)
        assert set(receipt) == {
            "schemaVersion",
            "source",
            "applicationId",
            "handoffDirectory",
            "commitSha",
            "versionName",
            "versionCode",
            "aabSha256",
            "apiResponseSha256",
            "playBundle",
        }
        assert receipt["applicationId"] == "com.afterlifearchive.medmanager"
        assert receipt["handoffDirectory"] == target.name
        assert receipt["playBundle"] == direct_bundle
        receipt_bytes = MODULE.canonical_json_bytes(receipt)
        assert direct_output.read_bytes() == receipt_bytes
        lowered_receipt = receipt_bytes.lower()
        for forbidden in (
            b"token",
            b"authorization",
            b"credential",
            b"editid",
            b"account",
            b"patient",
            b"password",
            b"secret",
        ):
            assert forbidden not in lowered_receipt
        assert MODULE.verify_play_upload_receipt(target, direct_response, direct_output, root) == receipt

        cli = subprocess.run(
            [
                sys.executable,
                str(SCRIPT_PATH),
                "--handoff",
                str(target),
                "--bundle-response",
                str(direct_response),
                "--output",
                str(direct_output),
                "--repository-root",
                str(root),
            ],
            check=False,
            capture_output=True,
            text=True,
        )
        assert cli.returncode == 0
        assert f"VERSION_CODE=1 AAB_SHA256={aab_sha256}" in cli.stdout

        list_response = write_json(
            root / "responses/list.json",
            {
                "kind": MODULE.EXPECTED_LIST_KIND,
                "bundles": [
                    {"versionCode": 2, "sha1": "b" * 40, "sha256": "c" * 64},
                    direct_bundle,
                ],
            },
        )
        list_output = root / "receipts/list" / expected_name
        list_receipt = MODULE.verify_play_upload_receipt(target, list_response, list_output, root)
        assert list_receipt["playBundle"] == direct_bundle

        rejected_root = root / "rejected"

        def rejected(label: str, response_value: object, *, output: Path | None = None) -> None:
            response = write_json(rejected_root / label / "response.json", response_value)
            receipt_output = output or rejected_root / label / "receipt" / expected_name
            expect_failure(
                label,
                lambda: MODULE.verify_play_upload_receipt(
                    target, response, receipt_output, root
                ),
            )

        missing = rejected_root / "missing.json"
        expect_failure(
            "missing response",
            lambda: MODULE.verify_play_upload_receipt(
                target, missing, rejected_root / "missing" / expected_name, root
            ),
        )

        response_symlink = rejected_root / "response-symlink.json"
        response_symlink.parent.mkdir(parents=True, exist_ok=True)
        response_symlink.symlink_to(direct_response)
        expect_failure(
            "response symlink",
            lambda: MODULE.verify_play_upload_receipt(
                target, response_symlink, rejected_root / "symlink" / expected_name, root
            ),
        )

        malformed = rejected_root / "malformed.json"
        malformed.write_text("{", encoding="utf-8")
        expect_failure(
            "malformed response",
            lambda: MODULE.verify_play_upload_receipt(
                target, malformed, rejected_root / "malformed" / expected_name, root
            ),
        )
        rejected("response root", [direct_bundle])
        rejected("direct fields", {**direct_bundle, "kind": "unexpected"})
        rejected("boolean version", {**direct_bundle, "versionCode": True})
        rejected("sha1", {**direct_bundle, "sha1": "invalid"})
        rejected("uppercase sha256", {**direct_bundle, "sha256": aab_sha256.upper()})
        rejected("wrong version", {**direct_bundle, "versionCode": 2})
        rejected("wrong sha256", {**direct_bundle, "sha256": "d" * 64})
        rejected(
            "list kind",
            {"kind": "unexpected", "bundles": [direct_bundle]},
        )
        rejected("empty list", {"kind": MODULE.EXPECTED_LIST_KIND, "bundles": []})
        rejected(
            "duplicate version",
            {"kind": MODULE.EXPECTED_LIST_KIND, "bundles": [direct_bundle, direct_bundle]},
        )
        rejected(
            "invalid other bundle",
            {
                "kind": MODULE.EXPECTED_LIST_KIND,
                "bundles": [direct_bundle, {"versionCode": 2, "sha1": "b" * 40}],
            },
        )
        rejected(
            "no matching version",
            {
                "kind": MODULE.EXPECTED_LIST_KIND,
                "bundles": [
                    {"versionCode": 2, "sha1": "b" * 40, "sha256": "c" * 64}
                ],
            },
        )
        rejected(
            "wrong output name",
            direct_bundle,
            output=rejected_root / "wrong-name" / "receipt.json",
        )
        rejected(
            "inside handoff",
            direct_bundle,
            output=target / expected_name,
        )

        real_parent = rejected_root / "real-parent"
        real_parent.mkdir(parents=True)
        parent_symlink = rejected_root / "parent-symlink"
        parent_symlink.symlink_to(real_parent, target_is_directory=True)
        rejected(
            "output parent symlink",
            direct_bundle,
            output=parent_symlink / expected_name,
        )

        conflict_output = rejected_root / "conflict" / expected_name
        conflict_output.parent.mkdir(parents=True)
        conflict_output.write_text("conflict\n", encoding="utf-8")
        rejected("existing conflict", direct_bundle, output=conflict_output)

        tampered_target = rejected_root / "tampered-handoff" / target.name
        tampered_target.parent.mkdir(parents=True)
        shutil.copytree(target, tampered_target)
        next(tampered_target.glob("*.aab")).write_bytes(b"tampered")
        expect_failure(
            "tampered handoff",
            lambda: MODULE.verify_play_upload_receipt(
                tampered_target,
                direct_response,
                rejected_root / "tampered-receipt" / expected_name,
                root,
            ),
        )

        oversized = rejected_root / "oversized.json"
        oversized.write_bytes(b" " * (MODULE.MAX_RESPONSE_BYTES + 1))
        expect_failure(
            "oversized response",
            lambda: MODULE.verify_play_upload_receipt(
                target, oversized, rejected_root / "oversized" / expected_name, root
            ),
        )
        assert not list(root.rglob("*.tmp"))

    print("Play upload receipt contract passed (4 accepted/idempotent, 21 rejected).")


if __name__ == "__main__":
    main()
