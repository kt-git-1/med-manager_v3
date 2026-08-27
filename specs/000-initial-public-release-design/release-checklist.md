# Initial Public Release Checklist

## Environment

- iOS `API_BASE_URL` points to the production API domain.
- iOS `SUPABASE_URL` and `SUPABASE_ANON_KEY` point to the production Supabase project.
- Vercel Production `SUPABASE_URL` and `SUPABASE_ANON_KEY` point to the same production Supabase project as the iOS build.
- Vercel Production `SUPABASE_SERVICE_ROLE_KEY` is set and is never embedded in iOS.
- Vercel Production `FCM_SERVICE_ACCOUNT_JSON` is set if caregiver push notifications are enabled.
- Supabase Auth confirm-signup email uses the production template in `docs/operations/supabase-auth-email-template.md`.

## App Store

- App Privacy answers match the implemented data handling.
- Privacy policy URL: `https://okusuri-mimamori.com/privacy`
- Terms URL: `https://okusuri-mimamori.com/terms`
- Support URL: `https://okusuri-mimamori.com/support`
- Support contact email: `support@okusuri-mimamori.com`
- `PrivacyInfo.xcprivacy` is included in the app target.

## Manual Smoke

- Caregiver signup/login works against production Supabase.
- Caregiver can create a patient and issue a linking code.
- Patient can link with the code and record a dose.
- Caregiver can see the dose status.
- From caregiver history, a missed past dose can be proxy-recorded and immediately appears in Today, history, streak, and inventory.
- From caregiver history, patient- and caregiver-recorded doses can be cancelled after confirmation; the cancelled state remains visible and no patient push is sent.
- Cancelling restores the quantity captured when the dose was recorded exactly once. Repeating the cancellation must not restore inventory twice; legacy records without a captured quantity must not guess a restoration amount.
- Every changed behavior has paired positive and negative acceptance cases; a passing positive case alone is not sufficient.
- Boundary times, retries/idempotency, and failure/recovery paths are covered for every time-sensitive or state-changing feature.
- The release decision records evidence for each acceptance category; any unverified category keeps the release on hold.
- Notification permission prompts and local reminders behave as expected.
- For each notification type, verify both delivery when its condition is met and suppression when its condition is not met.
- A recorded dose does not create a missed-dose `PushDelivery` or a caregiver notification after the cutoff.
- An unrecorded dose creates exactly one missed-dose delivery after the cutoff, and tapping it opens the correct patient/date/slot.
- Caregiver can delete the account from settings, and the session returns to mode selection.

## TestFlight and Android interoperability

- Every future TestFlight candidate is tested with the corresponding Android distributed Staging build before the cross-platform release gate is marked complete.
- Both apps must use the same environment: Staging TestFlight pairs only with the Android `staging` flavor, and Production TestFlight pairs only with an Android production-connected build.
- Test iOS as caregiver with Android as patient: create the patient, exchange the six-digit linking code, record on Android, and verify iOS Today, history, recorder, and inventory.
- Swap the roles and test Android as caregiver with iOS as patient: link again, proxy-record on Android, and verify iOS Today and history identify the family recorder.
- Verify the reverse record direction from iOS patient to Android caregiver as well.
- Cover scheduled doses, multiple-medication bulk recording, partial recording, insufficient inventory, PRN recording, and cancellation without duplicate records or double inventory decrements.
- Pull to refresh after each cross-platform write and confirm that time, status, medication-type count, recorder, and inventory agree on both platforms.
- Fully terminate and relaunch both apps; the selected mode, caregiver or patient session, link, and history must be restored.
- Record the iOS and Android version/build, OS/device, assigned role, operation direction, result, and reflection time in the TestFlight verification record.
- Use only dedicated synthetic QA accounts and patients. Never place credentials, session tokens, linking codes, or identifiers in documentation, logs, chat, or commits.
- Any P0 or P1 interoperability failure keeps both candidate builds on hold until the complete gate passes again on newly distributed builds.

## iOS 1.0.8 Build 64 Staging gate

### Artifact and environment

- [x] `MARKETING_VERSION=1.0.8` and `CURRENT_PROJECT_VERSION=64` in both `project.yml` and the Xcode project.
- [x] Archive Info.plist reports version `1.0.8 (64)`, Japanese development region, Staging API URL, and no embedded server secrets. (2026-08-27: archived app reports `1.0.8 (64)`, `ja`, and `https://staging-api.okusuri-mimamori.com/`; no server-secret values found)
- [x] Staging Supabase has the soft-cancel migration and Staging Vercel is healthy on the matching commit. (2026-08-27: migration applied; API fix commit `83772a5` deployed `READY`)
- [x] App Store Connect processing state is `VALID`, encryption declaration is complete, and the internal tester group can install Build 64. (2026-08-27: iPhone 13 installation confirmed)

### Automated API and database checks

