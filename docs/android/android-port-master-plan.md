# Android Port Master Development Plan

**Status:** iOS 1.0.6 emulator parity complete; physical/release gates open
**Development branch:** `android-dev`
**Reference:** published iOS 1.0.6 Build 51, `main@432b34c`
**Stack:** Kotlin, Jetpack Compose, Material 3

## 1. Outcome

Build a native Android app that reproduces the pinned iOS product's behavior, business rules, information architecture, Japanese copy, accessibility intent and visual identity. Android-native system interaction is used only where the platform requires it.

Completion means evidence-backed parity, not the presence of similar screens.

## 2. Non-negotiable rules

1. Android work stays on `android-dev` until the release gates pass.
2. Backend routes/tests are the business-rule authority; current iOS behavior/tests are the client authority.
3. Pin a source SHA. Never chase a moving `main` implicitly.
4. Define auth, request, response, error, time-zone and idempotency contracts before UI.
5. Model loading, cached/updating, empty, content, validation, offline, retry, auth, forbidden, conflict, rate-limit, partial success and retention states where applicable.
6. A successful write is not rolled back visually by a failed follow-up read.
7. Product copy comes from localization resources; no production UI hardcodes Japanese in Kotlin.
8. Production components power fixtures and screenshot tests. Screenshot-only duplicate UI is prohibited.
9. Visual and accessibility verification occurs per vertical slice, not at the end.
10. Any intentional Android difference is recorded with rationale and acceptance criteria.
11. New product ideas are separated from parity work and require explicit approval.
12. A requirement is complete only at `VERIFIED`, with physical-device evidence where required.

## 3. Architecture boundaries

Dependency direction:

`Compose route -> state holder -> use case/repository -> typed data source -> API/platform`

- Compose does not construct URLs, parse JSON, inspect tokens or encode backend policy.
- Every endpoint uses an explicit `PUBLIC`, `PATIENT` or `CAREGIVER` auth policy.
- DTOs mirror wire shape. Domain models express app meaning. UI models contain formatted display state.
- Session, selected patient, preferences, freshness revisions and navigation targets have distinct storage/state owners.
- The backend owns authorization, inventory, entitlement, retention, linking and record idempotency.
- Android owns Keystore-backed secrets, runtime permissions, local alarms, FCM token lifecycle, content URI sharing, system back and process recreation.

## 4. Required documents

- `source-baseline.md`: pinned truth and change control
- `api-contracts.md`: auth/HTTP/domain contracts
- `ui-screen-contracts.md`: information architecture and screen-state requirements
- `ui-fidelity-spec.md`: visual comparison process
- `parity-requirements.md`: status and evidence ledger
- `current-gap-audit.md`: current implementation delta
- Phase notes: implementation evidence, not higher authority than the files above

## 5. Vertical-slice procedure

Every slice follows this order:

1. **Pin references**: list iOS views/view models/DTOs/tests, API routes/services/tests and relevant localization key groups.
2. **Capture current iOS**: deterministic screenshots for content and exceptional states before writing Compose.
3. **Write contract tests**: method/path/auth/body/response/error/time-zone fixtures.
4. **Define state machine**: events, state transitions, cancellation, retries, optimistic behavior and invalidation consumers.
5. **Implement data/domain layer**: no UI until contracts pass.
6. **Build shared components**: tokens, accessibility semantics and deterministic fixtures.
7. **Implement navigation/interaction**: back, permission, keyboard, lifecycle, process recreation and deep links.
8. **Run automated gates**: unit, contract, Compose, lint, debug/release builds.
9. **Compare visuals**: matched screenshots, side-by-side and overlay/diff; fix material deltas immediately.
10. **Verify real behavior**: emulator, safe live API smoke, then physical device.
11. **Update evidence**: change matrix status only after recording results.

## 6. Rebaselined execution phases

### R0 — Baseline integrity and regression repair

Goal: keep the Android foundation conformant with the explicitly pinned published `main@432b34c` product contract before release verification.

