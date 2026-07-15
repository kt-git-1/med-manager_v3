# C18 Current-iOS Dose Status Copy — 2026-07-15

## Source contract

Where current iOS renders the full scheduled-dose status labels in Today and history detail, it uses the same three user-facing states:

| API state | Current iOS localization | Android resource |
|---|---|---|
| `TAKEN` | `記録済み` | `patient_status_taken` |
| `MISSED` | `飲み忘れ` | `patient_status_missed` |
| `PENDING` | `未記録` | `patient_status_pending` |

The Android shared resources previously retained `服用済み` / `未達` / `未服用`. Those stale labels reached Patient Today, the retained patient calendar, Caregiver Today, Caregiver History and their TalkBack day summaries. The shared resources now match `ios/MedicationApp/Resources/Localizable.strings`; action copy such as `服用済みにする` is a separate current-iOS interaction phrase and is intentionally unchanged.

## Regression coverage

- `PatientTodayContentTest.plannedDoseStatusesUseCurrentIosCopy` traverses the production Today lazy list and asserts all three current labels are visible while all three stale labels are absent.
- `PatientHistoryContentTest` protects the current recorded-state copy.
- `CaregiverAccessibilityTest` protects the complete spoken calendar-day summary with `記録済み` / `飲み忘れ` / `未記録`.
- The affected Patient Today/History and caregiver accessibility contracts pass within the complete API-35 suite, which is green at 176/176; the new Today contract also passes independently.

Matched iOS adaptive captures, full TalkBack traversal and physical-device verification remain in C01/C06/Gate I; this checkpoint closes the source-level and emulator-automated wording drift.
