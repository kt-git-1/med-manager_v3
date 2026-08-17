#!/usr/bin/env python3

import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


FIXTURE_PATH = Path(__file__).with_name("test-prepare-play-release-handoff.py")
FIXTURE_SPEC = importlib.util.spec_from_file_location("internal_track_handoff_fixtures", FIXTURE_PATH)
assert FIXTURE_SPEC is not None and FIXTURE_SPEC.loader is not None
FIXTURES = importlib.util.module_from_spec(FIXTURE_SPEC)
sys.modules[FIXTURE_SPEC.name] = FIXTURES
FIXTURE_SPEC.loader.exec_module(FIXTURES)

SCRIPT_PATH = Path(__file__).with_name("verify-play-internal-track-receipt.py")
SPEC = importlib.util.spec_from_file_location("verify_play_internal_track_receipt", SCRIPT_PATH)
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
        MODULE.TrackReceiptError,
        MODULE.UPLOAD.ReceiptError,
        MODULE.UPLOAD.HANDOFF.HANDOFF.HandoffError,
        OSError,
    ):
        return
    raise AssertionError(f"Expected rejection: {label}")


def release(
    version_codes: list[int],
    *,
    name: str = "1 (1.0.0)",
    track: str = MODULE.EXPECTED_TRACK,
    lifecycle: str = MODULE.EXPECTED_LIFECYCLE,
) -> dict[str, object]:
    return {
        "releaseName": name,
        "track": track,
        "activeArtifacts": [{"versionCode": item} for item in version_codes],
        "releaseLifecycleState": lifecycle,
    }