1. Add explicit per-request auth policies; make link exchange public/no-auth and non-invalidating.
2. Add link-error localization fixtures and canonical UI mapping.
3. Prove uninstall/reinstall and restore cannot resurrect a patient token.
4. Add a shared data-freshness revision/event model for Today, History and Inventory.
5. Move scheduled-dose reminder rebuilding off the record critical path; rebuild only after actual scheduled changes.
6. Test next-day and month-boundary reminder retention.
7. Preserve lazy tab instances/state and block hidden-tab input/accessibility.
8. Recapture changed patient iOS states and recheck affected patient matrix rows.
9. Preserve immediate Patient/Caregiver mutation feedback while authoritative post-write reconciliation runs outside the blocking UI path.

**Exit:** all recheck rows return to at least `IMPLEMENTED`; build/test/lint pass; no new caregiver UI yet.

### R1 — Shared production architecture

1. Replace feature-boundary `JSONObject` handling with typed serialization.
2. Introduce role-aware repositories/state holders and selected-patient persistence API.
3. Move every user-visible Kotlin literal into resources.
4. Split oversized patient UI into routes/components/state owners without changing behavior.
5. Establish screenshot fixtures, fake clock, deterministic Tokyo calendar and fake permission/push adapters.

**Exit:** shared architecture supports both roles without auth or policy branching in Compose.

### P1 — Entry/auth and patient parity verification

Reverify existing entry/auth and patient features against current iOS:

- Mode select and caregiver auth flows
- Patient linking/session restoration
- Patient Today individual/bulk/PRN/detail
- Patient history month/day/retention
- Patient notification settings, tutorial, deep links, accessibility and dark theme

This is a verification/repair phase, not a declaration that earlier code is automatically accepted.

### C1 — Caregiver shell and patient management

1. Five-tab persistent shell and lazy tab lifetime
2. Patient list/create/select and sole-patient auto-selection
3. Time presets and selected-patient propagation
4. Linking-code issue/copy/share
5. Revoke versus permanent delete semantics
6. Caregiver tutorial steps that operate on the real flow
7. No-patient and data-unavailable states shared across tabs

### C2 — Medication and regimen

1. Medication list filters/empty states
2. Add/edit regular and PRN medication forms
3. Date, dose, strength, notes and inventory validation
4. Daily/weekday regimen CRUD using patient slot presets
5. Mutation invalidation of Today, Inventory, History and notification schedule

### C3 — Caregiver Today and inventory

1. Today monitoring and status aggregation
2. Individual proxy record/delete
3. Caregiver bulk recording of older missed slots without patient window restriction
4. PRN recording/deletion
5. Mutation-success/follow-up-refresh-failure preservation
6. Inventory list/filter/detail/enable/quantity/adjust/refill
7. Low-stock badge propagation

### C4 — Caregiver history, PDF, settings and account lifecycle

1. Month/day history and mutation freshness
2. Remote push exact date/slot navigation/highlight
3. Retention role differences
4. PDF presets/custom validation/on-device generation/share
5. Push enable/disable and token lifecycle
6. Legal/support, logout and server-first account deletion

### X1 — Analytics, privacy and cross-platform hardening

1. Firebase Analytics with collection off by default
2. Explicit consent and reset-on-disable
3. Exact fixed-enum event parity; no identity, patient, medication, dose, inventory, date/time, free-text or token parameters
4. Preview/test/`disableAnalytics` suppression
5. DebugView, Realtime, Events and Explore verification procedure
6. FCM process-death routing, offline behavior, app links and Android backup/data extraction rules
7. Google Play billing remains out of scope until a Play-specific backend claim contract is approved; do not send StoreKit payloads from Android

### V1 — Release verification

1. Full automated regression for debug and release variants
2. Physical device matrix, TalkBack, 2.0 font, dark mode, notification delivery/taps, Doze and lifecycle
3. Performance and network-failure runs
4. Security/privacy review and dependency scan
5. Data safety and health-app declarations
6. Signed internal/closed test, feedback repair and rollout plan

### Current checkpoint — C97 (2026-08-17)

