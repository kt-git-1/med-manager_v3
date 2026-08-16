#!/usr/bin/env python3
"""Fail-closed verifier for the production Android Digital Asset Links surface."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from collections.abc import Mapping, Sequence
from dataclasses import dataclass


PRODUCTION_ASSETLINKS_URL = (
    "https://www.okusuri-mimamori.com/.well-known/assetlinks.json"
)
ANDROID_PACKAGE_NAME = "com.afterlifearchive.medmanager"
HANDLE_ALL_URLS_RELATION = "delegate_permission/common.handle_all_urls"
MAX_DOCUMENT_BYTES = 64 * 1024
MAX_CACHE_AGE_SECONDS = 300
_COLON_FINGERPRINT = re.compile(r"^(?:[0-9A-F]{2}:){31}[0-9A-F]{2}$")
_HEX_FINGERPRINT = re.compile(r"^[0-9A-Fa-f]{64}$")


class ContractError(ValueError):
    """Raised when the public association surface violates the release contract."""


@dataclass(frozen=True)
class HttpSurface:
    status: int
    headers: Mapping[str, str]
    body: bytes


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, file_pointer, code, message, headers, new_url):
        return None


def normalize_fingerprint(value: str) -> str:
    compact = re.sub(r"[:\s]", "", value)
    if not _HEX_FINGERPRINT.fullmatch(compact):
        raise ContractError("expected app-signing fingerprint must contain exactly 32 SHA-256 bytes")
    upper = compact.upper()
    return ":".join(upper[index : index + 2] for index in range(0, 64, 2))


def parse_expected_fingerprints(value: str) -> tuple[str, ...]:
    raw_values = [item.strip() for item in value.split(",") if item.strip()]
    if not raw_values:
        raise ContractError("at least one Play app-signing SHA-256 fingerprint is required")
    fingerprints = tuple(normalize_fingerprint(item) for item in raw_values)
    if len(set(fingerprints)) != len(fingerprints):
        raise ContractError("expected app-signing fingerprints must not contain duplicates")
    return fingerprints


def _header(headers: Mapping[str, str], name: str) -> str:
    for key, value in headers.items():
        if key.lower() == name.lower():
            return value
    return ""


def _require_exact_keys(value: Mapping[str, object], expected: set[str], label: str) -> None:
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        extra = sorted(actual - expected)
        details = []
        if missing:
            details.append(f"missing={','.join(missing)}")
        if extra:
            details.append(f"extra={','.join(extra)}")
        raise ContractError(f"{label} fields are not exact ({'; '.join(details)})")


def validate_surface(surface: HttpSurface, expected_fingerprints: Sequence[str]) -> None:
    if surface.status != 200:
        raise ContractError(f"production endpoint returned HTTP {surface.status}, expected 200")
    if len(surface.body) > MAX_DOCUMENT_BYTES:
        raise ContractError(f"assetlinks document exceeds {MAX_DOCUMENT_BYTES} bytes")

    content_type = _header(surface.headers, "Content-Type").split(";", 1)[0].strip().lower()
    if content_type != "application/json":
        raise ContractError("Content-Type must be application/json")
    if _header(surface.headers, "Cache-Control").strip().lower() != "public, max-age=300":
        raise ContractError("Cache-Control must be exactly public, max-age=300")

    try:
        document = json.loads(surface.body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ContractError("response body must be valid UTF-8 JSON") from error

    if not isinstance(document, list) or len(document) != 1:
        raise ContractError("assetlinks document must contain exactly one association statement")
    statement = document[0]
    if not isinstance(statement, dict):
        raise ContractError("association statement must be an object")
    _require_exact_keys(statement, {"relation", "target"}, "association statement")

    if statement["relation"] != [HANDLE_ALL_URLS_RELATION]:
        raise ContractError("relation must grant only handle_all_urls")
    target = statement["target"]
    if not isinstance(target, dict):
        raise ContractError("association target must be an object")
    _require_exact_keys(
        target,
        {"namespace", "package_name", "sha256_cert_fingerprints"},
        "association target",
    )
    if target["namespace"] != "android_app":
        raise ContractError("target namespace must be android_app")
    if target["package_name"] != ANDROID_PACKAGE_NAME:
        raise ContractError(f"target package must be {ANDROID_PACKAGE_NAME}")

    fingerprints = target["sha256_cert_fingerprints"]
    if not isinstance(fingerprints, list) or not fingerprints:
        raise ContractError("target must contain at least one certificate fingerprint")
    if not all(isinstance(item, str) and _COLON_FINGERPRINT.fullmatch(item) for item in fingerprints):
        raise ContractError("published fingerprints must be uppercase colon-separated SHA-256 values")
    if len(set(fingerprints)) != len(fingerprints):
        raise ContractError("published fingerprints must not contain duplicates")
    if set(fingerprints) != set(expected_fingerprints):
        raise ContractError("published fingerprints do not exactly match the Play app-signing set")


def fetch_surface(url: str, timeout_seconds: float = 15.0) -> HttpSurface:
    opener = urllib.request.build_opener(_NoRedirect())
    request = urllib.request.Request(
        url,
        headers={"Accept": "application/json", "User-Agent": "med-manager-app-links-verifier/1"},
    )
    try:
        with opener.open(request, timeout=timeout_seconds) as response:
            body = response.read(MAX_DOCUMENT_BYTES + 1)
            return HttpSurface(response.status, dict(response.headers.items()), body)
    except urllib.error.HTTPError as error:
        body = error.read(MAX_DOCUMENT_BYTES + 1)
        return HttpSurface(error.code, dict(error.headers.items()), body)
    except urllib.error.URLError as error:
        reason = type(error.reason).__name__
        raise ContractError(f"production endpoint could not be reached ({reason})") from error


def _arguments(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify the exact production Digital Asset Links HTTP and JSON contract."
    )
    parser.add_argument("--url", default=PRODUCTION_ASSETLINKS_URL)
    parser.add_argument(
        "--expected-sha256",
        default=os.environ.get("EXPECTED_APP_SIGNING_CERT_SHA256_FINGERPRINTS", ""),
        help="Play app-signing SHA-256 fingerprint(s), comma-separated; defaults to the environment.",
    )
    parser.add_argument("--timeout-seconds", type=float, default=15.0)
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    arguments = _arguments(argv or sys.argv[1:])
    try:
        if arguments.url != PRODUCTION_ASSETLINKS_URL:
            raise ContractError(f"URL must be exactly {PRODUCTION_ASSETLINKS_URL}")
        if arguments.timeout_seconds <= 0 or arguments.timeout_seconds > 60:
            raise ContractError("timeout must be greater than 0 and no more than 60 seconds")
        expected = parse_expected_fingerprints(arguments.expected_sha256)
        surface = fetch_surface(arguments.url, arguments.timeout_seconds)
        validate_surface(surface, expected)
    except ContractError as error:
        print(f"Production App Links verification failed: {error}", file=sys.stderr)
        return 1

    print(
        "Production App Links verified: "
        f"url={PRODUCTION_ASSETLINKS_URL} package={ANDROID_PACKAGE_NAME} "
        f"app_signing_certificates={len(expected)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
