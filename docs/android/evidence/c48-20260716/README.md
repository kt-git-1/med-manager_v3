# C48 UI-106 Current-iOS Patient Settings Matrix — 2026-07-16

## Baseline and environment

- Current iOS UI source: clean detached capture worktree at `staging@2b7d1fe`
- Android baseline: `android-dev@ecce60f` before this repair
- iOS: disposable iPhone 17e simulator, iOS 26.5, production `PatientSettingsView`
- Android: `MedicationApp_API_35`, 1080 x 2400, 420 dpi, production `SettingsContent`
- Deterministic data: notifications and privacy-first Analytics enabled; Japanese locale
- Diagnostics: raw captures normalized to 1080 x 2400, then rendered side by side and at 50% opacity

The detached iOS capture route supplied only deterministic permission, initial-scroll and confirmation state to the unchanged production Settings component. It made no API request. The disposable worktree/simulator were deleted after capture, and the parallel iOS worktree remained clean.

## Evidence

| State | Raw iOS | Raw Android | Side by side | 50% overlay |
|---|---|---|---|---|
| Top, light | [`ios`](ios-ui-106-patient-settings-top-light.png) | [`android`](android-ui-106-patient-settings-top-light-matched.png) | [`compare`](compare-ui-106-patient-settings-top-light-side-by-side.png) | [`overlay`](compare-ui-106-patient-settings-top-light-overlay-50.png) |
| Legal/logout, light | [`ios`](ios-ui-106-patient-settings-lower-light.png) | [`android`](android-ui-106-patient-settings-lower-light-matched.png) | [`compare`](compare-ui-106-patient-settings-lower-light-side-by-side.png) | [`overlay`](compare-ui-106-patient-settings-lower-light-overlay-50.png) |
| Permission denied top, light | [`ios`](ios-ui-106-patient-settings-denied-top-light.png) | [`android`](android-ui-106-patient-settings-denied-top-light-matched.png) | [`compare`](compare-ui-106-patient-settings-denied-top-light-side-by-side.png) | [`overlay`](compare-ui-106-patient-settings-denied-top-light-overlay-50.png) |
| Permission guidance, light | [`ios`](ios-ui-106-patient-settings-denied-light.png) | [`android`](android-ui-106-patient-settings-denied-light-matched.png) | [`compare`](compare-ui-106-patient-settings-denied-light-side-by-side.png) | [`overlay`](compare-ui-106-patient-settings-denied-light-overlay-50.png) |
| Logout confirmation, light | [`ios`](ios-ui-106-patient-settings-confirm-light.png) | [`android`](android-ui-106-patient-settings-confirm-light-matched.png) | [`compare`](compare-ui-106-patient-settings-confirm-light-side-by-side.png) | [`overlay`](compare-ui-106-patient-settings-confirm-light-overlay-50.png) |
| Top, dark | [`ios`](ios-ui-106-patient-settings-top-dark.png) | [`android`](android-ui-106-patient-settings-top-dark-matched.png) | [`compare`](compare-ui-106-patient-settings-top-dark-side-by-side.png) | [`overlay`](compare-ui-106-patient-settings-top-dark-overlay-50.png) |
| Top, largest text | [`ios`](ios-ui-106-patient-settings-top-accessibility-xxxl.png) | [`android`](android-ui-106-patient-settings-top-font-2.0-matched.png) | [`compare`](compare-ui-106-patient-settings-top-adaptive-side-by-side.png) | [`overlay`](compare-ui-106-patient-settings-top-adaptive-overlay-50.png) |

## Review disposition

1. The C21 card, icon, 20/15-unit typography, legal-row and 58-unit logout calibration remains valid against the current production component.
2. Direct runtime comparison exposed one remaining token drift: SwiftUI uses the system green `#34C759` for enabled notification/Analytics toggles, while Android still used patient teal. Android now uses the current green in light/dark and a 50%-green disabled-selected track when OS permission is denied.
3. Permission denial keeps the master preference visibly selected but disabled and adds the red-accent guidance card after legal/support. Both the top disabled control and lower guidance are paired separately.
4. Logout confirmation uses the exact title/message/cancel/action copy. Android changes the confirmation from a filled primary action to a destructive red text action; platform-native alert placement and material are intentionally retained.
5. Dark mode preserves semantic background/card/text colors and the green toggle. Android keeps the complete Settings list scroll-reachable at 200%, while current iOS caps the app root at `.xLarge` under Accessibility XXXL.
6. Current iOS has no logout-in-progress or logout-failure UI and ignores revoke failure before clearing the local patient token. Android intentionally does not copy that behavior: the pinned API/security contract requires server-first revocation, disabled `ログアウト中…`, failure preservation and explicit retry. Existing Android-only state fixtures remain behavioral evidence, not matched visual states.

## Verification

- `PatientSettingsContentTest`: 8/8 focused API-35 pass, including seven current-runtime evidence states and retained server-first progress/failure behavior.
- Full API 35 instrumentation: 211/211 passed with 0 skipped/failed after the final C48 source change.
- JVM: 186/186 passed with 0 skipped/failed; Debug assembly and Lint also pass.
- Parallel iOS worktree: clean; detached capture worktree and simulator removed.

Physical notification permission transitions, browser return, real session revocation, TalkBack traversal and OEM rendering remain Gate I evidence.