- R0–C4 and the automated portion of X1 are implemented against published `main@432b34c`.
- C62 reopened the initial C61 conclusion with a direct source audit and closed the expandable History, Today summary, medication form and inventory gaps. C63 then corrected the tutorial implementation after direct comparison with `PatientTutorialSampleView`, `CaregiverTutorialSampleView` and `GuidedTutorialOverlay`: Android now uses dedicated published sample screens for all 14 steps, exact fixed copy/data and the compact role-colored guide card. The current suite passes 202/202 JVM tests, Lint and 272/272 UI tests on API 26/33/35.
- C64 audits all 69 parity rows against current code/evidence: 63 implementation rows are complete and six external rows remain. It closes the only new local finding by replacing medication-form validation strings and inventory-calculation presentation text from the data layer with typed values formatted by Android resources. Debug/Release builds, Release APK compatibility, Play assets and the affected cross-API slice pass after the correction.
- C65-C68 execute the available A302SH/API 35 physical slice. C68 closes local primary/secondary, foreground/background, task-removed tap, forced Doze and wait-through-target cancellation with generic slot-only content through a Release-excluded Debug diagnostic. The final gate passes 273/273 Compose and 206/206 JVM tests, Lint, both APK assemblies, Release compatibility and Play assets.
- C69 extends the same device slice without source or health-data mutation: exact legal URLs and Back restoration pass from Patient/Caregiver Settings, live ephemeral linking-code copy/chooser opens without sending, denied/force-stop/uninstall reminder branches remain silent through their windows, and local App Standby is documented as a single delivery deferred until exit.
- C70 executes the safe read-only `XP-007` transition on the dedicated QA Caregiver: online baseline, same-process cached offline retention with stale/Retry, cold offline role restoration with initial recovery actions, and validated-Wi-Fi Retry recovery all pass. The run exposed a missing Caregiver Today patient-list Retry action; the production UI and regression test were corrected. Final gates pass 274/274 Compose, 206/206 JVM, Lint, Debug/Release assembly, Release APK compatibility and Play assets.
- C71 proves the same-installation role-isolation contract on the configured Debug artifact. Caregiver logout persists through force-stop/relaunch and later reauthentication; one process-only ephemeral link code enters Patient mode; Patient logout then makes Caregiver require account login and Patient require a new code. Neither role surface exposes the other's data, and the device ends at role selection with both sessions logged out.
- C72 closes the Patient-link OEM IME gap discovered by direct source/device audit. The six-digit numeric field now exposes `Done`, submits only when exactly six digits and not loading, and remains inert for incomplete/loading states. A302SH production-runtime testing with a non-existent code reaches the typed expired/not-found response without tapping the button; five digits remain local and disabled. Final gates pass 275/275 Compose and 206/206 JVM tests, Lint and Debug/Release assembly.
- C73 closes the remaining safe Caregiver form input slice. Patient creation now exposes guarded `Done`; medication basic fields explicitly traverse name -> strength -> unit -> dose, supply days -> initial inventory, and both group terminals clear focus without saving. A direct A302SH production-account pass exercises the medication path with a local-only draft and proves the visible medication-list count is unchanged. The audit also found and fixed system Back leaving the editor/app instead of returning to the medication list. Patient-create Done/guarding and Back behavior pass as physical instrumentation fixtures without creating or deleting server data. Final gates pass 278/278 Compose and 206/206 JVM tests, Lint, Debug/Release assembly, Release APK compatibility and Play assets.
- C74 closes the local single-flight/cancellation implementation gap exposed by auditing the still-open physical destructive/network matrix. Patient individual/bulk/PRN/revoke, Caregiver Today individual/delete/bulk/PRN, medication save/delete, inventory, patient/create/settings/link/destructive, and History backfill mutations now share one nonblocking repository mutation boundary per owner. Cancellation clears the visible busy state and releases the boundary, but never retries or reports success. Six high-latency regression families prove cross-type duplicate rejection, no automatic replay, local-state preservation and explicit manual retry. Confirmed medication core writes publish freshness before regimen reconciliation. The safe A302SH synthetic UI suite passes 278/278; Debug JVM 212/212, Release JVM 209/209, Lint, both APK assemblies, Release compatibility and Play assets pass. Real interrupted-write ambiguity and server idempotency remain V01 physical/API evidence.
- C75 audits the server boundary behind that client single-flight contract. Scheduled individual-dose creation now assigns side-effect ownership to the sole successful natural-key insert, while PRN and caregiver inventory-adjust requests accept a nullable UUID-v4 client mutation identifier. Android retains one identifier for the same in-process uncertain PRN/refill/correction operation and sends a new identifier for changed intent; legacy iOS requests remain valid. API replay/race/validation tests and Android body/retry tests pass. Full gates pass API 332/332, Debug JVM 216/216, Release JVM 213/213, Lint, both APK assemblies, Release compatibility, Play assets and A302SH synthetic UI 278/278. The migration is committed but deliberately not deployed from this local gate; real interrupted-network behavior remains V01 evidence.
- C76 executes the privacy-first Firebase gate instead of treating configuration as an indefinite external placeholder. The Android app is registered for the production package and its four values live only in GitHub Actions secrets or ephemeral local environment. Physical testing exposed that manual `FirebaseOptions` initialization alone leaves Analytics disabled with `Missing google_app_id`; Gradle now generates the standard Firebase resources while manifest collection and FCM auto-init remain off. A302SH proves pre-decision/refusal suppression, consent-on fixed-enum upload, both-role shared toggles, reset/resume, no custom identity/health/free-text keys, no Firebase user ID, DebugView `tab_name` inspection and Realtime aggregation. Final gates pass Debug JVM 216/216, Release JVM 213/213, Lint, both APKs, Release compatibility, Play assets and A302SH synthetic UI 278/278. Processed Events/Explore and FCM remain external evidence.
- C77 reaudits the FCM sender before generating physical traffic and finds that missed-dose delivery still supplied the iOS patient-specific notification envelope to Android. `DOSE_MISSED` now follows the existing `DOSE_TAKEN` platform branch: Android receives only strict navigation data with high priority and renders generic local copy, while iOS remains unchanged. The regression proves the Android data payload excludes the display name. Targeted push tests pass 16/16 and the complete API gate passes 333/333, typecheck and ESLint; physical FCM remains V01.
- C78 adds a reproducible Firebase Messaging handshake preflight without exposing or retaining an FCM token. Its Debug-only receiver requires `android.permission.DUMP`, reports status codes only, disables auto-init, deletes the Firebase token and clears app-owned token state; Release manifest inspection proves it is absent. A configured A302SH returns `TOKEN_READY` then `CLEANUP_READY`, a second cleanup succeeds, and uninstall leaves no app package. Debug JVM 216/216, Release JVM 213/213, Lint, Release assembly/compatibility and Play assets pass. Authenticated backend registration and actual FCM delivery remain V01.
- C79 executes the next real FC-001 boundary with the dedicated QA identity. Caregiver login/session restore, permission and the production Settings switch reach Firebase token acquisition, but live production responds `422` because published `main@432b34c` still permits only `platform=ios`; the route validates before upsert, so no device row is created. `android-dev` already permits `android` and its API/CI coverage passes under C77, but branch isolation intentionally leaves production unchanged until the final merge/deploy gate. The UI exposes sync failure, OFF takes effect locally, diagnostic cleanup removes token state, and app/session/temp artifacts are removed. Android must never fall back to `platform=ios` because that would select the wrong server privacy envelope.
- C80 performs the delayed production Analytics observation without generating new app traffic or changing Console configuration. The report's Platform filter now includes `android`, the Android 1.0.6 application row is processed successfully with 100% available data, and the C76 safe fixed-schema rows (`caregiver_tab_viewed`, `patient_tab_viewed`, `screen_viewed`, `tutorial_step_viewed`) are present. Their displayed counts remain property-wide and are not represented as Android-only counts. Processed Events is complete; temporary privacy-reviewed Explore creation/removal remains an explicit Console-write gate.
- C81 closes the remaining automatable navigation/focus assertions before assisted TalkBack: Patient navigation gains stable diagnostic tags, both role tab bars prove ordered merged labels, `Role.Tab` and exact selected state, and both tutorial shells prove pane titles while their live navigation trees are absent. A monolithic 280-test physical run exposed an OEM native Scudo crash after about three minutes; the implicated IME test passed alone, so the final matrix runs in four bounded shards. Those shards pass 66/66, 59/59, 79/79 and 76/76 after adding an existing-style dialog-readiness wait to one inventory test. Debug JVM 216/216, Release JVM 213/213, Lint and Release assembly also pass. Spoken order, one-finger focus, double-tap and two-finger scrolling remain non-automatable physical evidence and are not promoted.
- C82 turns that bounded runner into a checked-in, disposable-target gate. `android/scripts/run-connected-ui-shards.sh` validates a selected adb target and shard parameters, refuses to replace an existing app or test-package installation, supports a single-shard resume, and removes app/test packages on success, failure or interruption. Clean API 26, 33 and 35 AVDs each pass 66/66, 59/59, 79/79 and 76/76 (280 per API, 840/840 total). Debug JVM 216/216, Release JVM 213/213, Lint and Release assembly pass. One API-26 Gradle daemon stalled before APK installation; stopping the daemon and resuming only shard four passed 76/76. No test failure or app-data mutation occurred. Emulator evidence is not promoted to physical-device, spoken TalkBack, notification-delivery or signed-Play acceptance.
- C83 rechecks remote branch drift before the next release slice: `origin/main` remains the published `432b34c` baseline, while the two commits unique to `origin/staging` affect iOS Analytics/Xcode CI and introduce no new Android/API contract. They are not mechanically merged. Android CI now validates the C82 runner shell/help/invalid-shard boundary and runs both Debug 216/216 and Release 213/213 JVM suites, followed by Debug build, Lint, Release compatibility and Play assets. Local production-runtime and upload-signing checks still fail closed when their approved secret inputs are absent; the unsigned Release APK compatibility gate passes. Hosted CI is recorded separately from the 840-test emulator and physical-device matrices.
- C84 hardens the last client-runtime credential boundary before signed Play work. The former `SUPABASE_ANON_KEY` length-only check could accept a privileged `sb_secret_...` or legacy `service_role` value and compile it into the app. The validator now permits only the current publishable prefix with a constrained opaque body or a decodable three-segment legacy JWT whose issuer is `supabase` and single role is `anon`. Synthetic regression fixtures prove publishable/anon acceptance and secret/service-role/wrong-issuer/random/malformed rejection; the task is a dependency of production-runtime verification and a dedicated CI step. Debug JVM 216/216, Release JVM 213/213, Lint, Release compatibility and Play assets pass locally without reading a real key.
- C85 closes the remaining automatable signed-AAB artifact boundary. `bundleSignedRelease` now requires the registered upload-certificate SHA-256, builds the signed bundle only after runtime/signing/APK/assets gates, then verifies AAB structure, complete JAR signature coverage, exactly one signer and exact certificate match before succeeding. A synthetic JDK-only contract passes a correctly signed AAB and rejects wrong fingerprint, unsigned and partially signed artifacts. A synthetic Gradle integration exposed and fixed the prior unsigned-only APK filename assumption; both signed `app-release.apk` and unsigned `app-release-unsigned.apk` now use explicit paths. The full synthetic bundle flow passes, missing/mismatched fingerprint fails, and `clean` removes every generated key/artifact. Normal unsigned Debug/Release JVM, Lint, APK compatibility and Play assets pass after cleanup.
- C86 closes the automatable Release SDK/Data safety input boundary. `verifyReleaseSdkPolicy` resolves the actual `releaseRuntimeClasspath`, requires the approved Firebase Analytics/Messaging/Installations capabilities, rejects unapproved ads, billing, Install Referrer, Crashlytics/Performance and third-party attribution/analytics SDKs, and writes a sorted inventory. Its synthetic contract explicitly permits Firebase Analytics' current `play-services-ads-identifier` and Privacy Sandbox support transitives: artifact names alone do not prove advertising use, so Release APK advertising/attribution permission exclusion remains a separate hard gate. The policy is required by Release APK compatibility, `bundleSignedRelease` and hosted CI. Both JVM variants, Lint, Release APK compatibility and Play assets pass; the exact signed AAB, current Firebase disclosures and Play Console answers remain external.
- C87 prevents the C86 evidence basis from silently drifting between machines. Strict Gradle dependency locking is activated only for `releaseRuntimeClasspath`; its generated `app/gradle.lockfile` pins the complete external graph used by Data safety review. The policy task declares that lock as an input, succeeds with the checked-in 174-entry lock state and fails before dependency resolution when the state is absent. An intentional dependency update must use `--write-locks`, review the lock diff and rerun SDK policy, APK permission and vendor-disclosure review. Debug/test configurations remain unlocked.
- C88 closes the automatable merged Release-manifest boundary. `verifyReleaseApkCompatibility` now parses the manifest from the actual APK and rejects debug/test/shell-profileable flags, backup or cleartext relaxation, missing backup/data-transfer rules, compressed native-library drift, Firebase Analytics/FCM auto-init, any permission outside the six-item reviewed set, duplicate or unexpected exported components, weakened component permission guards, FileProvider drift and any authentication URI/action/category/autoVerify drift. The only exported surfaces are MainActivity, the `SEND`-guarded Firebase receiver and the `DUMP`-guarded AndroidX Profile Installer receiver. Eleven synthetic pass/fail contracts run separately in CI, and C95 updates the current actual Release contract to the canonical verified HTTPS host plus the fallback custom scheme. The release-owner-signed AAB and Play scan remain external.
- C89 extends the artifact boundary from APK to the Play-upload container. A strict-locked bundletool 1.18.0 classpath validates the generated AAB, dumps its protobuf base manifest and feeds it through the same C88 policy. A pure structure contract rejects missing bundle/config/dex entries, any unreviewed feature module and embedded `.env`, Firebase config, service-account or private-key/keystore files. The actual unsigned AAB passes as base-only with four DEX files, eight native libraries and the exact six-permission/three-exported manifest; C95 narrows its current auth-link set to two. Its SHA-256 is printed for evidence. `bundleSignedRelease` now requires this content gate before signature/certificate acceptance. Release-owner signing, Play processing/scan and installed-track evidence remain external.
- C90 closes the automatable pre-build upload-key boundary. `verifyUploadKeystore` runs after structural signing-input validation but before `bundleRelease`; it opens the configured store and alias, requires a private-key entry, compares its leaf SHA-256 with `PLAY_UPLOAD_CERT_SHA256`, proves the supplied key password by signing and verifying an ephemeral JAR, and emits no password. One accepted and seven rejected synthetic fixtures cover certificate, alias, store/key password, fingerprint, path and certificate-only drift. A complete synthetic `bundleSignedRelease` passes all 72 tasks through the same pre-build key, APK/AAB policy and post-build signer check; a clean ordinary regression then passes both JVM variants, Lint and all current unsigned release gates. The synthetic key/artifact is not release-owner evidence and is removed by `clean`.
- C91 closes the automatable release-evidence identity boundary. `bundleSignedRelease` now ends at `generateSignedReleaseEvidence`, which reruns every time and refuses any uncommitted Android or consumed role/icon/store input. Its policy validates the canonical package, positive version code, semantic version name, full source SHA, base-only DEX-bearing AAB, exact configured/actual signer and nonempty locked dependency evidence. Only after the full existing graph passes does it atomically write `app/build/reports/play-release-evidence.json` with commit/branch/baseline, app/SDK versions, exact AAB/manifest/certificate hashes, structure counts, dependency-lock/SDK-inventory hashes and the gate set. One accepted and eleven rejected pure fixtures cover each fail-closed dimension; a dirty-tree synthetic signed run is deliberately rejected after signature verification. The clean committed synthetic run and hosted CI are recorded separately in C91 evidence.
- C92 closes the automatable handoff-pairing boundary. `bundleSignedRelease` ends at `preparePlayReleaseHandoff`, which reparses C91 evidence and rechecks schema, clean source, production identity/version, source AAB name/hash, upload-certificate form, base-only module and the exact ordered ten-gate set. It atomically creates a version/code/commit-named directory containing exactly one correspondingly named AAB, the byte-identical ledger and a two-entry `SHA256SUMS`; an existing target is accepted only when all three files still match. The standalone contract proves idempotent acceptance and rejects source/packaged AAB tampering, dirty source, wrong package, malformed signer, feature module and missing gate without weakening production ownership or Play evidence requirements.
- C93 closes the automatable AAB-derived install-surface boundary. Strict-locked bundletool builds a universal APK Set from the exact AAB with an ephemeral synthetic signer; extraction fails unless the archive contains exactly `toc.pb` and `universal.apk`, has no duplicate entries and the inner APK is nonempty, valid and DEX-bearing. The extracted APK must use the ephemeral certificate, then passes the same production manifest, package/minimum/target SDK, six-permission, three-exported, current two-auth-link, advertising exclusion and 16 KB ZIP/native ELF policy as the assembled Release APK. Its output is explicitly test-only and excluded from C92 handoff; Play app signing, optimized device splits and track installation remain external.
- C94 closes the next automatable/available-physical device-split boundary. One complete APK Set is generated from the exact AAB with an ephemeral signer, then bundletool selects four APKs—base master, ABI, `ja`, and density—for API 26 arm64/xhdpi, API 33 x86_64/xxhdpi and the observed API 35 A302SH arm64/280dpi specification. The contract accepts both bundletool's `base-master.apk` and SDK-targeted `base-master_N.apk` identity, rejects ten malformed selections, and requires exactly one DEX-bearing master plus only the expected ABI's two native libraries. Every selected split must have the production package, the same synthetic certificate and 16 KB ZIP/native ELF alignment; the base master repeats the complete manifest/SDK/permission/App Links policy. A safety-tested installer refuses an existing app and cleans up failures. On A302SH the exact four installed base/config paths and version 1/1.0.6 pass, then uninstall leaves the package absent without launching it. Play app signing, Play-generated splits and track installation remain external.
- C95 closes the automatable production/installed App Links acceptance boundary without claiming deployment. A live read-only check confirms the production `www` endpoint still returns cached Vercel HTTP 404 because the fail-closed route exists only on `android-dev`, not `origin/main`. The production verifier refuses redirects, non-200/JSON, cache drift, extra associations/fields, wrong relation/package and any fingerprint set that differs from the Play **app-signing** certificate set. The installed verifier additionally refuses a non-Play installer, package-manager signer drift, any host other than `www.okusuri-mimamori.com`, and every state except `verified`. Direct source inspection found the Release manifest also declared the redirecting apex host; Android requires a redirect-free file on every declared host, so the manifest and artifact policy now expose only the canonical `www` host used by the API and confirmation email. Synthetic contracts accept five exact surfaces and reject the current 30 malformed/web/device states. Production remains open until the Android API contract is merged/deployed with the owner-supplied Play app-signing fingerprint and the Play-installed task passes on a physical device.
- C96 closes the automatable committed merge-surface boundary without merging branches. After a fresh fetch, `origin/main@432b34c` is still the exact merge base and ancestor of `android-dev`; the pre-C96 input audit counted 198 commits and 1,162 files across only Android, `docs/android`, one Android CI workflow, reviewed Android `.gitignore` additions and an exact 30-file API allowlist, with zero `ios/` changes. The verifier prints fresh counts on every run and rejects a stale/diverged main, deletion/rename, unexpected root/workflow/API/docs/iOS scope, unreviewed ignore override, private signing/config material, generated directories, symlinks/submodules, a blob over 2 MiB, more than 1,250 files or a changed tree over 400 MiB. The input high-fidelity evidence tree measured 385,664,936 bytes; its 842 PNG references are retained rather than recompressed or removed. One accepted and ten rejected temporary Git repositories prove the policy independently. Hosted CI fetches full ancestry before checking; success proves committed isolation, not release acceptance or authority to merge/deploy.
- C97 closes the local migration-upgrade semantics behind C75 without deploying it. API CI statically permits only the reviewed two nullable/default-free `TEXT` columns and two patient-scoped unique indexes, refuses remote databases by default, and applies the exact migration to an isolated PostgreSQL schema containing three legacy PRN and three legacy inventory-adjustment rows. It proves all six identities and null values survive, legacy duplicate-null writes remain valid, the same mutation UUID is valid for different patients, same-patient duplicates fail on the intended constraints, and the temporary schema is removed. Eleven malformed/unsafe fixtures are rejected. C96 is intentionally extended to exactly two reviewed workflows and 32 API files for this gate. Production migration state, lock/window assessment, deploy execution and real interrupted-network behavior remain V01 evidence.
- The repeatable privacy-first Firebase procedure is `firebase-analytics.md`; DebugView/Realtime evidence is `evidence/h07-20260817/README.md`, and delayed processed Events evidence is `evidence/c80-20260817/README.md`. Explore remains pending.
- The physical release procedure is `physical-device-matrix.md`; its row-level evidence still requires old-supported, current-reference and non-Google OEM targets plus the exact signed Play Internal artifact.
- C65-C97 cover one non-Google OEM/API 35 Debug target, a current 840-test API 26/33/35 emulator regression, aligned hosted CI, privileged-client-key rejection, synthetic end-to-end upload-keystore/signed-AAB verification, machine-readable exact-artifact evidence, checksum handoff and AAB-derived universal/device-split install-surface verification, including a cleaned-up physical A302SH split install, strict production/Play-installed App Links and committed main-merge-surface gates, strictly locked Release SDK policy, fail-closed merged Release-manifest policy and validated AAB content path, local reminder lifecycle, safe browser/link-code I/O, IME/offline/role isolation, mutation hardening, local server-idempotency and isolated legacy-upgrade contracts, live privacy-first Analytics through processed Events, both privacy-safe Android FCM envelopes, non-retaining token acquisition/cleanup, the authenticated production API boundary and automated navigation/tutorial accessibility semantics. V1 remains open for an old-supported physical device and Google/reference device, production migration/destructive interruption, complete spoken TalkBack, Analytics Explore, Android API deployment plus App Links/FCM registration/delivery, release-owner-signed AAB/Play testing, Console declarations and the final pre-merge main rebaseline.

