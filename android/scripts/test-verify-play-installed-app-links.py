#!/usr/bin/env python3
"""Pure contract for the Play-installed App Links verifier."""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path


SCRIPT = Path(__file__).with_name("verify-play-installed-app-links.py")
SPEC = importlib.util.spec_from_file_location("verify_play_installed_app_links", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

FINGERPRINT = ":".join(f"{index:02X}" for index in range(32))
OTHER_FINGERPRINT = ":".join(f"{index:02X}" for index in range(32, 64))


def state(*, package=MODULE.PACKAGE, signatures=None, domains=None):
    signatures = [FINGERPRINT] if signatures is None else signatures
    domains = {MODULE.DOMAIN: "verified"} if domains is None else domains
    domain_rows = "\n".join(f"      {host}: {value}" for host, value in domains.items())
    return (
        f"  {package}:\n"
        "    ID: 00000000-0000-0000-0000-000000000000\n"
        f"    Signatures: [{', '.join(signatures)}]\n"
        "    Domain verification state:\n"
        f"{domain_rows}\n"
        "  User 0:\n"
        "    Verification link handling allowed: true\n"
    )


def rejected(label, operation):
    try:
        operation()
    except MODULE.WEB.ContractError:
        return
    raise AssertionError(f"Rejected fixture unexpectedly passed: {label}")


def main():
    MODULE.validate_installer(f"package:{MODULE.PACKAGE}  installer={MODULE.PLAY_INSTALLER}\n")
    MODULE.validate_app_links_state(state(), (FINGERPRINT,))
    MODULE.validate_app_links_state(
        state(signatures=[OTHER_FINGERPRINT, FINGERPRINT]),
        (FINGERPRINT, OTHER_FINGERPRINT),
    )

    invalid = [
        ("non-Play installer", lambda: MODULE.validate_installer(f"package:{MODULE.PACKAGE}  installer=null")),
        ("missing package", lambda: MODULE.validate_installer("")),
        ("wrong package", lambda: MODULE.validate_app_links_state(state(package="com.example.other"), (FINGERPRINT,))),
        ("missing state", lambda: MODULE.validate_app_links_state(f"  {MODULE.PACKAGE}:\n", (FINGERPRINT,))),
        ("empty signatures", lambda: MODULE.validate_app_links_state(state(signatures=[]), (FINGERPRINT,))),
        ("wrong signer", lambda: MODULE.validate_app_links_state(state(signatures=[OTHER_FINGERPRINT]), (FINGERPRINT,))),
        ("lowercase signer", lambda: MODULE.validate_app_links_state(state(signatures=[FINGERPRINT.lower()]), (FINGERPRINT,))),
        ("duplicate signer", lambda: MODULE.validate_app_links_state(state(signatures=[FINGERPRINT, FINGERPRINT]), (FINGERPRINT,))),
        ("pending domain", lambda: MODULE.validate_app_links_state(state(domains={MODULE.DOMAIN: "none"}), (FINGERPRINT,))),
        ("failed domain", lambda: MODULE.validate_app_links_state(state(domains={MODULE.DOMAIN: "legacy_failure"}), (FINGERPRINT,))),
        ("empty domains", lambda: MODULE.validate_app_links_state(state(domains={}), (FINGERPRINT,))),
        ("wrong domain", lambda: MODULE.validate_app_links_state(state(domains={"example.com": "verified"}), (FINGERPRINT,))),
        ("unpublished apex", lambda: MODULE.validate_app_links_state(state(domains={MODULE.DOMAIN: "verified", "okusuri-mimamori.com": "verified"}), (FINGERPRINT,))),
    ]
    for label, operation in invalid:
        rejected(label, operation)

    print(f"Play-installed App Links verifier contract passed: accepted=3 rejected={len(invalid)}")


if __name__ == "__main__":
    main()