def track_release(
    version_codes: list[str],
    *,
    status: str = "completed",
    name: str = "1 (1.0.0)",
    **extra: object,
) -> dict[str, object]:
    return {
        "name": name,
        "versionCodes": version_codes,
        "status": status,
        **extra,
    }


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
        aab_sha256 = report["artifact"]["sha256"]
        assert isinstance(aab_sha256, str)

        bundle_response = write_json(
            root / "responses/bundle.json",
            {"versionCode": 1, "sha1": "a" * 40, "sha256": aab_sha256},
        )
        upload_name = f"{target.name}.play-upload-receipt.json"
        upload_receipt = root / "receipts/upload" / upload_name
        MODULE.UPLOAD.verify_play_upload_receipt(
            target, bundle_response, upload_receipt, root
        )

        release_list_response = write_json(
            root / "responses/internal-release-list.json",
            {"releases": [release([1])]},
        )
        track_response = write_json(
            root / "responses/internal-track.json",
            {
                "track": "qa",
                "releases": [
                    track_release(
                        ["1"],
                        releaseNotes=[{"language": "ja-JP", "text": "更新内容"}],
                        inAppUpdatePriority=0,
                    )
                ],
            },
        )
        output_name = f"{target.name}.play-internal-track-receipt.json"
        output = root / "receipts/internal" / output_name
        receipt = MODULE.verify_play_internal_track_receipt(
            target,
            bundle_response,
            upload_receipt,
            release_list_response,
            track_response,
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
            "uploadReceiptSha256",
            "releaseListResponseSha256",
            "trackResponseSha256",
            "track",
            "releaseLifecycleState",
            "trackStatus",
            "activeVersionCodes",
        }
        assert receipt["applicationId"] == "com.afterlifearchive.medmanager"
        assert receipt["track"] == "qa"
        assert receipt["versionCode"] == 1
        assert receipt["releaseLifecycleState"] == MODULE.EXPECTED_LIFECYCLE
        assert receipt["trackStatus"] == "completed"
        assert receipt["activeVersionCodes"] == [1]
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
            b"releasename",
        ):
            assert forbidden not in lowered

        assert MODULE.verify_play_internal_track_receipt(
            target,
            bundle_response,
            upload_receipt,
            release_list_response,
            track_response,
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
        assert (
            "TRACK=qa VERSION_CODE=1 STATE=RELEASE_LIFECYCLE_STATE_PUBLISHED "
            "TRACK_STATUS=completed" in cli.stdout
        )

        multiple_response = write_json(
            root / "responses/internal-multiple.json",
            {
                "releases": [
                    release(
                        [2],
                        name="2 draft",
                        lifecycle="RELEASE_LIFECYCLE_STATE_IN_REVIEW",
                    ),
                    release([3, 1], name="1 published"),
                ]
            },
        )
        multiple_output = root / "receipts/internal-multiple" / output_name
        multiple = MODULE.verify_play_internal_track_receipt(
            target,
            bundle_response,
            upload_receipt,
            multiple_response,
            track_response,
            multiple_output,
            root,
        )
        assert multiple["activeVersionCodes"] == [1, 3]

        rejected_root = root / "rejected"

        def rejected(label: str, value: object, *, receipt_output: Path | None = None) -> None:
            rejected_release_list = write_json(rejected_root / label / "response.json", value)
            rejected_output = receipt_output or rejected_root / label / "receipt" / output_name
            expect_failure(
                label,
                lambda: MODULE.verify_play_internal_track_receipt(
                    target,
                    bundle_response,
                    upload_receipt,
                    rejected_release_list,
                    track_response,
                    rejected_output,
                    root,
                ),
            )

        def rejected_track(label: str, value: object) -> None:
            rejected_track_response = write_json(
                rejected_root / label / "track-response.json", value
            )
            expect_failure(
                label,
                lambda: MODULE.verify_play_internal_track_receipt(
                    target,
                    bundle_response,
                    upload_receipt,
                    release_list_response,
                    rejected_track_response,
                    rejected_root / label / "receipt" / output_name,
                    root,
                ),
            )

        expect_failure(
            "missing release-list response",
            lambda: MODULE.verify_play_internal_track_receipt(
                target,
                bundle_response,
                upload_receipt,
                rejected_root / "missing.json",
                track_response,
                rejected_root / "missing" / output_name,
                root,
            ),
        )

        symlink_response = rejected_root / "response-symlink.json"
        symlink_response.parent.mkdir(parents=True, exist_ok=True)
        symlink_response.symlink_to(release_list_response)
        expect_failure(
            "release-list response symlink",
            lambda: MODULE.verify_play_internal_track_receipt(
                target,
                bundle_response,
                upload_receipt,
                symlink_response,
                track_response,
                rejected_root / "symlink" / output_name,
                root,
            ),
        )

        malformed = rejected_root / "malformed.json"
        malformed.write_text("{", encoding="utf-8")
        expect_failure(
            "malformed release-list response",
            lambda: MODULE.verify_play_internal_track_receipt(
                target,
                bundle_response,
                upload_receipt,
                malformed,
                track_response,
                rejected_root / "malformed" / output_name,
                root,
            ),
        )

        rejected("response root", [release([1])])
        rejected("envelope fields", {"releases": [release([1])], "nextPageToken": "x"})
        rejected("empty releases", {"releases": []})
        rejected("releases type", {"releases": {}})
        rejected("too many releases", {"releases": [release([2 + item]) for item in range(21)]})
        rejected("release fields", {"releases": [{**release([1]), "extra": True}]})
        rejected("empty release name", {"releases": [release([1], name="")]})
        rejected("wrong track", {"releases": [release([1], track="internal")]})
        rejected(
            "artifacts type",
            {"releases": [{**release([1]), "activeArtifacts": {"versionCode": 1}}]},
        )
        rejected("empty artifacts", {"releases": [release([])]})
        rejected(
            "artifact fields",
            {
                "releases": [
                    {**release([1]), "activeArtifacts": [{"versionCode": 1, "sha256": "x"}]}
                ]
            },
        )
        rejected("boolean version", {"releases": [release([True])]})
        rejected("zero version", {"releases": [release([0])]})
        rejected("oversized version", {"releases": [release([MODULE.MAX_VERSION_CODE + 1])]})
        rejected("duplicate artifact version", {"releases": [release([1, 1])]})
        rejected("missing expected version", {"releases": [release([2])]})
        rejected("duplicate matching release", {"releases": [release([1]), release([1], name="again")]})
        rejected(
            "unspecified lifecycle",
            {
                "releases": [
                    release([1], lifecycle="RELEASE_LIFECYCLE_STATE_UNSPECIFIED")
                ]
            },
        )
        rejected(
            "matching draft",
            {"releases": [release([1], lifecycle="RELEASE_LIFECYCLE_STATE_DRAFT")]},
        )
        rejected(
            "matching review",
            {"releases": [release([1], lifecycle="RELEASE_LIFECYCLE_STATE_IN_REVIEW")]},
        )

        rejected_track("track root", [track_release(["1"])])
        rejected_track("wrong track resource", {"track": "internal", "releases": [track_release(["1"])]})
        rejected_track("track envelope fields", {"track": "qa", "releases": [track_release(["1"])], "extra": True})
        rejected_track("empty track releases", {"track": "qa", "releases": []})
        rejected_track("track release fields", {"track": "qa", "releases": [{**track_release(["1"]), "extra": True}]})
        rejected_track("missing track status", {"track": "qa", "releases": [{"versionCodes": ["1"]}]})
        rejected_track("integer track version", {"track": "qa", "releases": [track_release([1])]})
        rejected_track("leading-zero track version", {"track": "qa", "releases": [track_release(["01"])]})
        rejected_track("duplicate track version", {"track": "qa", "releases": [track_release(["1", "1"])]})
        rejected_track("missing target track version", {"track": "qa", "releases": [track_release(["2"])]})
        rejected_track("duplicate target track release", {"track": "qa", "releases": [track_release(["1"]), track_release(["1"], name="again")]})
        rejected_track("draft track release", {"track": "qa", "releases": [track_release(["1"], status="draft")]})
        rejected_track("halted track release", {"track": "qa", "releases": [track_release(["1"], status="halted")]})
        rejected_track("unspecified track status", {"track": "qa", "releases": [track_release(["1"], status="statusUnspecified")]})
        rejected_track("qa staged fraction", {"track": "qa", "releases": [track_release(["1"], userFraction=0.5)]})
        rejected_track("qa country targeting", {"track": "qa", "releases": [track_release(["1"], countryTargeting={"countries": ["JP"]})]})
        rejected_track("bad release note", {"track": "qa", "releases": [track_release(["1"], releaseNotes=[{"language": "ja-JP"}])]})
        rejected_track("bad update priority", {"track": "qa", "releases": [track_release(["1"], inAppUpdatePriority=6)]})

        missing_upload = rejected_root / "missing-upload.json"
        expect_failure(
            "missing C114 receipt",
            lambda: MODULE.verify_play_internal_track_receipt(
                target,
                bundle_response,
                missing_upload,
                release_list_response,
                track_response,
                rejected_root / "missing-upload" / output_name,
                root,
            ),
        )
        upload_symlink = rejected_root / "upload-symlink.json"
        upload_symlink.symlink_to(upload_receipt)
        expect_failure(
            "C114 receipt symlink",
            lambda: MODULE.verify_play_internal_track_receipt(
                target,
                bundle_response,
                upload_symlink,
                release_list_response,
                track_response,
                rejected_root / "upload-symlink" / output_name,
                root,
            ),
        )
        tampered_upload = rejected_root / "tampered-upload" / upload_name
        tampered_upload.parent.mkdir(parents=True)
        tampered_upload.write_text("{}\n", encoding="utf-8")
        expect_failure(
            "tampered C114 receipt",
            lambda: MODULE.verify_play_internal_track_receipt(
                target,
                bundle_response,
                tampered_upload,
                release_list_response,
                track_response,
                rejected_root / "tampered-upload-output" / output_name,
                root,
            ),
        )
        wrong_bundle = write_json(
            rejected_root / "wrong-bundle.json",
            {"versionCode": 1, "sha1": "a" * 40, "sha256": "f" * 64},
        )
        expect_failure(
            "bundle response drift",
            lambda: MODULE.verify_play_internal_track_receipt(
                target,
                wrong_bundle,
                upload_receipt,
                release_list_response,
                track_response,
                rejected_root / "wrong-bundle" / output_name,
                root,
            ),
        )

        rejected("wrong output name", {"releases": [release([1])]}, receipt_output=rejected_root / "wrong-name.json")
        rejected("inside handoff", {"releases": [release([1])]}, receipt_output=target / output_name)
        real_parent = rejected_root / "real-parent"
        real_parent.mkdir(parents=True)
        parent_symlink = rejected_root / "parent-symlink"
        parent_symlink.symlink_to(real_parent, target_is_directory=True)
        rejected(
            "output parent symlink",
            {"releases": [release([1])]},
            receipt_output=parent_symlink / output_name,
        )
        conflict = rejected_root / "conflict" / output_name
        conflict.parent.mkdir(parents=True)
        conflict.write_text("conflict\n", encoding="utf-8")
        rejected("existing conflict", {"releases": [release([1])]}, receipt_output=conflict)

        tampered_target = rejected_root / "tampered-handoff" / target.name
        tampered_target.parent.mkdir(parents=True)
        shutil.copytree(target, tampered_target)
        next(tampered_target.glob("*.aab")).write_bytes(b"tampered")
        expect_failure(
            "tampered handoff",
            lambda: MODULE.verify_play_internal_track_receipt(
                tampered_target,
                bundle_response,
                upload_receipt,
                release_list_response,
                track_response,
                rejected_root / "tampered-handoff-output" / output_name,
                root,
            ),
        )

        oversized = rejected_root / "oversized.json"
        oversized.write_bytes(b" " * (MODULE.MAX_RESPONSE_BYTES + 1))
        expect_failure(
            "oversized track response",
            lambda: MODULE.verify_play_internal_track_receipt(
                target,
                bundle_response,
                upload_receipt,
                oversized,
                track_response,
                rejected_root / "oversized" / output_name,
                root,
            ),
        )
        assert not list(root.rglob("*.tmp"))

    print("Play Internal track receipt contract passed (4 accepted/idempotent, 48 rejected).")


if __name__ == "__main__":
    main()
