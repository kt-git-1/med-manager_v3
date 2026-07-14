# A03 Installation and Session Safety Evidence — 2026-07-14

**Branch:** `android-dev`
**Baseline:** `main@1d9d19e`
**Device:** `MedicationApp_API_35`, Android 15 / API 35 emulator

## Policy review

Android's official Auto Backup documentation warns that on Android 12+ some manufacturers may still perform device-to-device transfer when only `android:allowBackup="false"` is declared. It also requires the legacy `fullBackupContent` rules for Android 11 and lower and the `dataExtractionRules` format for Android 12 and higher: <https://developer.android.com/identity/data/autobackup>

The app therefore uses all three layers:

1. `android:allowBackup="false"`.
2. Explicit all-domain exclusion for legacy backup, cloud backup and device transfer.
3. A marker in `noBackupFilesDir`; missing marker clears restored mode, patient selection and encrypted session preferences before they can be read.

The third layer fails closed even if an OEM transfers ordinary shared preferences despite the manifest policy. The AES-GCM key remains Android Keystore-backed and is not stored in the preference payload.

## Automated evidence

`connectedDebugAndroidTest` ran 24 tests successfully, including:

- encrypted value is not plaintext at rest;
- a new storage/repository instance restores an active installation;
- expired patient credentials are rejected;
- loss of the no-backup installation marker clears simulated restored session data.

## Emulator lifecycle evidence

An active patient-mode installation was established, force-stopped and cold-launched:

```text
BEFORE_FORCE_STOP: text="連携コード"
AFTER_FORCE_STOP: text="連携コード"
```

Backup Manager rejected the package independently of the emulator's disabled transport state:

```text
BACKUP_ENABLED: Backup Manager currently disabled
Running incremental backup for 1 requested packages.
Backup finished with result: Backup is not allowed
Unable to run backup
```

After a normal uninstall followed by installation of the same debug APK, the app returned to mode selection and did not restore the patient-link route:

```text
Success
AFTER_REINSTALL_MODE: text="本人として使う"
AFTER_REINSTALL_LINK_SCREEN: absent
PACKAGE_FLAGS: flags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ]
```

`ALLOW_BACKUP` is absent from the installed package flags.

## Remaining release evidence

- Repeat force-stop and uninstall/reinstall on a physical device.
- Exercise a real OEM/Google device-to-device setup transfer where available.
- Confirm the signed internal/closed-track build has the same package flags and resource rules.
