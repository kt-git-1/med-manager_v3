# C93 AAB-derived install-surface evidence

**Date:** 2026-08-17

**Branch:** `android-dev`

**Source baseline:** published iOS/API `main@432b34c`

**Parity row:** XP-010 remains `PARTIAL`

## Contract

- Use the strict-locked bundletool 1.18.0 graph to build a universal APK Set from the exact generated AAB.
- Sign only that diagnostic APK Set with a newly generated ephemeral test key; verify the extracted APK carries exactly that certificate.
- Require the APK Set to contain exactly `toc.pb` and `universal.apk`, with no duplicate entries; require the inner APK to be valid, nonempty and contain its manifest and primary DEX.
- Extract atomically and preserve an existing output on every rejected input.
- Reapply the C88 Release APK policy to the AAB-derived APK: production package, min/target SDK, exact permission/exported/App Links surface, advertising exclusions and 16 KB ZIP/native ELF alignment.
- Add `bundle-install-surface` to the exact ordered signed-evidence/handoff gate set, but keep `universal-test-only.apk` outside the C92 three-file handoff.

## Local verification

- The pure APK Set contract accepted one valid fixture and rejected eight missing/extra/duplicate/empty/malformed/missing-manifest-or-DEX fixtures; every rejection preserved the existing output and left no temporary file.
- The actual unsigned Release AAB produced an exact two-entry universal APK Set. Its extracted APK passed package `com.afterlifearchive.medmanager`, minSdk 26, targetSdk 35, six permissions, three exported components, three authentication links, advertising/attribution exclusion and 16 KB ZIP/native ELF alignment.
- From final clean implementation commit `4927f5d`, the complete synthetic `bundleSignedRelease` graph passed 78 tasks (76 executed, two up-to-date). The exact signed AAB remained base-only with four DEX files and eight native libraries.
- The generated evidence JSON identified full source commit `4927f5dbc6fca94adbb3f9b648734413ed195112` and contained the exact ordered nine-gate set with `bundle-install-surface` between AAB content and Play assets.
- Independent checks passed both `SHA256SUMS` entries, byte-identical source/packaged JSON, full source commit and AAB hash. The C92 handoff contained exactly its named AAB, JSON and checksum file; no universal APK entered it.
- The clean ordinary regression passed 110 tasks (108 executed, two up-to-date), Debug JVM 216/216, Release JVM 213/213, Lint, 175-module SDK policy, APK/AAB policies, the new install surface and Play assets.
- Final `clean` removed every generated key, APK/APKS/AAB, evidence JSON and handoff directory.

## Hosted CI correction and result

- Initial [run #31976923471](https://github.com/kt-git-1/med-manager_v3/actions/runs/31976923471) reached the new gate and proved the APKS structure, but exposed a Linux-only mismatch in parsed `apksigner` digest text. The gate failed; Play assets were skipped.
- Commit `4927f5d` removed localized/tool-version-sensitive digest parsing. It now compares the normalized certificate body exported from the ephemeral keystore with the PEM certificate verified and emitted from the APK.
- Corrected [run #31977465117](https://github.com/kt-git-1/med-manager_v3/actions/runs/31977465117) passed all 24 job steps, including both JVM variants, Lint, assembled Release APK, AAB content and the AAB-derived install surface on Linux.

## Deliberately incomplete external evidence

- The diagnostic universal APK uses an ephemeral synthetic signer. It is neither upload-authorized nor Play app-signed and must not be distributed or used for final device acceptance.
- Universal mode does not prove Play's optimized per-device split generation, Play processing/scan, install source, update path or App Links under the Play app-signing certificate.
- Release-owner runtime/signing, the exact production C92 handoff, independent Console fingerprint/scan comparison, Internal/Closed-track install and rollout evidence remain required.
