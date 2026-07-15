# C47 UI-105 Reachability Reconciliation — 2026-07-16

## Baseline

- Current iOS UI source: clean parallel `staging@2b7d1fe`
- Android baseline: `android-dev@f8e3506` before this repair
- Authoritative screen contract: `ui-screen-contracts.md` UI-105

## Finding

UI-105 is not a current patient screen. Current iOS `HistoryMonthView` renders `HistoryDayDetailView` only in the caregiver branch, and `PatientReadOnlyView` routes patient notification targets to Today. Android already routed every valid patient target to Today, but `PatientNavigationState` still saved a legacy selected-history date and `PatientHomeScreen` could present an obsolete patient day-detail bottom sheet if that dead state were set internally.

Capturing and matching this sheet as a current patient screen would therefore preserve the wrong product state. C47 removes the stale production navigation/state surface instead.

## Closed drift

- Removed `selectedHistoryDate`, its mutators and its saved-state field from patient navigation.
- Removed the unreachable patient `ModalBottomSheet` day-detail presentation from `PatientHomeScreen`.
- Preserved the typed day API/repository lifecycle and shared timeline/row primitives used by the current caregiver UI-206 contract and its tests.
- Kept valid patient notification targets on Today with the exact slot highlight regardless of payload date, matching current iOS.
- Added production-shell coverage proving a notification target does not create `history-day-detail-list`, plus a historical-date routing regression.

## Evidence disposition

- The five C15 Android component fixtures remain regression evidence for the retained data/timeline foundation, not evidence of a reachable patient screen.
- Current visual acceptance for scheduled/PRN rows, loading, empty, retry, notification highlight and missed-dose backfill belongs to reachable caregiver UI-206 (`c16`/`c28`).
- There is intentionally no new iOS/Android UI-105 screenshot pair: non-reachability is the parity contract and is proven by production source, routing and UI tests.

## Verification

- `PatientTabRoutingTest`: current and historical dates both route to Today and preserve the exact slot.
- `PatientNavigationStateTest`: restored patient navigation contains tab lifetime and dose detail only; no retired history-date destination.
- `PatientTodayContentTest.productionNotificationTargetScrollsToExactSlotWithoutReplacingNextDose`: exact Today slot appears and the history-day detail root does not exist.
- Full API 35 instrumentation: 209/209, 0 skipped and 0 failed.
- JVM: 186/186, 0 skipped and 0 failed.
- `assembleDebug` and `lintDebug`: passed.

Physical notification delivery/tap and TalkBack remain Gate I evidence. Caregiver UI-206 visual residuals remain tracked under the caregiver screen matrix rather than UI-105.
