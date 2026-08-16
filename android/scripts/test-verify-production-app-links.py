#!/usr/bin/env python3
"""Pure acceptance/rejection contract for verify-production-app-links.py."""

from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path


SCRIPT = Path(__file__).with_name("verify-production-app-links.py")
SPEC = importlib.util.spec_from_file_location("verify_production_app_links", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

FINGERPRINT = ":".join(f"{index:02X}" for index in range(32))
OTHER_FINGERPRINT = ":".join(f"{index:02X}" for index in range(32, 64))


def document(fingerprints=None):
    fingerprints = [FINGERPRINT] if fingerprints is None else fingerprints
    return [
        {
            "relation": [MODULE.HANDLE_ALL_URLS_RELATION],
            "target": {
                "namespace": "android_app",
                "package_name": MODULE.ANDROID_PACKAGE_NAME,
                "sha256_cert_fingerprints": fingerprints,
            },
        }
    ]


def surface(value=None, *, status=200, content_type="application/json", cache_control="public, max-age=300"):
    body = json.dumps(value if value is not None else document(), separators=(",", ":")).encode()
    return MODULE.HttpSurface(
        status=status,
        headers={"Content-Type": content_type, "Cache-Control": cache_control},
        body=body,
    )


def rejected(label, candidate, expected=(FINGERPRINT,)):
    try:
        MODULE.validate_surface(candidate, expected)
    except MODULE.ContractError:
        return
    raise AssertionError(f"Rejected fixture unexpectedly passed: {label}")


def main():
    expected = MODULE.parse_expected_fingerprints(FINGERPRINT.lower().replace(":", ""))
    assert expected == (FINGERPRINT,)
    MODULE.validate_surface(surface(), expected)

    transition = (FINGERPRINT, OTHER_FINGERPRINT)
    MODULE.validate_surface(surface(document(list(reversed(transition)))), transition)

    invalid = []
    invalid.append(("HTTP 404", surface(status=404)))
    invalid.append(("HTML content type", surface(content_type="text/html")))
    invalid.append(("unbounded cache", surface(cache_control="public, max-age=3600")))
    invalid.append(("malformed JSON", MODULE.HttpSurface(200, {"Content-Type": "application/json", "Cache-Control": "public, max-age=300"}, b"[")))
    invalid.append(("multiple statements", surface(document() + document())))

    wrong = document()
    wrong[0]["debug"] = True
    invalid.append(("unexpected statement field", surface(wrong)))
    wrong = document()
    wrong[0]["relation"] = ["delegate_permission/common.get_login_creds"]
    invalid.append(("wrong relation", surface(wrong)))
    wrong = document()
    wrong[0]["target"]["package_name"] = "com.example.other"
    invalid.append(("wrong package", surface(wrong)))
    invalid.append(("lowercase published fingerprint", surface(document([FINGERPRINT.lower()]))))
    invalid.append(("duplicate published fingerprint", surface(document([FINGERPRINT, FINGERPRINT]))))
    invalid.append(("empty published fingerprints", surface(document([]))))
    invalid.append(("wrong signing certificate", surface(document([OTHER_FINGERPRINT]))))
    invalid.append(("oversized response", MODULE.HttpSurface(200, {"Content-Type": "application/json", "Cache-Control": "public, max-age=300"}, b" " * (MODULE.MAX_DOCUMENT_BYTES + 1))))

    for label, candidate in invalid:
        rejected(label, candidate)

    malformed_expected = ("", "AA", FINGERPRINT + ":00")
    for value in malformed_expected:
        try:
            MODULE.parse_expected_fingerprints(value)
        except MODULE.ContractError:
            continue
        raise AssertionError(f"Malformed expected fingerprint unexpectedly passed: {value!r}")
    try:
        MODULE.parse_expected_fingerprints(f"{FINGERPRINT},{FINGERPRINT}")
    except MODULE.ContractError:
        pass
    else:
        raise AssertionError("Duplicate expected fingerprints unexpectedly passed")

    assert MODULE._NoRedirect().redirect_request(None, None, 302, "Found", {}, "https://example.com") is None
    print(f"Production App Links verifier contract passed: accepted=2 rejected={len(invalid) + len(malformed_expected) + 1}")


if __name__ == "__main__":
    main()
