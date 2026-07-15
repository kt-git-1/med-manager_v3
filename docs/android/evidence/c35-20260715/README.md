# C35 Full Rebaseline Regression

## Source synchronization

- `git fetch origin main` on 2026-07-15 returned `origin/main@1cf8aef`.
- This exactly matches the C31 pin and the second parent of the C31 merge commit.
- No new main-side API/iOS/spec commit remained to merge before the regression run.

## Complete current gates

| Gate | Result |
|---|---|
| API Vitest | 70 files / 315 tests passed |
| Android JVM | 27 result files / 185 tests passed, 0 failed/error/skipped |
| Android Debug APK | `assembleDebug` passed |
| Android Lint | `lintDebug` passed |
| Android API-35 instrumentation | 187/187 passed, 0 failed/skipped |

The instrumentation suite ran as one unfiltered `connectedDebugAndroidTest` invocation on `MedicationApp_API_35(AVD)` and completed in 5m47s. It therefore covers the complete current checked-in suite rather than only the C32–C34 focused classes.

## Rebaseline disposition

- `PH-009` patient streak: `IMPLEMENTED`.
- `CG-008` status-focused Caregiver Today: `IMPLEMENTED`.
- `XP-002` taken/missed caregiver notification routing: `IMPLEMENTED`.
- No parity row remains `RECHECK_REQUIRED`.

This closes the automated C31 rebaseline. It does not replace the explicitly pending fresh iOS runtime comparisons, expanded current API 26/33 reruns, live Firebase Console evidence, physical-device matrix or signed Play release gates.
