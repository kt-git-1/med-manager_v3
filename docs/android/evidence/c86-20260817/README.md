# C86 Release SDK and Data safety policy evidence

**Date:** 2026-08-17
**Branch:** `android-dev`
**Source baseline:** published iOS/API `main@432b34c`
**Parity row:** XP-009 remains `PARTIAL`

## Contract

- Resolve the actual `releaseRuntimeClasspath`, not only direct dependency declarations.
- Require the approved Firebase Analytics, Cloud Messaging and Installations capabilities.
- Fail closed on unapproved advertising, billing, Install Referrer, crash/performance, attribution and external analytics SDKs.
- Record Firebase Analytics' known advertising-ID and Privacy Sandbox support transitives without treating their names as proof of advertising use.
- Independently reject advertising/attribution permissions from the exact Release APK.
- Require the policy from Release APK compatibility, `bundleSignedRelease` and hosted Android CI.

## Local results

- `verifyReleaseSdkPolicyContract`: passed allowed and forbidden synthetic coordinates.
- `verifyReleaseSdkPolicy`: passed 175 resolved modules and generated a sorted local inventory.
- Required capabilities present: Firebase Analytics, Firebase Cloud Messaging and Firebase Installations.
- Known Firebase Analytics transitives recorded: `play-services-ads-identifier`, `ads-adservices`, `ads-adservices-java`.
- Unapproved SDK groups: absent.
- Debug JVM: 216/216 passed.
- Release JVM: 213/213 passed.
- `lintDebug`: passed.
- `verifyReleaseApkCompatibility`: passed application ID, min/target SDK, advertising/attribution permission exclusions, 16 KB ZIP/native alignment and SHA-256 inspection.
- `verifyPlayStoreAssets`: passed.
- Hosted Android CI [run #31969758228](https://github.com/kt-git-1/med-manager_v3/actions/runs/31969758228): all steps passed for implementation commit `83f13db`, including the new Release SDK/Data safety policy on a clean runner.

## Deliberately incomplete external evidence

- This inventory is from the current unsigned Release dependency/build state, not the release-owner-signed Play AAB.
- Firebase/Google disclosure guidance must be rechecked for the exact resolved versions immediately before submission.
- Data safety and Health apps answers require release-owner/legal confirmation and dated Play Console evidence.
- XP-009 therefore remains `PARTIAL`; local policy success is not Console acceptance.
