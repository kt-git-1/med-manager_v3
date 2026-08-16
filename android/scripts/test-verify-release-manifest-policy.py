#!/usr/bin/env python3
"""Synthetic contract tests for the Release merged-manifest policy."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("verify-release-manifest-policy.py")
SPEC = importlib.util.spec_from_file_location("release_manifest_policy", SCRIPT)
assert SPEC and SPEC.loader
POLICY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(POLICY)


VALID_MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.afterlifearchive.medmanager"
    android:versionCode="1" android:versionName="1.0.6">
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
  <uses-permission android:name="android.permission.WAKE_LOCK" />
  <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
  <uses-permission android:name="com.google.android.c2dm.permission.RECEIVE" />
  <permission android:name="com.afterlifearchive.medmanager.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
      android:protectionLevel="0x2" />
  <uses-permission android:name="com.afterlifearchive.medmanager.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
  <application android:name="com.afterlifearchive.medmanager.MedicationApplication"
      android:allowBackup="false" android:fullBackupContent="@xml/backup_rules"
      android:dataExtractionRules="@xml/data_extraction_rules" android:usesCleartextTraffic="false"
      android:extractNativeLibs="false">
    <meta-data android:name="firebase_analytics_collection_enabled" android:value="false" />
    <meta-data android:name="firebase_messaging_auto_init_enabled" android:value="false" />
    <activity android:name="com.afterlifearchive.medmanager.MainActivity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
      <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="okusuri-mimamori.com" android:pathPrefix="/auth/" />
        <data android:scheme="https" android:host="www.okusuri-mimamori.com" android:pathPrefix="/auth/" />
      </intent-filter>
      <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="okusurimimamori" android:host="auth" android:path="/login" />
      </intent-filter>
    </activity>
    <receiver android:name="com.afterlifearchive.medmanager.ReminderReceiver" android:exported="false" />
    <service android:name="com.afterlifearchive.medmanager.CaregiverFirebaseMessagingService"
        android:exported="false" />
    <provider android:name="androidx.core.content.FileProvider" android:exported="false"
        android:authorities="com.afterlifearchive.medmanager.fileprovider"
        android:grantUriPermissions="true" />
    <receiver android:name="com.google.firebase.iid.FirebaseInstanceIdReceiver"
        android:permission="com.google.android.c2dm.permission.SEND" android:exported="true" />
    <receiver android:name="androidx.profileinstaller.ProfileInstallReceiver"
        android:permission="android.permission.DUMP" android:exported="true" />
  </application>
</manifest>
"""


class ReleaseManifestPolicyTests(unittest.TestCase):
    def assert_rejected(self, xml: str, expected: str) -> None:
        with self.assertRaisesRegex(POLICY.PolicyError, expected):
            POLICY.verify_manifest_text(xml)

    def test_approved_contract_passes(self) -> None:
        summary = POLICY.verify_manifest_text(VALID_MANIFEST)
        self.assertEqual(summary["permissions"], 6)
        self.assertEqual(summary["exported_components"], 3)
        self.assertEqual(summary["authentication_links"], 3)

    def test_bundletool_padded_signature_value_passes(self) -> None:
        POLICY.verify_manifest_text(
            VALID_MANIFEST.replace(
                'android:protectionLevel="0x2"',
                'android:protectionLevel="0x00000002"',
            ),
        )

    def test_debuggable_release_is_rejected(self) -> None:
        self.assert_rejected(
            VALID_MANIFEST.replace("android:allowBackup", 'android:debuggable="true" android:allowBackup'),
            "must not be debuggable",
        )

    def test_test_only_or_shell_profileable_release_is_rejected(self) -> None:
        self.assert_rejected(
            VALID_MANIFEST.replace("android:allowBackup", 'android:testOnly="true" android:allowBackup'),
            "testOnly",
        )
        self.assert_rejected(
            VALID_MANIFEST.replace("  </application>", '    <profileable android:shell="true" />\n  </application>'),
            "Shell profiling",
        )

    def test_backup_or_cleartext_relaxation_is_rejected(self) -> None:
        self.assert_rejected(VALID_MANIFEST.replace('android:allowBackup="false"', 'android:allowBackup="true"'), "backup")
        self.assert_rejected(
            VALID_MANIFEST.replace('android:usesCleartextTraffic="false"', 'android:usesCleartextTraffic="true"'),
            "Cleartext",
        )
        self.assert_rejected(
            VALID_MANIFEST.replace(' android:fullBackupContent="@xml/backup_rules"', ""),
            "full-backup",
        )

    def test_firebase_auto_collection_is_rejected(self) -> None:
        self.assert_rejected(
            VALID_MANIFEST.replace(
                'android:name="firebase_analytics_collection_enabled" android:value="false"',
                'android:name="firebase_analytics_collection_enabled" android:value="true"',
            ),
            "firebase_analytics_collection_enabled",
        )

    def test_unreviewed_permission_is_rejected(self) -> None:
        self.assert_rejected(
            VALID_MANIFEST.replace(
                "  <application",
                '  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />\n  <application',
            ),
            "Unexpected Release permissions",
        )

    def test_unreviewed_exported_component_is_rejected(self) -> None:
        self.assert_rejected(
            VALID_MANIFEST.replace(
                "  </application>",
                '    <service android:name="com.example.Exposed" android:exported="true" />\n  </application>',
            ),
            "Unexpected exported component contract",
        )

    def test_firebase_receiver_must_remain_permission_guarded(self) -> None:
        self.assert_rejected(
            VALID_MANIFEST.replace('android:permission="com.google.android.c2dm.permission.SEND" ', ""),
            "Unexpected exported component contract",
        )

    def test_profile_receiver_must_remain_dump_guarded(self) -> None:
        self.assert_rejected(
            VALID_MANIFEST.replace('android:permission="android.permission.DUMP" ', ""),
            "Unexpected exported component contract",
        )

    def test_unreviewed_authentication_host_is_rejected(self) -> None:
        self.assert_rejected(
            VALID_MANIFEST.replace(
                '        <data android:scheme="https" android:host="okusuri-mimamori.com"',
                '        <data android:scheme="https" android:host="evil.example" android:pathPrefix="/auth/" />\n'
                '        <data android:scheme="https" android:host="okusuri-mimamori.com"',
            ),
            "Unexpected authentication link surface",
        )

    def test_authentication_link_must_remain_browsable(self) -> None:
        self.assert_rejected(
            VALID_MANIFEST.replace(
                '<category android:name="android.intent.category.BROWSABLE" />',
                "",
                1,
            ),
            "DEFAULT and BROWSABLE",
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
