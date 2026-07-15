# Android Port Source Baseline

**Baseline date:** 2026-07-15
**Development branch:** `android-dev`
**Reference main commit:** `1cf8aef5d214718a27175ae0e8290e079e66f922` (`Bump iOS build to 38 for production TestFlight`)
**Previous reference:** `1d9d19e5376752d2e224560a9f2c7e950645e22b` (`Bump production TestFlight to build 36`)
**Baseline merge checkpoint:** C31 merge commit containing `main@1cf8aef`

This file pins the source material used to reproduce the current product on Android. A later change on `main` does not silently change the Android contract. It starts a new rebaseline procedure.

## 1. Branch isolation

- Android code and Android port documents are changed only on `android-dev`.
- iOS and API changes are authored and released from `main`/`staging`, not from the Android workstream.
- `main` is merged into `android-dev` only at an explicit rebaseline checkpoint.
- Android is merged back only after the release gates in `android-port-master-plan.md` pass.
- A parity fix must not edit iOS merely to make Android easier to match. A product/API defect is fixed on `main` first and then rebaselined.

## 2. Authority order

When sources disagree, use the first applicable source below and record the disagreement.

1. Backend route/service behavior and passing backend tests at the pinned commit
2. Shipping iOS behavior and passing iOS tests at the pinned commit
3. `ios/MedicationApp/Resources/Localizable.strings` for user-facing Japanese copy
4. Product specifications under `specs/`
5. Android documents in this directory
6. Existing Android behavior

The older feature specifications are intent documents, not always the current runtime contract. For example, `specs/0115-slot-bulk-dose-recording/spec.md` says caregivers do not use bulk recording, while the current API and iOS app do. Android follows the current API/iOS behavior and records the conflict here rather than reviving the obsolete rule.

## 3. Canonical source map

| Concern | Canonical sources |
|---|---|
| App entry, mode restoration, auth routing | `RootView.swift`, `SessionStore.swift`, `SessionStoreTests.swift` |
| HTTP/session behavior | `APIClient.swift`, `APIClientTests.swift`, `APIError.swift` |
| Japanese copy | `Resources/Localizable.strings` |
| Shared visual tokens | `Shared/AppConstants.swift`, `PatientUI`, `CaregiverUI` |
| Patient shell/tutorial/settings | `PatientReadOnlyView.swift` |
| Patient Today | `PatientTodayView.swift`, `PatientTodayViewModel.swift`, Today tests |
| Caregiver shell/tutorial | `CaregiverHomeView.swift` |
| Patient management | `PatientManagementView.swift` and child views |
| Caregiver Today | `CaregiverTodayView.swift`, `CaregiverTodayViewModel.swift`, `TodayCaregiverFlowTests.swift` |
| Medication/regimen | `MedicationListView.swift`, `MedicationFormView*`, medication/regimen routes |
| Inventory | `InventoryListView.swift`, `InventoryDetailView.swift`, inventory routes |
| History/PDF | History views/view model, PDF feature files, history routes |
| Local patient reminders | notification feature files and notification tests |
| Caregiver push | push settings, `DeviceTokenManager`, push routes, push tests |
| Analytics/privacy | `AnalyticsService.swift`, `docs/firebase-analytics.md`, settings consent UI |

## 4. Main changes since the previous Android baseline

The prior pinned source was `main@1d9d19e`. C31 reviewed the complete `1d9d19e..1cf8aef` API/iOS/spec delta and merged it without cherry-picking. The following semantic changes are mandatory recheck items; older Android screenshots and passing tests do not close them.

