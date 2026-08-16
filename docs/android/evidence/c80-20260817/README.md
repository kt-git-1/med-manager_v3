# C80 processed Android Analytics observation

**Observed:** 2026-08-17 02:53 JST
**Branch/commit:** `android-dev@b8dad61`
**Property:** production Analytics property reached from the production Firebase project
**Operation:** read-only report inspection; no app traffic, report comparison, Explore or Console configuration was created or saved

## Observation

- The delayed processed report now exposes Platform `android` alongside `ios`; the first same-day observation had offered only iOS.
- The application-version card includes the Android 1.0.6 application row with processing status `success` and reports 100% available data.
- The processed event table contains the fixed-schema C76 rows:
  - `caregiver_tab_viewed`
  - `patient_tab_viewed`
  - `screen_viewed`
  - `tutorial_step_viewed`
- The displayed event counts are property-wide aggregates. They are intentionally not claimed as Android-only counts or as evidence connecting the same people across platforms.
- No user, patient, caregiver, medication, dose, inventory, device, advertising or free-text dimension was opened or recorded.

## Result

| Gate | Result |
|---|---|
| Production property reached from Firebase | PASS |
| Platform includes Android | PASS |
| Android 1.0.6 processing row | PASS — success, 100% available data |
| Privacy-safe processed event names | PASS |
| Android-only event-count attribution | NOT CLAIMED — report counts are property-wide |
| New Analytics traffic or Console write | NONE |
| Temporary privacy-reviewed Explore | PENDING — separate controlled Console-write gate |

C80 closes the processed Events delay without weakening the privacy boundary. `XP-004` remains `PARTIAL` only because the temporary fixed-enum-only Explore creation/removal evidence is still pending.
