# Android Play Release Runbook

This is the production handoff procedure for Gate I. It does not authorize creating, replacing, or committing an upload key. The upload key and its backup location must be chosen by the release owner.

## 1. Preconditions

- Complete `play-developer-account-onboarding.md` first. This Medical / Medication and Treatment Management product is a Health app, so use a verified Play **Organization** developer account with matching D-U-N-S-backed identity; do not create a Personal account as a shortcut. The currently selected Google account is still at developer-account signup.
- Work from `android-dev`; merge to `main` only after the release gates pass.
- Treat `release-gates.json` plus its matching `RG-001`–`RG-010` backlog rows as the canonical residual order. The ledger does not authorize any listed external action.
- Rebaseline against the latest `origin/main` and resolve every new iOS/API change first.
- Run `verifyMainMergeSurface` after every fetch/rebaseline. It verifies committed history only; a pass does not authorize merge/deploy and does not excuse a dirty worktree.
- Require API CI's C97 legacy-upgrade and C98 read-only preflight/postdeploy contracts to pass. They protect compatibility and observation locally but do not authorize or prove a production migration.
- Keep `BILLING_ENABLED=false` until a separate Google Play purchase contract is approved.
- Keep the registered production-package Android Firebase app's four runtime values outside Git. C76 completes consent/DebugView/Realtime and C80 closes processed Events; Explore remains before production rollout.
- Execute the privacy-reviewed consent-off/on/reset plus DebugView, Realtime, Events and Explore matrix in `firebase-analytics.md`; DebugView alone is not the acceptance gate.
- Register and verify the production App Link domain.
- Use Play App Signing. Store the upload-key keystore and passwords in an approved password manager/backup, never in Git or build logs.
- Increment `versionCode` for every Play upload. Confirm `versionName` is the intended public version.
- Recheck the current official target-API requirement immediately before upload. As of 2026-07-15, mobile submissions require API 35 or newer and this project targets 35.
- Verify 16 KB page-size compatibility for every native library. Play requires this for API-35+ submissions; the repository gate checks both APK ZIP alignment and every ELF `LOAD` segment.

The organization account is an external release-owner asset, not a repository or CI resource. Current Google guidance documents a one-time US$25 registration fee and requires organization name/address, D-U-N-S, website and verified contact details. Confirm the exact displayed agreement, local-currency charge and legal/entity inputs immediately before signup. Account selection, agreement acceptance, payment, identity/contact verification and application creation are persistent external actions and must not be automated from this runbook without explicit action-time authorization.

