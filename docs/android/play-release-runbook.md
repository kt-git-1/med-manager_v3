# Android Play Release Runbook

This is the production handoff procedure for Gate I. It does not authorize creating, replacing, or committing an upload key. The upload key and its backup location must be chosen by the release owner.

## 1. Preconditions

- Work from `android-dev`; merge to `main` only after the release gates pass.
- Rebaseline against the latest `origin/main` and resolve every new iOS/API change first.
- Keep `BILLING_ENABLED=false` until a separate Google Play purchase contract is approved.
- Keep the registered production-package Android Firebase app's four runtime values outside Git. C76 completes consent/DebugView/Realtime and C80 closes processed Events; Explore remains before production rollout.
- Execute the privacy-reviewed consent-off/on/reset plus DebugView, Realtime, Events and Explore matrix in `firebase-analytics.md`; DebugView alone is not the acceptance gate.
- Register and verify the production App Link domain.
- Use Play App Signing. Store the upload-key keystore and passwords in an approved password manager/backup, never in Git or build logs.
- Increment `versionCode` for every Play upload. Confirm `versionName` is the intended public version.
- Recheck the current official target-API requirement immediately before upload. As of 2026-07-15, mobile submissions require API 35 or newer and this project targets 35.
- Verify 16 KB page-size compatibility for every native library. Play requires this for API-35+ submissions; the repository gate checks both APK ZIP alignment and every ELF `LOAD` segment.

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
PLAY_UPLOAD_CERT_SHA256=<registered Play upload certificate SHA-256>
```

The API production environment separately requires `ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS`, containing the Play app-signing certificate SHA-256 fingerprint (or comma-separated fingerprints during an intentional certificate transition). This value belongs in Vercel, not Android `local.properties`. See `evidence/i03-app-links-20260715.md`.

Do not add `google-services.json`, a keystore, passwords, or populated `local.properties` to the repository. The repository ignores `android/local.properties`, `*.jks`, and `*.keystore`, but the operator must still inspect `git status` before committing.

## 3. Build and local verification

```bash
cd android
./gradlew clean test assembleDebug assembleRelease lint
./gradlew verifyReleaseSdkPolicy
./gradlew verifyReleaseApkCompatibility
./gradlew verifyReleaseBundleContent
./gradlew verifyReleaseBundleInstallSurface
./gradlew verifyReleaseDeviceSplitSurface
./gradlew verifyPlayStoreAssets
./gradlew verifyUploadKeystore
./gradlew bundleSignedRelease
```

`verifyReleaseSdkPolicy` resolves the exact Release runtime graph and rejects unapproved collection/monetization SDKs. `verifyReleaseApkCompatibility` checks the exact assembled Release APK manifest/security/permission/SDK/16 KB contract. `verifyReleaseBundleContent` uses strict-locked bundletool to validate the AAB, requires only the reviewed base module, rejects embedded private configuration/key material, dumps the protobuf manifest and reapplies the APK policy before printing structure counts and SHA-256. `verifyReleaseBundleInstallSurface` uses that same locked bundletool and an ephemeral test key to build an exact two-entry universal APK Set from the exact AAB, proves the extracted APK's synthetic signer, then reapplies the complete APK policy. `verifyReleaseDeviceSplitSurface` builds the full APK Set once and selects exact base/ABI/Japanese/density quartets for API 26 arm64, API 33 x86_64 and API 35 A302SH; it verifies every split's package/certificate/16 KB contract and reapplies full policy to each selected base master. Both output families are diagnostic build output, not Play upload/install artifacts and not part of the C92 handoff. `verifyPlayStoreAssets` checks listing text/assets and iOS icon parity. `verifyUploadKeystore` runs before AAB generation: the selected alias must be a usable private-key entry and its certificate must exactly match `PLAY_UPLOAD_CERT_SHA256`; passwords are neither printed nor declared as cacheable task inputs. `bundleSignedRelease` intentionally fails before generation when runtime/signing/key inputs are incomplete or inconsistent; after generation it requires all APK/AAB/install-surface gates, complete JAR signature coverage, exactly one signer and the same certificate identity. A normal `bundleRelease` may remain unsigned and is not a Play-upload artifact.

The final signed task also requires clean committed Android and consumed role/icon/store inputs. It writes the exact ledger, then creates `app/build/outputs/play-release/v<version>-code<versionCode>-<commit12>/` atomically. That directory must contain exactly the commit/version-named AAB, byte-identical `play-release-evidence.json` and `SHA256SUMS`. If the same target already exists it must match all three files; the task never silently overwrites a conflict. Treat this directory as the indivisible handoff, run `shasum -a 256 -c SHA256SUMS` inside it, and select only its named AAB in Play Console. If any input changes, run `clean` and rebuild the complete handoff.

`verifyProductionRuntime` also prevents privileged Supabase credentials from entering the client artifact. `SUPABASE_ANON_KEY` may contain a current `sb_publishable_...` key or a legacy JWT whose issuer is `supabase` and sole role is `anon`; `sb_secret_...`, legacy `service_role`, wrong-issuer, malformed and opaque-long values fail closed. The synthetic `verifyRuntimeCredentialSafety` task exercises this contract without reading or logging any real key.

`app/gradle.lockfile` strictly pins `releaseRuntimeClasspath` and the isolated `bundletoolCli` verifier classpath. It currently represents 174 runtime coordinates and 17 bundletool coordinates, with four shared. Never hand-edit it. For an intentional app dependency update, use `./gradlew :app:dependencies --configuration releaseRuntimeClasspath --write-locks`; for a bundletool update, use the same command with `--configuration bundletoolCli`. Review every lock diff and rerun SDK, APK, AAB, disclosure and complete CI gates. Missing or stale state fails closed.

Before upload, also verify:

- `applicationId` is `com.afterlifearchive.medmanager`.
- The merged Release Manifest contains no advertising ID, AdServices attribution/ID, or Install Referrer permission.
- `verifyReleaseSdkPolicy` passes and its inventory matches the dependency state used for the exact AAB; recheck current Firebase vendor disclosures rather than copying older answers.
- `verifyReleaseApkCompatibility` passes for the exact commit and dependency lock state used for the AAB.
- `verifyReleaseBundleContent` validates that same AAB and reports the expected base-only structure and manifest contract.
- `verifyReleaseBundleInstallSurface` derives and validates the universal APK from that same AAB. Do not upload, distribute or retain its ephemeral-signed `universal-test-only.apk`, and do not use it to close Play app-signing, optimized split or track-install rows.
- `verifyReleaseDeviceSplitSurface` derives exact representative selected split quartets from that same AAB. The optional A302SH `adb install-multiple` verifier must refuse an existing installation, match the retained device spec, avoid launching the app and uninstall on success/failure. It still cannot close Play app-signing, Play-generated split or track-install rows.
- `verifyUploadKeystore` reports the same upload-certificate SHA-256 that the release owner independently reads from Play Console; a successful synthetic contract is not a substitute.
- No production secret appears in tracked files or Gradle output.
- `bundleSignedRelease` reports that the AAB certificate matches the registered Play upload certificate; independently compare the reported fingerprint with Play Console before upload.
- `SHA256SUMS` passes inside the generated three-file handoff, and its JSON SHA/certificate/version/commit match the named AAB selected for upload; retain the whole directory unchanged.
- API 26/33/35 tests and the physical-device matrix are green for the exact commit.

## 4. Play tracks

1. Upload the signed AAB to Internal testing and record commit SHA, `versionCode`, certificate fingerprint, tester account and result.
2. Install from Play, not adb. Verify caregiver/patient sign-in, session restoration, App Links, FCM permission/token/delivery/tap, local reminders, background/Doze/process death, legal links and analytics consent.
   Execute and record every applicable row in `physical-device-matrix.md`; the summary in this runbook is not a substitute for that evidence ledger.
3. Complete Data safety and Health apps declarations from the actual production build. Do not infer declarations from SDK names alone.
   Use `https://www.okusuri-mimamori.com/support#section-3` as the account-deletion request URL unless the release owner intentionally replaces it; it is the verified public support section with an email request path and does not require the app to be installed.
4. Populate the Japanese main store listing from `play-store-listing-ja.md`, upload the prepared 1350 x 2400 phone set, and verify every field and asset in the Play preview. The store listing is shared across test tracks.
5. Promote the same artifact to Closed testing. Record device/OS coverage, crashes/ANRs, Firebase delivery and Analytics verification.
6. Only after all residual matrix rows are accepted, prepare production rollout and the `android-dev` to `main` merge without overwriting newer iOS/API work.

## 5. Current external blockers

- Firebase app registration, runtime configuration, physical consent, DebugView and Realtime evidence are complete under C76; C80 closes processed Events. Analytics Explore and FCM remain pending.
- C79 confirms production `main@432b34c` still rejects Android push-device registration before upsert. Merge/deploy the tested Android API contract and rerun FC-001 before any Play FCM acceptance; do not relabel Android devices as iOS.
- No release-owner upload keystore has been selected, so a production-signed AAB cannot be produced here yet.
- One A302SH Android 15/API 35 Debug target is evidenced through C76; old-supported and Google/reference devices remain pending.
- Play-installed Internal/Closed track and final Console declaration evidence remain pending.
