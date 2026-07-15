# C29 UI-208 caregiver settings source calibration

- Date: 2026-07-15
- Android branch: `android-dev`
- iOS reference: clean `staging@8717788`
- Android fixture: API 35, 1080 x 2400 display
- iOS source fixture: 1170 x 2532 display

## Source contract

C29 calibrates the normal selected-patient Settings surface against the current production `PatientManagementView.swift`, the shared `CaregiverPatientHeader`, and the built-in caregiver Settings tutorial sample. The shared hierarchy is:

- a 62/50-unit selected-patient avatar with large `連携・設定` title and patient-aware subtitle;
- one 18-corner selection card containing the icon/title/help header and a 44-unit selected-patient row;
- one teal-stroked selected-patient card with avatar, selection capsule, code-issue action and destructive actions;
- an 18-corner detail-settings card with the 34-unit group icon and an icon-bearing time-preset navigation row;
- push, Analytics privacy, legal/support and account cards in that order;
- teal group-header icon treatments, semantic action colors, 16-unit section rhythm and enough bottom inset to keep the final account actions above navigation.

Android retains a separate `リンク解除` action because contract CG-004 distinguishes data-preserving session revocation from irreversible patient deletion. Current iOS exposes only code issue and deletion in this card. The additional Android row is therefore an intentional API-contract extension, not visual drift.

## Closed drift

- Replaced the stale blue group header and one-card-per-patient list with the shared patient avatar/header and a single iOS-style selection card.
- Folded code issue and destructive actions into the selected-patient card instead of rendering separate linking and danger cards.
- Rebuilt detail, push, Analytics, legal/support and account sections around the current icon/header/action-row hierarchy.
- Matched the filled red logout and outlined account-delete hierarchy while preserving the existing confirmation and server-first deletion behavior.
- Added the current grouped background and 128-unit bottom content inset.
- Updated tutorial scroll indexes for the grouped card structure without changing code/time-sheet behavior.
- Made the deterministic typical-light fixture include enabled push and Analytics state so the source card order is visible.

## Reference provenance and intentional differences

The iOS image was built from a temporary detached worktree. A transient Debug-only route selected tutorial step 5; it was not committed. The image was captured on a disposable iPhone 17e simulator, then both the simulator and temporary worktree were removed. The parallel iOS/staging worktree was never modified.

The iOS image is a tutorial sample: it uses `田中 花子`, generic guidance copy, a highlighted tab overlay and omits `リンク解除`. Android evidence uses the production component with synthetic patient `さくら`, the patient-aware header, the CG-004 revoke action and no tutorial overlay. Those product-only differences are intentionally retained.

## Evidence

| File | SHA-256 |
|---|---|
| `ios-ui-208-caregiver-settings-source-light.png` | `a3f98048d782be638de168c0b5972eb7d8c8f762983b3329f646ed7355d64efc` |
| `android-ui-208-caregiver-settings-source-calibrated-light.png` | `3823de052a3720402c11097c20b036230c40204e3631519b43f8655bf7647e9c` |
| `ui-208-light-side-by-side.png` | `ec66366261f11ccac265b928655b0f694b9966bfdd1739b01482b635f56d76d4` |

## Verification

- Current iOS Debug reference build: pass.
- `CaregiverHomeScreenTest`: 16/16 on API 35.
- C17 loading/error/empty, linking-code sheet, time-preset sheet and dark/200% sheet contracts remain covered.
- JVM tests, Debug assembly and Lint: pass.
- Full API-35 instrumentation suite: 185/185 pass, including C29 typical-light, C17 Settings state/sheet and caregiver dark/200% reachability contracts.

## Remaining release evidence

Matched production iOS multiple-patient, no-patient, dark and large-text pairs remain. Physical TalkBack traversal, clipboard/Sharesheet/browser, notification permission/FCM and destructive production-account verification remain Gate I requirements.
