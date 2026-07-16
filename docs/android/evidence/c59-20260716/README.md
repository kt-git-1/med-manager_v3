# C59 local-completion and external-release audit

**Date:** 2026-07-16
**Branch:** `android-dev`
**Starting commit:** `bf0c192`
**Reference product:** `main@3e52fb2`

## Result

The audit distinguishes finished Android implementation from evidence that only a release owner, physical device or external Console can produce. It found no new local product/API implementation gap. It did find and close two documentation-control gaps:

1. The master plan still reported `main@1cf8aef` and “implementation recheck required” after the completed C58 rebaseline.
2. Gate H documents referenced a nonexistent `docs/firebase-analytics.md`, so the required live verification procedure was not actually reproducible from the repository.

C59 pins the plan to `main@3e52fb2`, closes the obsolete C01 umbrella item through its C37–C48 evidence owners, and adds `docs/android/firebase-analytics.md` with consent-off/on/reset controls, DebugView commands, the exact safe event schema, Realtime/Events/Explore checks and the H07 evidence format.

## Authoritative current-state checks

The audit disclosed only configured/missing state and never printed credentials.

| Requirement/input | Current evidence | Judgment |
|---|---|---|
| Android branch isolation | Android worktree is on `android-dev`; parallel iOS worktree is `main@3e52fb2` | Satisfied |
| Remote checkpoint | Starting local HEAD exactly matched `origin/android-dev@bf0c192` | Satisfied before C59 |
| Pinned current main | C58 merge `2fb4a9f` contains `main@3e52fb2`; source/API/UI contracts record the delta | Satisfied |
| Local implementation gates | C57: API 26/33/35 777/777; C58: API 35 259/259, JVM 189/189, API 315/315, typecheck/lint/build pass | Satisfied for current local implementation |
| Firebase Android runtime values | None of `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_SENDER_ID` was available in environment or local configuration | Missing external input |
| Analytics live evidence | No configured Firebase transport/property; no physical target | Not yet verifiable; procedure now complete |
| Release signing | No release-owner keystore or four signing inputs available | Missing external input |
| Physical-device matrix | `adb devices -l` reported no attached target | Missing external target |
| Play Internal/Closed and Console declarations | No signed AAB, Play app-signing certificate or Console session/artifact supplied | Missing external access/artifact |
| Final merge to main | Release rows remain `PARTIAL`; merge gate intentionally remains closed | Correctly pending |

## Fail-closed and secret-free preflight

The current configuration was exercised rather than inferred from filenames:

- `:app:verifyProductionRuntime` failed only for the four missing/malformed Firebase names: `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_SENDER_ID`.
- `:app:verifyProductionSigning` failed with the expected four-input release-signing instruction and did not print a credential value.
- `:app:verifyPlayStoreAssets` passed.
- `:app:verifyReleaseApkCompatibility` passed for application ID `com.afterlifearchive.medmanager`, min SDK 26, target SDK 35, forbidden advertising/attribution permission exclusion and 16 KB ZIP/native ELF alignment.
- Audited unsigned Release APK SHA-256: `4ba40093ff40cc69ccefa2b87a1103f2bd73321230c894b8c1ab5096f1f04c32`.

The two expected failures prove that a locally incomplete environment cannot accidentally emit the signed Play task; the two passing gates prove that missing external credentials do not hide a local listing or APK compatibility defect.

## Remaining release sequence

1. Supply the four Android Firebase values and a physical device; execute `docs/android/firebase-analytics.md` and archive H07 evidence.
2. Execute the physical notification, FCM/Doze/process-death, TalkBack/font/dark/rotation, browser/Sharesheet, session restore, backup/transfer and destructive-flow matrix.
3. Select/back up the release-owner upload key, provide signing inputs, run `bundleSignedRelease`, verify its certificate/hash and upload the exact AAB to Play Internal testing.
4. Obtain the Play app-signing fingerprint, deploy/verify Digital Asset Links from a Play-installed build, complete Data safety/Health apps/listing review, then promote the same artifact to Closed testing.
5. Re-fetch current `main`, audit any newer API/iOS delta, rerun the affected and final gates, and merge Android files without overwriting newer iOS/API work.

None of these rows can be marked complete from emulator tests or documentation alone. The active Android objective therefore remains open after C59 even though the locally implementable product surface is complete.
