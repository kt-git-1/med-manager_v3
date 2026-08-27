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

- [ ] `MARKETING_VERSION=1.0.8` and `CURRENT_PROJECT_VERSION=64` in both `project.yml` and the Xcode project.
- [ ] Archive Info.plist reports version `1.0.8 (64)`, Japanese development region, Staging API URL, and no embedded server secrets.
- [ ] Staging Supabase has the soft-cancel migration and Staging Vercel is healthy on the matching commit.
- [ ] App Store Connect processing state is `VALID`, encryption declaration is complete, and the internal tester group can install Build 64.

### Automated API and database checks

- [x] Prisma schema validates; API typecheck, production build, lint, and all unit/integration tests pass. (2026-08-27: 70 files / 328 tests)
- [ ] Active schedule/history/streak/report calculations ignore cancelled records while caregiver history retains cancellation metadata.
- [ ] New single and bulk records persist the exact consumed quantity.
- [ ] Cancellation and inventory restoration commit atomically.
- [ ] Repeated or concurrent cancellation cannot restore inventory twice.
- [ ] Legacy records without captured quantity are cancelled without guessing an inventory restoration amount.
- [ ] Re-recording a cancelled schedule creates one active result and decrements inventory once.
- [ ] Cancellation does not invoke the dose-taken push path.
- [ ] Account deletion and patient unlink cleanup still remove the new cancellation-bearing records safely.

### Caregiver history correction flow

- [ ] A patient-recorded dose shows actual time, recorder, and `記録を取り消す`.
- [ ] A caregiver-recorded dose shows actual time, recorder, and `記録を取り消す`.
- [ ] Tapping cancel presents drug, scheduled time, actual time, recorder, and inventory explanation before mutation.
- [ ] Dismissing the confirmation makes no API, history, inventory, streak, or notification change.
- [ ] Confirming changes the card to `取り消し済み` and shows cancellation time.
- [ ] Today, month summary, selected-day summary, streak, report, and inventory refresh consistently after cancellation.
- [ ] The patient receives no push notification for cancellation.
- [ ] A cancelled dose can be recorded again with `もう一度代理で記録`.
- [ ] A past missed dose can be recorded with `代理で記録`; actual time is the operation time and recorder is family.
- [ ] Future unrecorded doses cannot be proxy-recorded from history unless they are explicitly in a cancelled state.
- [ ] Multiple drugs in one slot support partial cancel/re-record without changing the other drugs.
- [ ] Insufficient inventory, expired auth, offline, timeout, and server failure show an error and preserve the pre-operation state.

### 1.0.8 UI and synchronization regression

- [ ] Caregiver Today header matches the title/name layout used by the other caregiver screens.
- [ ] Caregiver and patient Today clearly highlight the same next expected slot.
- [ ] Slot colors are morning yellow, noon orange, evening blue, and bedtime purple across Today and history.
- [ ] Medication dosage input separates numeric value and unit, supports Japanese labels, and places validation errors beside the field.
- [ ] Inventory detail can scroll above the bottom tab on compact iPhones and with larger text.
- [ ] Slot-time changes made on iOS appear on Android after refresh/resume, and Android changes appear on iOS after refresh/resume.
- [ ] Foreground refresh does not duplicate schedules, records, reminders, or network mutations.
- [ ] Logout, unlink, account deletion, and mode changes clear local reminders, APNs/FCM identity, and stale push preferences.

### Core smoke and device matrix

- [ ] Fresh install: caregiver signup/login, patient creation, linking code issue, patient link, medication registration, and Today schedule generation.
- [ ] Upgrade install from Build 63 preserves caregiver login, patient link, medication, history, inventory, preferences, and notification authorization.
- [ ] Patient scheduled recording, caregiver slot proxy recording, PRN recording/cancellation, inventory refill/correction, pull-to-refresh, and five-tab navigation pass.
- [ ] Cold launch, background/resume, force quit/relaunch, offline launch/recovery, and expired-token refresh pass.
- [ ] iPhone compact width, current large iPhone, iPad, portrait, and supported large Dynamic Type have no clipped actions or hidden content.
- [ ] VoiceOver labels distinguish scheduled time, actual time, recorder, cancellation, and destructive confirmation actions.
- [ ] Record/proxy/cancel interactions meet the release response-time thresholds on Staging and do not leave a long-running updating overlay.
- [ ] Real local reminder delivers when unrecorded and is suppressed when recorded; caregiver missed-dose push delivers once and is suppressed after timely recording.
- [ ] iOS caregiver ↔ Android patient and Android caregiver ↔ iOS patient interoperability passes on the same Staging account.

### Build 64 execution record

- [x] iOS unit tests: 215 executed, 35 known environment/unfinished-spec skips, 0 failures.
- [x] iOS UI retry set: medication supply calculation, caregiver history grouping/cancellation UI, caregiver tutorial, and patient medicine expansion all passed; the empty-inventory route remains skipped because that launch fixture is unavailable.
- [x] The first full UI run was invalidated by an iOS 26.5 Simulator accessibility-runtime runner exit. The five affected cases were rerun after a Simulator reboot: four passed and one retained its explicit fixture-unavailable skip, with zero assertion failures.
- [ ] Simulator smoke against the deployed Staging API.
- [ ] Physical-device TestFlight smoke, performance measurements, notifications, upgrade from Build 63, and Android interoperability.