The 12 opted-in testers for 14 continuous days requirement applies to newly created **Personal** accounts. It is not the account model for this Health app and must not be copied into the organization release plan. This project still requires Internal installation followed by a controlled closed test, crash/ANR review and final production-access checks; those are product-quality gates, not a claim that organization accounts inherit the Personal-account rule.

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
PLAY_APP_SIGNING_CERT_SHA256_FINGERPRINTS=<Play app-signing certificate SHA-256>
```

`PLAY_UPLOAD_CERT_SHA256` and `PLAY_APP_SIGNING_CERT_SHA256_FINGERPRINTS` are different identities under Play App Signing. Read both independently only after the verified Organization account creates the exact production-package app; never substitute the locally generated upload certificate for the app-signing certificate delivered to users. The latter may be comma-separated only during an intentional certificate transition and is public certificate metadata, not a private key.

The API production environment separately requires `ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS` with the exact same Play app-signing fingerprint set. This value belongs in Vercel, not Android `local.properties`. See `evidence/i03-app-links-20260715.md`.

Do not add `google-services.json`, a keystore, passwords, or populated `local.properties` to the repository. The repository ignores `android/local.properties`, `*.jks`, and `*.keystore`, but the operator must still inspect `git status` before committing.

## 2.1 Protected production API release control

After the reviewed Android/API change is present on `main`, use only `.github/workflows/android-api-production-release.yml` for RG-002. The workflow has no push, pull-request, schedule or chained trigger; it accepts a manual dispatch only from the exact current `main` commit. `api/vercel.json` disables Git-based Vercel deployment for both `android-dev` and `main`, so a source push cannot deploy the API ahead of its migration. Do not re-enable Git deployment or run a separate Vercel deployment while this release is in progress.

Create the GitHub environment `android-api-production` under release-owner control with required reviewers and prevent self-review where the repository plan supports it. Store only these workflow inputs there:

- secrets: `ANDROID_PRODUCTION_DIRECT_URL`, `PLAY_APP_SIGNING_CERT_SHA256_FINGERPRINTS`, `VERCEL_TOKEN`;
- variables: `ANDROID_API_PRODUCTION_RELEASE_ENABLED`, `ANDROID_API_PRODUCTION_REVIEWERS_CONFIGURED`, `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID`.

The direct URL secret must target the approved production Supabase `public` schema. Do not put SSL query parameters into the secret: C104 removes any supplied SSL overrides, downloads the exact official Supabase Root 2021 CA to runner-temporary storage, validates its canonical DER SHA-256 and validity window, then exports a masked `sslmode=verify-full` URL with an absolute `sslrootcert` path. All audit, Prisma migration and runtime build steps consume that normalized URL; the raw secret is referenced exactly once and the CA is removed by an always-run cleanup. The Play fingerprint is public certificate metadata but remains environment-scoped to prevent an unreviewed identity change. Vercel's production environment separately retains the API runtime variables, including `ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS`; the workflow pulls those values into an ephemeral production build and deletes `.vercel`/`.next` state at the end. Never copy secret values, prepared URLs, certificate files or raw migration/deploy output into evidence.

After locked installation, the workflow runs `npm audit --audit-level=high` before it reads the production database secret. C101's bounded lock refresh leaves zero High/Critical runtime or build advisories; any future High/Critical advisory blocks even preflight. C102 reduces the explicitly tracked Moderate findings from thirteen to six in the Firebase Storage/uuid transitive chain; they are not represented as zero-risk. Do not bypass the audit or force an unsupported transitive override without a separate compatibility review.

C102 fixes the API to Node `22.x` in `package.json`; Vercel uses that exact supported major instead of resolving the previous broad `>=20` range to its latest runtime. API CI, API E2E and the production workflow contain exactly five Node 22 setup entries, and the runtime contract locks Prisma 7.9.1, Node 22 types and Firebase Admin 14.2.0. Never change only a dashboard setting or one workflow: update the package, all workflows, dependency baseline, contract and full DB/API/E2E evidence together. The complete audit now retains six Moderate Firebase Storage/uuid transitive findings; they are not grounds to bypass the zero-High/Critical gate or force an unsupported uuid major.

C104 applies the same trust boundary to deployed API runtime connections. Remote production URLs must resolve under the reviewed Supabase database domains; runtime removes connection-string SSL keys and supplies the pinned Supabase root with `rejectUnauthorized=true`, so the normal Node TLS stack verifies the chain and hostname. Loopback remains available for local build/tests, and an unreviewed remote provider fails closed in production. A live read-only audit proved exact `verify-full` with the official CA and a separate zero-row-query socket check proved an authorized TLS 1.3 connection. The production Supabase project's incoming-SSL enforcement was still OFF at the checkpoint. Enabling it restarts the database, so only the release owner may schedule that change; after restart, rerun the workflow's default count-only preflight and confirm application health before any migration/deploy arm is enabled. C104 itself changes no dashboard setting and performs no production write.

The dispatch inputs are deliberately exact:

| Mode | Confirmation | Effect |
|---|---|---|
| `preflight` (default) | `PREFLIGHT_ANDROID_API_PRODUCTION` | C98 count-only read-only audit; no migration, build or deploy |
| `deploy` | `DEPLOY_ANDROID_API_PRODUCTION` | First rollout only: absent-state preflight, migration, exact postdeploy audit, pinned Vercel production build/deploy, fixed health and App Links checks |
| `release` | `RELEASE_ANDROID_API_PRODUCTION` | Later/recovery rollout: require the migration's exact postdeploy state before the idempotent migration command and the same deploy/smokes |

For either write mode, set both arm variables to the exact lowercase value `true`; otherwise dispatch validation fails. `ANDROID_API_PRODUCTION_REVIEWERS_CONFIGURED=true` is an attestation, not a replacement for actual environment protection. Enter the full 40-character lowercase SHA shown by current `main`; the workflow fetches `origin/main`, compares all three identities and refuses a dirty checkout. Run `preflight` first and review its anonymous counts/window. Only after independent release approval should the owner arm and dispatch `deploy`. If migration succeeds but a later build/deploy check fails, do not retry `deploy` because absent-state preflight will correctly reject it; diagnose the failure and use `release` only after the exact postdeploy audit is valid. Keep both arms false or absent outside an approved window.

As of C100, the live repository has no `android-api-production` environment and none of these production secrets/variables, so no write-mode run is possible. C100 creates and tests the control path only; it does not execute preflight, migrate a database or deploy Vercel.

## 3. Build and local verification

Before deploying the Android API contract, require the C97 static/isolated upgrade gate and run C98 `preflight` against the target. C98 opens one `READ ONLY` transaction, requires the migration/columns/indexes to be absent and outputs counts only. Use the protected workflow whenever possible: it creates the exact `verify-full` URL without exposing the secret. For an independently approved emergency local audit, first download and fingerprint the official CA in an ephemeral directory, then pass a direct PostgreSQL URL containing exact `sslmode=verify-full` and an absolute `sslrootcert` path. `sslmode=require`, missing/relative roots and `uselibpqcompat` are rejected. Never place the URL in shell history, chat, Git or evidence.

```bash
ANDROID_MUTATION_DEPLOYMENT_AUDIT=preflight \
ALLOW_REMOTE_ANDROID_MUTATION_AUDIT=1 \
DIRECT_URL='<approved-direct-url-with-sslmode-verify-full-and-absolute-sslrootcert>' \
node scripts/verify-android-mutation-deployment.mjs --mode preflight
```

Use its anonymous counts to assess the unique-index deployment window. Deploy once through the normal controlled API release with `npx prisma migrate deploy`, then change both mode values to `postdeploy` and rerun the same C98 command. Postdeploy must match the checked-in SQL checksum, the finished Prisma record, both exact valid/ready indexes and zero duplicate groups before API replay testing. The audit never substitutes for deploy logs or real uncertain-response recovery, and identifier/health values must never be copied into evidence.

```bash
cd android
./gradlew verifyMainMergeSurface
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