- [x] Prisma schema validates; API typecheck, production build, lint, and all unit/integration tests pass. (2026-08-27 rerun: 70 files / 329 tests)
- [x] Active schedule/history/streak/report calculations ignore cancelled records while caregiver history retains cancellation metadata.
- [x] New single and bulk records persist the exact consumed quantity.
- [x] Cancellation and inventory restoration commit atomically.
- [x] Repeated or concurrent cancellation cannot restore inventory twice.
- [x] Legacy records without captured quantity are cancelled without guessing an inventory restoration amount.
- [x] Re-recording a cancelled schedule creates one active result and decrements inventory once. (API/DB sequence and Android patient re-record both verified)
- [x] Cancellation does not invoke the dose-taken push path.
- [x] Account deletion and patient unlink cleanup still remove the new cancellation-bearing records safely.

### Caregiver history correction flow

- [x] A patient-recorded dose shows actual time, recorder, and `記録を取り消す`. (Android patient record reflected in iPhone TestFlight caregiver history)
- [x] A caregiver-recorded dose shows actual time, recorder, and `記録を取り消す`.
- [x] Tapping cancel presents drug, scheduled time, actual time, recorder, and inventory explanation before mutation. (iPhone TestFlight XCUI, Build 64)
- [x] Dismissing the confirmation makes no API, history, inventory, streak, or notification change. (iPhone TestFlight XCUI plus public Staging API state/inventory verification)
- [x] Confirming changes the card to `取り消し済み` and shows cancellation time. (iPhone TestFlight XCUI, Build 64)
- [ ] Today, month summary, selected-day summary, streak, report, and inventory refresh consistently after cancellation.
- [x] The patient receives no push notification for cancellation.
- [x] A cancelled dose can be recorded again with `もう一度代理で記録`. (iPhone TestFlight XCUI; record, cancel, and re-record sequence)
- [x] A past missed dose can be recorded with `代理で記録`; actual time is the operation time and recorder is family. (public Staging API plus iPhone TestFlight accessibility state)
- [x] Future unrecorded doses cannot be proxy-recorded from history unless they are explicitly in a cancelled state. (iPhone TestFlight XCUI, Build 64)
- [x] Multiple drugs in one slot support partial cancel/re-record without changing the other drugs. (iPhone TestFlight XCUI plus public Staging API/inventory verification, Build 64)
- [ ] Insufficient inventory, expired auth, offline, timeout, and server failure show an error and preserve the pre-operation state. (Insufficient-inventory path passed on iPhone TestFlight and public Staging API; the other failure paths remain open.)

### 1.0.8 UI and synchronization regression

- [x] Caregiver Today header matches the title/name layout used by the other caregiver screens.
- [x] Caregiver and patient Today clearly highlight the same next expected slot.
- [x] Slot colors are morning yellow, noon orange, evening blue, and bedtime purple across Today and history.
- [x] Medication dosage input separates numeric value and unit, supports Japanese labels, and places validation errors beside the field.
- [x] Inventory detail can scroll above the bottom tab on compact iPhones and with larger text.
- [x] Slot-time changes made on iOS appear on Android after refresh/resume, and Android changes appear on iOS after refresh/resume.
- [x] Foreground refresh does not duplicate schedules, records, reminders, or network mutations.
- [ ] Logout, unlink, account deletion, and mode changes clear local reminders, APNs/FCM identity, and stale push preferences. (Build 65 patient logout/mode change cleared delivered local reminders; synthetic caregiver account deletion removed its remote Push device and all app/Auth data. Patient unlink remains open.)

### Core smoke and device matrix

- [ ] Fresh install: caregiver signup/login, patient creation, linking code issue, patient link, medication registration, and Today schedule generation.
- [x] Upgrade install from Build 63 preserves caregiver login, patient link, medication, history, inventory, preferences, and notification authorization.
- [ ] Patient scheduled recording, caregiver slot proxy recording, PRN recording/cancellation, inventory refill/correction, pull-to-refresh, and five-tab navigation pass.
- [ ] Cold launch, background/resume, force quit/relaunch, offline launch/recovery, and expired-token refresh pass.
- [x] iPhone compact width, current large iPhone, iPad, portrait, and supported large Dynamic Type have no clipped actions or hidden content. (2026-08-27: device matrix passed; patient Today, caregiver Today, and inventory editing also passed at accessibility-extra-large)
- [x] VoiceOver labels distinguish scheduled time, actual time, recorder, cancellation, and destructive confirmation actions.
- [x] Record/proxy/cancel interactions meet the release response-time thresholds on Staging and do not leave a long-running updating overlay. (API create/cancel/re-record 0.206–0.495 s; physical-device navigation/refresh measurements recorded)
- [ ] Real local reminder delivers when unrecorded and is suppressed when recorded; caregiver missed-dose push delivers once and is suppressed after timely recording. (Build 65 real local delivery/suppression passed; the caregiver timely-recording suppression half remains open.)
- [x] iOS caregiver ↔ Android patient and Android caregiver ↔ iOS patient scheduled-dose interoperability passes on the same Staging account. (PRN/partial/insufficient-inventory extensions remain open in the matrix above.)