## 7. Automated quality gates

Run from `android/` unless noted:

- Unit/contract tests for debug and release variants
- Compose instrumentation tests on the supported emulator API range
- `lint`
- `assembleDebug` and release-like assembly
- `git diff --check`
- Backend contract/integration tests when Android depends on changed server semantics

High-risk flows additionally require tests for process death, concurrent refresh, double tap/idempotency, stale follow-up response, Tokyo date boundary, permission denial and accessibility tree state.

## 8. Phase exit gate

A phase exits only when:

- Every scoped row has current automated evidence.
- No scoped row remains `RECHECK_REQUIRED`, `PARTIAL` or `BLOCKED` without an approved phase boundary.
- Required screen states have matched current iOS captures.
- Debug and release-like build/test/lint pass.
- Safe live API smokes pass for the phase's mutations and error families.
- Emulator and required physical-device checks pass.
- No P0/P1 defect remains.
- Documentation names exact evidence and source/build SHAs.

## 9. Merge-to-main gate

Android is ready to merge only when:

- All release-scope rows are `VERIFIED`.
- The final rebaseline against then-current `main` produces no unresolved contract/UI drift.
- Release signing, Firebase Android app configuration and Play Console declarations are production-ready.
- Closed testing meets the agreed stability threshold.
- Main merge contains Android files/docs only plus intentionally shared changes already present on main; it does not backflow stale iOS/API files.