After the Android API contract is merged to `main`, its production deployment is complete and Play App Signing is enabled, run the network gate. It checks only the canonical host declared by the Release manifest and refuses redirects, HTTP/cache/content drift, extra trust statements and any certificate mismatch:

```bash
./gradlew verifyProductionAppLinks
```

After installing the exact Internal-test artifact from Google Play on an API-31+ physical device, run the installed gate. It first reruns the production gate, then requires the Google Play installer, exact app-signing certificate and only `www.okusuri-mimamori.com: verified`; it never launches or mutates app data:

```bash
ANDROID_SERIAL=<physical-device-serial> ./gradlew verifyPlayInstalledAppLinks
```

`verifyReleaseSdkPolicy` resolves the exact Release runtime graph and rejects unapproved collection/monetization SDKs. `verifyReleaseApkCompatibility` checks the exact assembled Release APK manifest/security/permission/SDK/16 KB contract. `verifyReleaseBundleContent` uses strict-locked bundletool to validate the AAB, requires only the reviewed base module, rejects embedded private configuration/key material, dumps the protobuf manifest and reapplies the APK policy before printing structure counts and SHA-256. `verifyReleaseBundleInstallSurface` uses that same locked bundletool and an ephemeral test key to build an exact two-entry universal APK Set from the exact AAB, proves the extracted APK's synthetic signer, then reapplies the complete APK policy. `verifyReleaseDeviceSplitSurface` builds the full APK Set once and selects exact base/ABI/Japanese/density quartets for API 26 arm64, API 33 x86_64 and API 35 A302SH; it verifies every split's package/certificate/16 KB contract and reapplies full policy to each selected base master. Both output families are diagnostic build output, not Play upload/install artifacts and not part of the C92 handoff. `verifyPlayStoreAssets` first exercises one accepted/twenty rejected listing fixtures, then binds locale/package/app name, disclosures, URLs, screenshot order and shipping configuration. It also exercises deterministic/invalid renderer inputs, generates all eight JPEGs in build output, requires byte-for-byte equality with the committed handoff and checks source pixels, padding, dimensions, iOS icon parity and graphics. Run `updatePlayStoreScreenshots` explicitly to accept reviewed source changes; verification never rewrites them. Neither task replaces exact signed-build comparison or Play preview. `verifyUploadKeystore` runs before AAB generation: the selected alias must be a usable private-key entry and its certificate must exactly match `PLAY_UPLOAD_CERT_SHA256`; passwords are neither printed nor declared as cacheable task inputs. `bundleSignedRelease` intentionally fails before generation when runtime/signing/key inputs are incomplete or inconsistent; after generation it requires all APK/AAB/install-surface gates, complete JAR signature coverage, exactly one signer and the same certificate identity. A normal `bundleRelease` may remain unsigned and is not a Play-upload artifact.

`verifyMainMergeSurface` operates only on committed `origin/main...HEAD`. It requires current main as the exact ancestor, no deletion/rename, exactly the reviewed top-level/workflow/API/documentation scopes, no iOS path, no private/generated artifact or unsupported file mode, and bounded high-fidelity evidence size. Hosted CI uses full Git ancestry before running it. If main advances, merge/rebaseline main into `android-dev`, rerun API/Android gates and update the allowlist only after direct review; never force the task green by changing refs or removing a required contract.

