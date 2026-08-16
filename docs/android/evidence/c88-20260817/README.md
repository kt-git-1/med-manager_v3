# C88 merged Release-manifest security/privacy evidence

**Date:** 2026-08-17
**Branch:** `android-dev`
**Source baseline:** published iOS/API `main@432b34c`
**Parity rows:** SH-009 and AU-006 remain implemented; XP-009 and XP-010 remain `PARTIAL`

## Contract

- Parse the merged XML emitted from the actual Release APK, not only the source manifest.
- Reject `debuggable`, `testOnly`, shell-profileable, backup, cleartext or compressed-native-library drift.
- Require legacy backup and current cloud/device-transfer exclusion resources.
- Require Firebase Analytics collection and FCM auto-init to remain manifest-off.
- Allow exactly the six reviewed permissions; reject any new permission until reviewed.
- Allow exactly MainActivity plus the permission-guarded Firebase and Profile Installer receivers as exported components.
- Require app-owned receiver/service/FileProvider components to remain private with the exact authority/grant contract.
- Allow exactly the two production HTTPS auth hosts and one custom auth route with exact VIEW/DEFAULT/BROWSABLE and HTTPS autoVerify semantics.

## Local results

- Synthetic manifest policy: 11/11 pass, including rejection of debug/test/profileable, backup/cleartext, auto-collection, permission, exported component/guard and host/category drift.
- Actual unsigned Release APK: passed.
- Merged manifest: six permissions, three reviewed exported components, three authentication links.
- Known exported dependency receivers remain narrowly permission-guarded: Firebase by `com.google.android.c2dm.permission.SEND`; AndroidX Profile Installer by `android.permission.DUMP`.
- Debug JVM: 216/216 passed.
- Release JVM: 213/213 passed.
- `lintDebug`, Release SDK policy, application ID/SDK, advertising exclusions, 16 KB ZIP/native alignment and Play assets: passed.

## Deliberately incomplete external evidence

- The inspected artifact is the current unsigned Release APK, not the release-owner-signed Play AAB.
- Play App Signing, exact signed-AAB manifest/scan, installed App Links and Console declarations remain required.
