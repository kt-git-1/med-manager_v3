# C30 UI-208 dark and maximum-text source calibration

- Date: 2026-07-15
- Android branch: `android-dev`
- iOS UI reference: clean `staging@8717788`; UI sources unchanged through parallel `staging@41ea977`
- Android fixture: API 35, dark theme, 200% font, 1080 x 2400 display
- iOS source fixture: iPhone 17e, dark appearance, accessibility XXXL, 1170 x 2532 display

## Scope

C30 closes the missing matched adaptive pair for the normal selected-patient Settings surface. C17 already covered both dedicated sheets in dark mode at 200% font. This checkpoint adds the production card hierarchy itself and verifies that its off-screen controls remain reachable at the same Android font scale.

The test renders the real `CaregiverHomeScreen` with deterministic selected-patient, enabled push and enabled Analytics state. It verifies and scrolls to:

- push switch;
- Analytics consent switch;
- Privacy Policy, Terms and Support rows;
- logout and account-delete actions;
- code issue and its sheet share action;
- time-preset sheet and its save action.

## Repair found by evidence

The first dark capture exposed a white status-bar safe-area above otherwise-correct dark content. Individual tabs supplied their own background, but the shared caregiver shell root did not. `CaregiverHomeScreen` now paints the semantic screen background across the full window before applying safe drawing insets, so every caregiver tab inherits a correct dark top inset.

## Reference provenance and intentional differences

The iOS image uses the same transient Debug-only Settings tutorial route built from detached `staging@8717788` for C29. A fresh disposable simulator was configured to dark appearance and `accessibility-extra-extra-extra-large`, captured, and deleted. No iOS branch or worktree was changed.

The iOS tutorial sample retains guidance copy, `田中 花子`, a highlighted tab overlay and no revoke action. Android uses synthetic patient `さくら`, its patient-aware subtitle, the CG-004 data-preserving revoke row and normal production navigation. Android uses 200% font while iOS uses its maximum accessibility category; line wrapping and the amount visible above navigation are therefore platform-adaptive rather than pixel-identical.

## Evidence

| File | SHA-256 |
|---|---|
| `ios-ui-208-caregiver-settings-dark-accessibility-xxxl.png` | `a39ddc9036bfc24e9692f725d00bd4015e449928849dfff0e4280f1411264dda` |
| `android-ui-208-caregiver-settings-dark-font-2.0.png` | `0794e6383a183f11faede68bc3defee7ddf6c651ed38a01b302acf92bb375c65` |
| `android-ui-208-caregiver-settings-actions-dark-font-2.0.png` | `e9d91075ece2aa23e14fd6138afa5f129ea298d0e1123a612c09e63e3c34d1f6` |
| `ui-208-dark-adaptive-side-by-side.png` | `e0ce0faf8141a8e8e2c870ea4e0ff9bf76e869fb98c89419328ed0d97bea13be` |

## Verification

- `CaregiverLargeTextUiTest`: 10/10 on API 35, covering light/dark 200% paths.
- C17 code/time sheets: retained in the same test path.
- JVM tests, Debug assembly and Lint: pass.
- Full API-35 instrumentation suite: 185/185 pass after the shared caregiver-shell background repair.

## Remaining release evidence

Physical TalkBack traversal, OEM display/font settings, notification permission/FCM and destructive production-account behavior remain Gate I. Other screens still retain their own unmatched exceptional-state dark/large-text residuals.