The final signed task also requires clean committed Android and consumed role/icon/store inputs. Its schema-v2 ledger includes the Japanese listing, source map, icon, feature graphic and ordered eight screenshot SHA-256 values; handoff creation re-hashes those exact repository files. C111 runs this production generator path in hosted CI using an isolated committed tree and disposable `jarsigner`/`keytool` identity, including dirty-listing rejection; that signer is never a production credential. The owner task then creates `app/build/outputs/play-release/v<version>-code<versionCode>-<commit12>/` atomically. That directory must contain exactly the commit/version-named AAB, byte-identical `play-release-evidence.json` and `SHA256SUMS`. If the same target already exists it must match all three files; the task never silently overwrites a conflict. Treat this directory as the indivisible handoff, run `shasum -a 256 -c SHA256SUMS` inside it, and select only its named AAB in Play Console. If any input changes, run `clean` and rebuild the complete handoff.

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
- `verifyProductionAppLinks` passes against the deployed redirect-free `www` endpoint using the independently read Play app-signing certificate set. The current production 404 is a hard failure, not a waivable warning.
- No production secret appears in tracked files or Gradle output.
- `bundleSignedRelease` reports that the AAB certificate matches the registered Play upload certificate; independently compare the reported fingerprint with Play Console before upload.
- `SHA256SUMS` passes inside the generated three-file handoff, and its JSON SHA/certificate/version/commit match the named AAB selected for upload; retain the whole directory unchanged.
- API 26/33/35 tests and the physical-device matrix are green for the exact commit.
- `verifyMainMergeSurface` passes against freshly fetched `origin/main`; inspect its base/head/count summary and separately confirm `git status` is clean before opening or performing the merge.
- From `api/`, `node scripts/verify-android-play-policy-readiness.mjs --release` passes only after the verified Organization legal name is present in the public privacy policy and `play-review-access.json` records final reusable, region-independent access against the exact Play `versionCode`. Never place the login, password or patient identifier in Git or command output.

## 4. Play tracks

1. Upload the signed AAB to Internal testing and record commit SHA, `versionCode`, certificate fingerprint, tester account and result.
2. Install from Play, not adb. Run `verifyPlayInstalledAppLinks`, then verify caregiver/patient sign-in, session restoration, both production auth paths, browser fallback, FCM permission/token/delivery/tap, local reminders, background/Doze/process death, legal links and analytics consent.
   Execute and record every applicable row in `physical-device-matrix.md`; the summary in this runbook is not a substitute for that evidence ledger.
3. Complete Data safety and Health apps declarations from the actual production build. Do not infer declarations from SDK names alone.
   After C106 is merged/deployed, confirm `https://www.okusuri-mimamori.com/account-deletion` returns HTTP 200 without authentication and use that dedicated URL. Do not enter it while production still returns 404. Run the release-mode policy verifier with the verified legal developer name and final review-access facts before submission.
4. Run `verifyPlayStoreAssets`, populate the Japanese main store listing from `play-store-listing-ja.md`, upload the exact ordered 1350 x 2400 phone set pinned by `play-store-assets/phone-ja-JP/sources.tsv`, and compare every field/surface with the exact signed build in the Play preview. The store listing is shared across test tracks.
5. Promote the same artifact to Closed testing. Record device/OS coverage, crashes/ANRs, Firebase delivery and Analytics verification.
6. Only after all residual matrix rows are accepted, prepare production rollout and the `android-dev` to `main` merge without overwriting newer iOS/API work.

## 5. Current external blockers

- Firebase app registration, runtime configuration, physical consent, DebugView and Realtime evidence are complete under C76; C80 closes processed Events. Analytics Explore and FCM remain pending.
- C79 confirms production `main@432b34c` still rejects Android push-device registration before upsert. Merge/deploy the tested Android API contract and rerun FC-001 before any Play FCM acceptance; do not relabel Android devices as iOS.
- C95 confirms production Digital Asset Links still returns HTTP 404 because its tested route is only on `android-dev`. Merge/deploy the same Android API contract, configure the Play app-signing certificate in production and pass both App Links tasks; neither a synthetic certificate nor an upload certificate closes this row.
- No release-owner upload keystore has been selected, so a production-signed AAB cannot be produced here yet.
- One A302SH Android 15/API 35 Debug target is evidenced through C76; old-supported and Google/reference devices remain pending.
- Play-installed Internal/Closed track and final Console declaration evidence remain pending.
- C106 source readiness passes, but its public route is not deployed and the Organization legal name/final Play review-access fields intentionally remain pending. The retained QA password stays only in the external release-owner secret store and must never be copied into Git or evidence.
