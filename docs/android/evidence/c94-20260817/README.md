# C94 Device-specific split install-surface evidence

**Date:** 2026-08-17

**Branch:** `android-dev`

**Source baseline:** published iOS/API `main@432b34c`

**Parity rows:** XP-008 and XP-010 remain `PARTIAL`

## Contract

- Build one complete APK Set from the exact generated AAB with strict-locked bundletool 1.18.0 and an ephemeral test certificate.
- Select exact base master, ABI, Japanese-language and density APKs for API 26 arm64/xhdpi, API 33 x86_64/xxhdpi and the observed API 35 A302SH arm64/280dpi specification.
- Accept only bundletool's reviewed `base-master.apk` or SDK-targeted `base-master_N.apk` name plus the exact three configuration identities; reject extra, missing, wrong-ABI/density/language, malformed, duplicate or misplaced DEX/native entries.
- Require every selected APK to be a valid ZIP-aligned package with the production application ID, exactly one matching ephemeral certificate and 16 KB native ZIP/ELF alignment. Reapply the complete Release manifest/SDK/permission/exported/App Links policy to the selected base master.
- Refuse to replace an existing device installation; require the connected device to match the retained specification; install exactly the four selected APKs without launching; verify installed base/config paths and version; uninstall on success or failure.
- Add `device-split-install-surface` to signed evidence and handoff policy without adding any selected APK to the three-file Play handoff.

## Local verification

- Pure selected-set contract accepted both unsuffixed and `_2` base-master variants and rejected ten malformed selections; its report replacement was atomic.
- Installer contract accepted one complete simulated install and rejected a pre-existing app plus an incomplete installed set. The refusal preserved the pre-existing package, while the post-install rejection removed the synthetic package.
- The actual unsigned Release AAB selected four APKs for each of three specifications. Every set contained four DEX files only in its base master and two native libraries only for the expected ABI.
- All twelve selected APKs used the same per-run synthetic certificate, production package and 16 KB ZIP policy. Both arm64 and x86_64 native splits passed 16 KB ELF LOAD alignment. Every base master repeated package, minSdk 26, targetSdk 35, six permissions, three exported components, three auth links and advertising/attribution exclusions.
- On connected `A302SH` serial `SX3LHMB430113755`, observed API 35, ABI list, physical density 280 and `ja-JP` matched the retained spec. With no existing package, `adb install-multiple` installed exactly base, arm64, Japanese and xhdpi paths at versionCode 1/versionName 1.0.6 without app launch. Uninstall passed and an independent package-list check confirmed absence.
- Clean ordinary regression passed 114 tasks (112 executed, two up-to-date), Debug JVM 216/216, Release JVM 213/213, Lint, SDK/APK/AAB/universal/split policies and Play assets.
- From clean implementation commit `63c62a5`, the complete synthetic `bundleSignedRelease` graph passed 81 tasks (79 executed, two up-to-date). The ledger identified full commit `63c62a533c1080203599dfbb754a2b04b5a429c3` and exact ordered ten-gate set.
- Independent checks passed both `SHA256SUMS` entries, byte-identical source/packaged JSON, commit and AAB hash; all three split reports retained exactly four APK identities. The C92 handoff remained exactly its named AAB, JSON and checksum file with no universal or split APK.
- The identical signed task reran in eight seconds (18 executed, 63 up-to-date), and final `clean` removed all generated keys, APK/APKS/AAB, reports and handoff artifacts.

## Hosted CI

- [Run #31979011016](https://github.com/kt-git-1/med-manager_v3/actions/runs/31979011016) passed all 25 job steps for commit `63c62a5`, including both split safety contracts and the Linux API 26 arm64, API 33 x86_64 and API 35 A302SH-spec selected-surface gate after all prior Android CI gates.

## Deliberately incomplete external evidence

- Every selected APK was locally re-signed by an ephemeral synthetic key. The physical installer source was `adb`, not Play.
- Local bundletool uses the same reviewed AAB model but does not prove Play's app-signing certificate, server-side split generation/optimization, processing/scan, installer identity, update path or track eligibility.
- Release-owner runtime/signing, the exact production C92 handoff, independent Console artifact/fingerprint comparison, Internal/Closed-track install and rollout evidence remain required.
