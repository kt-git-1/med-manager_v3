# Android Play Release Runbook

This is the production handoff procedure for Gate I. It does not authorize creating, replacing, or committing an upload key. The upload key and its backup location must be chosen by the release owner.

## 1. Preconditions

- Work from `android-dev`; merge to `main` only after the release gates pass.
- Rebaseline against the latest `origin/main` and resolve every new iOS/API change first.
- Keep `BILLING_ENABLED=false` until a separate Google Play purchase contract is approved.
- Create the Android app in Firebase and supply the four runtime values. Complete Analytics DebugView evidence before a production rollout.
- Register and verify the production App Link domain.
- Use Play App Signing. Store the upload-key keystore and passwords in an approved password manager/backup, never in Git or build logs.
- Increment `versionCode` for every Play upload. Confirm `versionName` is the intended public version.

## 2. Local or CI-only configuration

Supply these names either as environment variables or in the Git-ignored `android/local.properties`. `RELEASE_STORE_FILE` is resolved relative to the `android` project directory; an absolute path is also accepted.

```properties
API_BASE_URL=https://www.okusuri-mimamori.com/
SUPABASE_URL=...
SUPABASE_ANON_KEY=...
FIREBASE_APP_ID=...
FIREBASE_API_KEY=...
FIREBASE_PROJECT_ID=...
FIREBASE_SENDER_ID=...
EMAIL_CONFIRMATION_REDIRECT_URL=https://www.okusuri-mimamori.com/auth/confirmed
BILLING_ENABLED=false
RELEASE_STORE_FILE=/absolute/private/path/upload-key.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

The API production environment separately requires `ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS`, containing the Play app-signing certificate SHA-256 fingerprint (or comma-separated fingerprints during an intentional certificate transition). This value belongs in Vercel, not Android `local.properties`. See `evidence/i03-app-links-20260715.md`.

Do not add `google-services.json`, a keystore, passwords, or populated `local.properties` to the repository. The repository ignores `android/local.properties`, `*.jks`, and `*.keystore`, but the operator must still inspect `git status` before committing.

## 3. Build and local verification

```bash
cd android
./gradlew clean test assembleDebug assembleRelease lint
./gradlew bundleSignedRelease
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

`bundleSignedRelease` intentionally fails before bundle generation when any signing value is missing or the keystore path does not exist. A normal `bundleRelease` may remain unsigned and is not a Play-upload artifact.

Before upload, also verify:

- `applicationId` is `com.afterlifearchive.medmanager`.
- The merged Release Manifest contains no advertising ID, AdServices attribution/ID, or Install Referrer permission.
- No production secret appears in tracked files or Gradle output.
- The AAB certificate matches the registered Play upload certificate.
- API 26/33/35 tests and the physical-device matrix are green for the exact commit.

## 4. Play tracks

1. Upload the signed AAB to Internal testing and record commit SHA, `versionCode`, certificate fingerprint, tester account and result.
2. Install from Play, not adb. Verify caregiver/patient sign-in, session restoration, App Links, FCM permission/token/delivery/tap, local reminders, background/Doze/process death, legal links and analytics consent.
3. Complete Data safety and Health apps declarations from the actual production build. Do not infer declarations from SDK names alone.
4. Promote the same artifact to Closed testing. Record device/OS coverage, crashes/ANRs, Firebase delivery and Analytics verification.
5. Only after all residual matrix rows are accepted, prepare production rollout and the `android-dev` to `main` merge without overwriting newer iOS/API work.

## 5. Current external blockers

- No production Android Firebase values are available locally, so DebugView/Realtime/Events/Explore evidence is pending.
- No release-owner upload keystore has been selected, so a production-signed AAB cannot be produced here yet.
- Physical-device, Play-installed Internal/Closed track and Console declaration evidence remain pending.
