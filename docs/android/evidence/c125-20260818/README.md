# C125 Android local gate sweep (2026-08-18)

**Date:** 2026-08-18  
**Branch:** `android-dev` (`4b6612b`)  
**Baseline:** published iOS 1.0.6 Build 51 (`main@432b34c`)

## Summary

- Ran local Android verification tasks after C124.
- All Kotlin unit/lint/release-evidence-local checks passed.
- Firebase/production runtime preflights are intentionally blocked by missing local environment values in this host.
- `adb` was not available in `PATH`, so device-side execution was not possible in this run.

## Commands executed

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- `./gradlew :app:lintDebug :app:testDebugUnitTest`
- `./gradlew :app:verifyReleaseGates`
- `./gradlew :app:verifyPlayStoreAssets :app:verifyPlayStoreListing`
- `./gradlew :app:verifyReleaseSdkPolicy :app:verifyReleaseApkCompatibility :app:verifyReleaseBundlePolicyContract`
- `./gradlew :app:verifyReleaseEvidencePolicyContract`
- `./gradlew :app:verifyReleaseBundleInstallSurface`
- `./gradlew :app:verifyFirebaseRuntime` *(failed: missing Firebase runtime env)*
- `./gradlew :app:verifyProductionRuntime` *(failed: missing SUPABASE/Firebase runtime env)*

## Findings

- `testDebugUnitTest`/`assembleDebug`: OK
- `lintDebug`: OK
- `verifyReleaseGates`: ready=3 / blocked=7 / verified=0 / partialRequirements=6
- `verifyPlayStoreListing`: OK (textBlocks=4, screenshots=8, sourceMappings=8, publicUrls=5)
- `verifyPlayStoreAssets`: OK
- `verifyReleaseSdkPolicy`: OK (UP-TO-DATE)
- `verifyReleaseApkCompatibility`: OK (applicationId `com.afterlifearchive.medmanager`, 6 permissions, 3 exported surfaces)
- `verifyReleaseBundlePolicyContract`: OK
- `verifyReleaseEvidencePolicyContract`: OK
- `verifyReleaseBundleInstallSurface`: OK
- `verifyFirebaseRuntime` and `verifyProductionRuntime` are blocked on environment setup.
- `adb` binary was not found in expected tool paths (`/usr/local/bin/adb`, `/opt/homebrew/bin/adb`, `/Library/Android/sdk/platform-tools/adb`, `~/Library/Android/sdk/platform-tools/adb`).

## Next required input for continuation

1. Provide an `adb` executable in shell `PATH` (or add full `platform-tools` path) and confirm connected device state.
2. Supply Firebase + Supabase runtime values via `android/local.properties` for remote-runtime verification:
   - `FIREBASE_APP_ID`
   - `FIREBASE_API_KEY`
   - `FIREBASE_PROJECT_ID`
   - `FIREBASE_SENDER_ID`
   - `SUPABASE_URL`  
   - `SUPABASE_ANON_KEY`
3. Re-run runtime verification tasks and proceed with requested physical assisted checks.
