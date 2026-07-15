# C21 UI-106 Patient Settings Source-Calibrated Comparison — 2026-07-15

## Evidence

| Surface | File | Provenance |
|---|---|---|
| iOS app-derived light reference | [`ios-ui-106-patient-settings-app-reference-light.png`](ios-ui-106-patient-settings-app-reference-light.png) | Unmodified `api/public/screenshots/patient-settings-demo.png` from current `main`; SHA-256 `4db26b9a3a75909a843e88b9259c67a46496ee1089a26f410173e71e7815647d` |
| Android initial light result | [`android-ui-106-patient-settings-source-calibrated-light.png`](android-ui-106-patient-settings-source-calibrated-light.png) | Production `SettingsContent` on API 35 with notifications and privacy-first Analytics enabled |
| Android legal/logout result | [`android-ui-106-patient-settings-source-calibrated-logout-light.png`](android-ui-106-patient-settings-source-calibrated-logout-light.png) | The same production component scrolled through all legal/support rows to the logout action |
| Diagnostic comparison | [`side-by-side`](ui-106-light-side-by-side.png), [`50% overlay`](ui-106-light-overlay-50.png) | Initial raw images normalized to 1080 x 2400 for alignment only |

The iOS image is a real-app-derived marketing capture containing the notification, linked-status and logout hierarchy. Current production `PatientSettingsView` also contains Analytics consent, legal/support navigation and notification-denied handling; the SwiftUI source is authoritative for those additional sections and all metrics. The raw captures remain authoritative because the diagnostic files rescale the iOS reference.

## Differences found and closed

- Settings cards now use the shared iOS `PatientCard` family: 18-unit corners, 18-unit padding, a one-unit neutral stroke or 1.5-unit accent stroke, and the existing elevation.
- Section titles use 20-unit bold type and 20-unit icons with 18-unit content spacing.
- Toggle, linked-status and legal rows now use 20-unit bold titles, 15-unit semibold supporting text, four-unit title spacing and the current 44/48-unit circular icon frames.
- Legal/support rows use a plain full-width row with 14-unit spacing and a proper chevron icon instead of Material text-button padding and a text glyph.
- Logout now uses the current 18-unit corner, 58-unit minimum height, leading logout icon and 20-unit bold label while preserving server-first progress/confirmation behavior.
- The shared 62 dp teal header icon corrected under C19 remains visible and consistent with the iOS header.

## Regression coverage

- `PatientSettingsContentTest.currentIosSourceCalibratedSettingsFixtureUsesProductionHierarchy`
- The complete `PatientSettingsContentTest` class passes 6/6 on API 35 and emits both Android raw fixtures.
- The combined `PatientAdaptiveUiTest` and `PatientSettingsContentTest` pass 12/12, retaining 130%/200% scroll reachability after the larger source-matched typography.
- The complete API-35 instrumentation suite passes 180/180; JVM tests, Debug assembly and Lint also pass.
- Existing tests continue to cover notification-denied, logout submitting/failure and explicit confirmation states.

This closes the source-calibrated typical light UI-106 pass without changing the iOS worktree. Deterministic matched iOS notification-denied/logout states, matched dark/large-text, physical TalkBack and physical-device rendering remain in C01/C06/Gate I.