| Change | Current contract | Android impact |
|---|---|---|
| Public link exchange | `POST /api/patient/link` is sent without Authorization. A failure on this public request must not clear an existing session. A caregiver token is rejected by the backend. | Add per-request auth policy and tests. `AU-002`, `SH-006` are recheck-required. |
| Link error localization | Validation, expired/not-found, auth/forbidden, network and other failures have stable Japanese UI messages. | Remove accidental raw backend/exception copy from link UI. |
| Patient installation safety | iOS discards Keychain-only patient tokens after reinstall using an installation marker. | Android must prove uninstall/reinstall cannot restore a patient token; `allowBackup=false` is necessary but requires device evidence. |
| Patient record follow-up | Successful scheduled individual/bulk recording updates UI immediately, then rebuilds the seven-day reminder plan off the mutation critical path. | Do not wait for reminder maintenance before success feedback. Rebuild only after at least one scheduled record changes. |
| Next-day reminder retention | Rebuilding after a dose must retain valid notifications on the next day and across a month boundary. | Add deterministic boundary tests before changing scheduler code. |
| Cross-tab history freshness | Successful individual, bulk, PRN and delete operations signal that history is stale. History refreshes when active/visible. | Define a shared invalidation revision/event; do not rely only on app restart. |
| Lazy tab lifetime | Patient and caregiver tabs are instantiated lazily and remain alive after first visit. Hidden tabs do not receive taps/accessibility focus. | Navigation must preserve scroll/data/form state and avoid unnecessary reload flicker. |
| Caregiver proxy bulk | Caregivers can bulk-record an older missed slot; the patient-only `-30/+60` recording window does not block caregiver proxy recording. | Separate patient and caregiver eligibility policy. Do not share a UI-only window guard. |
| Caregiver mutation stability | After a successful mutation, a refresh failure preserves rendered data and shows an error instead of replacing the screen with empty/error state. | Repository state must distinguish mutation success from follow-up refresh failure. |
| Inventory bulk performance | Bulk inventory changes, adjustments and alert transitions are applied in bounded database work while preserving all-or-nothing inventory behavior. | Android response/UX semantics stay the same; add timeout-safe loading and no client-side inventory authority. |
| Patient deletion integrity | Deletion removes slot-time revisions and all dependent records in server-defined order. | UI waits for server success, then clears selection and invalidates dependent screens. No client cascade. |
| Account deletion cleanup | Server deletion also disables/removes caregiver device registrations. | Android performs the server delete before local reset and treats local FCM cleanup as defense in depth. |
| Caregiver Today/History refresh | Caregiver mutations notify history and the currently visible tab refreshes without destructive state reset. | Required in Phase 3C repository design. |
| Patient recording streak | `GET /api/patient/history/streak` returns `currentStreakDays`, `isAtLeast` and `todayStatus`. Patient History inserts a `連続記録` card after today's progress; streak failure is supplementary and must not replace usable history. | Add the typed endpoint, independent state/lifecycle and current iOS card/copy. `PH-009` is `RECHECK_REQUIRED`. |
| Status-focused Caregiver Today | Current iOS removes the next-dose hero, medicine list and top bulk action. The screen now leads with `今日の服薬状況`, then optional PRN and the four-slot timeline; recording remains on eligible timeline rows. | Remove the obsolete C22 hero and next-row orange treatment while preserving mutation behavior. `CG-008`/`UI-201` are `RECHECK_REQUIRED`. |
| Missed-dose caregiver push | The server sends privacy-minimal `type=DOSE_MISSED` payloads one hour after an unrecorded scheduled slot. iOS accepts both `DOSE_TAKEN` and `DOSE_MISSED`, validates the same patient/date/slot fields and opens caregiver History. | Expand the strict Android allowlist without accepting arbitrary types and reuse exact History routing. `XP-002` is `RECHECK_REQUIRED`. |
| Public guide and release operations | Public-site guide, screenshots, cron configuration notes and release checklist changed without adding an Android runtime contract. | Preserve through the merge; no Android feature row changes solely for these files. |

## 5. Rebaseline procedure

Run this procedure whenever API or iOS behavior changes on `main`.

1. Ensure `android-dev` is clean and all current Android work is committed or intentionally preserved.
2. Record the old and new `main` SHAs.
3. Merge `main` into `android-dev`; do not cherry-pick individual runtime files without their tests.
4. Review `git diff <old-main>..<new-main>` for `api/`, `ios/`, `specs/`, legal/privacy and Firebase changes.
5. Update this file and `api-contracts.md`.
6. Mark affected matrix rows `RECHECK_REQUIRED` before implementation.
7. Update DTO fixtures and behavioral tests first.
8. Recapture changed iOS screen states using the pinned build.
9. Re-run the affected vertical-slice gates.
10. Move a row back to `IMPLEMENTED` or `VERIFIED` only with new evidence.

## 6. Baseline acceptance checklist

- [x] `android-dev` contains `main@1cf8aef` through the C31 merge checkpoint.
- [x] The main delta was reviewed across API, iOS, and tests.
- [x] Runtime/spec conflicts are explicitly identified.
- [ ] All affected Android contract tests have been updated (C32–C34).
- [ ] All affected Android implementation rows have passed recheck (C32–C34); explicitly separated physical-device evidence remains pending.
- [ ] Current iOS reference screenshots have been captured for every scoped state.
