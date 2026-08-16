#!/usr/bin/env python3

import importlib.util
import json
from pathlib import Path
import sys
import tempfile


sys.dont_write_bytecode = True
SCRIPT_PATH = Path(__file__).with_name("prepare-play-release-handoff.py")
SPEC = importlib.util.spec_from_file_location("prepare_play_release_handoff", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def valid_report(aab: Path) -> dict[str, object]:
    return {
        "schemaVersion": 1,
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
        report = valid_report(aab)
        evidence.write_text(json.dumps(report), encoding="utf-8")

        target = MODULE.prepare_handoff(aab, evidence, output_root)
        assert target.name == "v1.0.6-code1-" + "b" * 12
        aab_name = f"med-manager-android-{target.name}.aab"
        assert {entry.name for entry in target.iterdir()} == {
            aab_name,
            "play-release-evidence.json",
            "SHA256SUMS",
        }
        assert MODULE.prepare_handoff(aab, evidence, output_root) == target
        assert not list(output_root.glob(".handoff-*"))

        original_aab = aab.read_bytes()
        aab.write_bytes(b"tampered")
        expect_failure("Tampered source AAB", lambda: MODULE.prepare_handoff(aab, evidence, root / "tampered"))
        aab.write_bytes(original_aab)

        for label, mutate in (
            ("Dirty source", lambda value: value["source"].update(releaseInputTreeClean=False)),
            ("Wrong application", lambda value: value["application"].update(applicationId="com.example.debug")),
            ("Malformed signer", lambda value: value["artifact"].update(uploadCertificateSha256="bad")),
            ("Feature module", lambda value: value["artifact"].update(modules=["base", "feature"])),
            ("Missing gate", lambda value: value.update(verifiedGates=list(MODULE.EXPECTED_GATES[:-1]))),
        ):
            fixture = valid_report(aab)
            mutate(fixture)
            fixture_path = root / f"{label.replace(' ', '-').lower()}.json"
            fixture_path.write_text(json.dumps(fixture), encoding="utf-8")
            expect_failure(label, lambda path=fixture_path: MODULE.prepare_handoff(aab, path, root / path.stem))

        (target / aab_name).write_bytes(b"tampered packaged AAB")
        expect_failure("Tampered existing handoff", lambda: MODULE.prepare_handoff(aab, evidence, output_root))

    print("Play release handoff contract passed (1 accepted/idempotent, 7 rejected).")


if __name__ == "__main__":
    main()
