#!/usr/bin/env python3
"""Fail-closed security/privacy policy for the merged Android Release manifest."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ANDROID_URI = "http://schemas.android.com/apk/res/android"
ANDROID = f"{{{ANDROID_URI}}}"
PACKAGE = "com.afterlifearchive.medmanager"


class PolicyError(RuntimeError):
    pass


def android_attr(element: ET.Element, name: str) -> str | None:
    return element.get(f"{ANDROID}{name}")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise PolicyError(message)


def component_name(element: ET.Element) -> str:
    name = android_attr(element, "name") or ""
    if name.startswith("."):
        return f"{PACKAGE}{name}"
    if "." not in name:
        return f"{PACKAGE}.{name}"
    return name


def verify_manifest_text(xml_text: str) -> dict[str, int | str]:
    try:
        manifest = ET.fromstring(xml_text)
    except ET.ParseError as error:
        raise PolicyError(f"Merged manifest is not valid XML: {error}") from error

    require(manifest.tag == "manifest", "Expected a manifest root element")
    require(manifest.get("package") == PACKAGE, "Unexpected manifest package")
    require((android_attr(manifest, "versionCode") or "").isdigit(), "versionCode must be numeric")
    require(bool(android_attr(manifest, "versionName")), "versionName must be present")

    applications = manifest.findall("application")
    require(len(applications) == 1, "Expected exactly one application element")
    application = applications[0]
    require(
        android_attr(application, "name") == f"{PACKAGE}.MedicationApplication",
        "Unexpected application class",
    )
    require(android_attr(application, "allowBackup") == "false", "Release backup must be disabled")
    require(
        bool(android_attr(application, "fullBackupContent")),
        "Legacy full-backup exclusion rules must be present",
    )
    require(
        bool(android_attr(application, "dataExtractionRules")),
        "Cloud/device-transfer exclusion rules must be present",
    )
    require(
        android_attr(application, "usesCleartextTraffic") == "false",
        "Cleartext traffic must be disabled",
    )
    require(
        android_attr(application, "extractNativeLibs") == "false",
        "Release native libraries must remain uncompressed for alignment verification",
    )
    require(android_attr(application, "debuggable") in {None, "false"}, "Release must not be debuggable")
    require(android_attr(application, "testOnly") in {None, "false"}, "Release must not be testOnly")
    for profileable in application.findall("profileable"):
        require(
            android_attr(profileable, "shell") in {None, "false"},
            "Shell profiling must be disabled in Release",
        )

    metadata: dict[str, list[str | None]] = {}
    for item in application.findall("meta-data"):
        name = android_attr(item, "name")
        if name:
            metadata.setdefault(name, []).append(android_attr(item, "value"))
    for name in (
        "firebase_analytics_collection_enabled",
        "firebase_messaging_auto_init_enabled",
    ):
        require(metadata.get(name) == ["false"], f"{name} must exist exactly once and be false")

    expected_permissions = {
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.WAKE_LOCK",
        "android.permission.POST_NOTIFICATIONS",
        "com.google.android.c2dm.permission.RECEIVE",
        f"{PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    }
    permission_items = manifest.findall("uses-permission") + manifest.findall("uses-permission-sdk-23")
    permission_names = [android_attr(item, "name") or "" for item in permission_items]
    require(len(permission_names) == len(set(permission_names)), "Duplicate uses-permission entry")
    actual_permissions = set(permission_names)
    unexpected_permissions = sorted(actual_permissions - expected_permissions)
    missing_permissions = sorted(expected_permissions - actual_permissions)
    require(not unexpected_permissions, f"Unexpected Release permissions: {', '.join(unexpected_permissions)}")
    require(not missing_permissions, f"Missing required Release permissions: {', '.join(missing_permissions)}")

    custom_permission = [
        item
        for item in manifest.findall("permission")
        if android_attr(item, "name") == f"{PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    ]
    require(len(custom_permission) == 1, "Dynamic receiver signature permission must be declared once")
    require(
        android_attr(custom_permission[0], "protectionLevel") in {"signature", "0x2"},
        "Dynamic receiver permission must use signature protection",
    )

    component_tags = ("activity", "activity-alias", "service", "receiver", "provider")
    exported: dict[str, tuple[str, str | None]] = {}
    components: dict[str, list[ET.Element]] = {}
    for tag in component_tags:
        for component in application.findall(tag):
            name = component_name(component)
            components.setdefault(name, []).append(component)
            if android_attr(component, "exported") == "true":
                exported[name] = (tag, android_attr(component, "permission"))
    duplicate_components = sorted(name for name, matches in components.items() if len(matches) != 1)
    require(not duplicate_components, f"Duplicate merged components: {', '.join(duplicate_components)}")

    expected_exported = {
        f"{PACKAGE}.MainActivity": ("activity", None),
        "com.google.firebase.iid.FirebaseInstanceIdReceiver": (
            "receiver",
            "com.google.android.c2dm.permission.SEND",
        ),
        "androidx.profileinstaller.ProfileInstallReceiver": (
            "receiver",
            "android.permission.DUMP",
        ),
    }
    require(exported == expected_exported, f"Unexpected exported component contract: {exported}")

    def single_component(name: str, tag: str) -> ET.Element:
        matches = components.get(name, [])
        require(len(matches) == 1, f"Expected exactly one {name} component")
        require(matches[0].tag == tag, f"Unexpected component type for {name}")
        return matches[0]

    reminder = single_component(f"{PACKAGE}.ReminderReceiver", "receiver")
    require(android_attr(reminder, "exported") == "false", "ReminderReceiver must not be exported")
    messaging = single_component(f"{PACKAGE}.CaregiverFirebaseMessagingService", "service")
    require(android_attr(messaging, "exported") == "false", "Caregiver FCM service must not be exported")
    file_provider = single_component("androidx.core.content.FileProvider", "provider")
    require(android_attr(file_provider, "exported") == "false", "FileProvider must not be exported")
    require(android_attr(file_provider, "grantUriPermissions") == "true", "FileProvider URI grants must be explicit")
    require(
        android_attr(file_provider, "authorities") == f"{PACKAGE}.fileprovider",
        "Unexpected FileProvider authority",
    )

    main_activity = single_component(f"{PACKAGE}.MainActivity", "activity")
    link_contract: set[tuple[str, str, str]] = set()
    launcher_filters = 0
    for intent_filter in main_activity.findall("intent-filter"):
        actions = {android_attr(item, "name") or "" for item in intent_filter.findall("action")}
        categories = {android_attr(item, "name") or "" for item in intent_filter.findall("category")}
        filter_links: list[tuple[str, str, str]] = []
        for data in intent_filter.findall("data"):
            scheme = android_attr(data, "scheme") or ""
            host = android_attr(data, "host") or ""
            path = android_attr(data, "path") or android_attr(data, "pathPrefix") or ""
            if scheme or host or path:
                filter_links.append((scheme, host, path))
        if "android.intent.action.MAIN" in actions:
            launcher_filters += 1
            require(categories == {"android.intent.category.LAUNCHER"}, "Launcher category contract drifted")
            require(not filter_links, "Launcher filter must not expose URI data")
        if filter_links:
            require(actions == {"android.intent.action.VIEW"}, "Authentication links must use only ACTION_VIEW")
            require(
                categories == {"android.intent.category.DEFAULT", "android.intent.category.BROWSABLE"},
                "Authentication links must remain DEFAULT and BROWSABLE",
            )
        if any(link[0] == "https" for link in filter_links):
            require(android_attr(intent_filter, "autoVerify") == "true", "HTTPS App Links must use autoVerify")
        link_contract.update(filter_links)
    require(launcher_filters == 1, "Expected exactly one launcher intent filter")
    expected_links = {
        ("https", "okusuri-mimamori.com", "/auth/"),
        ("https", "www.okusuri-mimamori.com", "/auth/"),
        ("okusurimimamori", "auth", "/login"),
    }
    require(link_contract == expected_links, f"Unexpected authentication link surface: {sorted(link_contract)}")

    return {
        "package": PACKAGE,
        "permissions": len(actual_permissions),
        "exported_components": len(exported),
        "authentication_links": len(link_contract),
    }


def verify_manifest(path: Path) -> dict[str, int | str]:
    return verify_manifest_text(path.read_text(encoding="utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path, help="XML emitted by apkanalyzer manifest print")
    arguments = parser.parse_args()
    try:
        summary = verify_manifest(arguments.manifest)
    except (OSError, PolicyError) as error:
        print(f"Release manifest policy failed: {error}", file=sys.stderr)
        return 1
    print("Release manifest security/privacy policy verification passed.")
    print(
        f"package={summary['package']} permissions={summary['permissions']} "
        f"exported={summary['exported_components']} authLinks={summary['authentication_links']}",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