### Build 64 execution record

- [x] iOS unit tests: 215 executed, 35 known environment/unfinished-spec skips, 0 failures.
- [x] iOS UI retry set: medication supply calculation, caregiver history grouping/cancellation UI, caregiver tutorial, and patient medicine expansion all passed; the empty-inventory route remains skipped because that launch fixture is unavailable.
- [x] The first full UI run was invalidated by an iOS 26.5 Simulator accessibility-runtime runner exit. The five affected cases were rerun after a Simulator reboot: four passed and one retained its explicit fixture-unavailable skip, with zero assertion failures.
- [x] Simulator smoke against the deployed Staging API.
- [x] Physical-device TestFlight smoke, performance measurements, dose-taken and missed-dose Push, and upgrade from Build 63. Android patient → iOS caregiver interoperability passed; the full bidirectional matrix remains open above.

### Build 64 rerun evidence (2026-08-27)

- Public Staging history mutation: missed → caregiver record → cancel → repeated cancel → re-record passed; inventory sequence was `18 → 17 → 18 → 18 → 17`.
- QA found and fixed a real regression where patient slot bulk recording skipped a cancelled row. Commit `83772a5` revives only cancelled rows under a concurrency guard; the new regression case brings the API suite to 329 passing tests.
- Android Staging patient re-recorded the cancelled evening slot. iPhone 13 TestFlight history then showed actual time, `本人が記録`, and the cancellation action; inventory changed once from 18 to 17.
- Android caregiver bulk-recorded two bedtime medications; iPhone TestFlight patient Today refreshed to the same actual time and two medications. After cancellation, iPhone patient re-recorded both in 4.028 s and Android caregiver Today refreshed to `本人が記録`. A final Android caregiver re-record was reflected back on iPhone patient Today.
- Real caregiver dose-taken Push arrived on iPhone from the Android patient write. The matching delivery row was created exactly once.
- Scoped real missed-dose Push arrived on iPhone with the Japanese bedtime message. A second send of the same patient/date/slot created no second delivery.
- Android Staging offline launch showed `取得に失敗しました`; after network restoration and relaunch, Today recovered with the same actual times and next bedtime slot.
- The temporary synthetic caregiver was logged out on iPhone, deleted through the public Staging account-deletion endpoint, and verified absent from Supabase Auth, patient, medication, dose-record, and push-device storage. Local QA Keychain items and Android emulator Staging state were cleared.
- iPhone 13 TestFlight navigation measurements: patient history 1.626–1.698 s, Today 1.627–1.662 s, settings 1.636–1.658 s, pull refresh 2.363 s, relaunch 2.390 s; caregiver tabs 1.753–1.922 s.
- Build 64 archive inspection passed for version/build, Japanese development region, Staging API URL, and absence of embedded server-secret values. Three high-risk screens passed at accessibility-extra-large on iPhone 17e Simulator.
- Future unrecorded history, a two-medication partial cancel/re-record, and insufficient-inventory rejection passed through gesture-driven iPhone TestFlight XCUI with API/inventory state verification.
- An unrecorded real local reminder delivered, but Build 64 still delivered the recorded bedtime reminder when the app was backgrounded immediately after recording. This exposed an async rebuild race, so Build 64 is not merge eligible.

### Build 65 notification-race follow-up

- [x] Immediately remove the recorded slot's primary and repeat local-reminder identifiers before starting the asynchronous full notification-plan rebuild.
- [x] Focused patient-Today performance/regression tests pass, including immediate reminder cancellation without waiting for the background rebuild.
- [x] iOS unit tests: 215 executed, 35 known environment/unfinished-spec skips, 0 failures.
- [x] Archive reports `1.0.8 (65)`, Japanese development region, Staging API URL, valid APNs entitlement, and no embedded database/JWT/service-role secret values.
- [x] Upload Build 65, set the existing non-exempt-encryption declaration, confirm Apple state `VALID`, and install it from TestFlight on the physical iPhone.
- [x] On a physical iPhone, verify one unrecorded real local reminder delivers. (23:33 evening reminder, XCUI screenshot evidence)
- [x] On the same physical iPhone, record a later slot and immediately background the app; verify neither its primary nor repeat reminder delivers. (23:37 bedtime reminder absent through 23:38, XCUI count 0; API confirmed the patient record)
- [x] Upgrade from Build 64 preserves the synthetic patient session and Build 65 patient navigation/refresh/relaunch smoke passes. (history/Today/settings 1.621–1.667 s; refresh 2.385 s; relaunch 2.407 s)
- [x] Build 65 caregiver login and Today/history/inventory/medication navigation smoke passes. (tab transitions 1.781–2.018 s; history fixed-scroll rendering captured on the physical iPhone)
- [x] Delete the synthetic caregiver through the Build 65 confirmation UI, return to mode selection, and verify Supabase Auth, patient, medication, dose-record, and Push-device data plus local QA secrets are removed.
