# C28 UI-206 caregiver history source calibration

- Date: 2026-07-15
- Android branch: `android-dev`
- iOS reference: clean `staging@8717788`
- Android fixture: API 35, 1080 x 2400 display
- iOS source fixture: 1170 x 2532 display

## Source contract

C28 calibrates the caregiver month-history surface against the current production `HistoryMonthView.swift`, `HistoryDayDetailView.swift` and the built-in `-CaregiverMarketingScreenshot.history` source sample. Both evidence images use the same synthetic June calendar state: selected June 10, taken/pending/missed markers and neighboring PRN history. No account, token or real medication data is present.

The current shared contract is:

- the 62/50-unit selected-patient avatar, 34-unit title and 17-unit patient subtitle hierarchy;
- a centered `yyyy年M月` label, with month navigation shown only when the billing-enabled history range is available;
- an 18-corner, stroked/shadowed calendar card with 17/15-unit title/copy;
- Monday-first weekday order, 54-unit day cells, subtle history-cell fill/stroke and 6-unit ordered status dots;
- current status colors and copy: teal `記録済み`, red `飲み忘れ`, gray `未記録` and system purple `頓服`;
- the explanatory legend plus adaptive items, followed by the selected-day card using `M月d日（E）`, `%d/%d回分 記録済み`, help copy and icon-bearing pills;
- the existing inline timestamp/name-sorted day timeline, loading/empty/retry states, notification highlight and confirmation-protected missed-dose recording from C16.

## Closed drift

- Replaced the stale calendar-glyph header with the shared current caregiver avatar/header metrics.
- Changed the calendar from Sunday-first to the current Monday-first contract.
- Added history-cell fill/stroke treatment and matched the four marker colors, size and spacing.
- Restored the missing legend explanation and adaptive legend layout.
- Corrected the selected date and progress formats from Android-only copy to `M月d日（E）` and `%d/%d回分 記録済み`.
- Added the future-pending explanation and current icon-bearing summary pills.
- Removed free-release month arrows while retaining them for billing-enabled history.

## Reference provenance and intentional differences

The iOS image was built unmodified from a temporary detached worktree and captured on a disposable iPhone 17e simulator. The temporary worktree and simulator were removed afterward; the parallel iOS/staging worktree was never modified.

The iOS reference is the app's tutorial marketing sample, so it deliberately shows guidance copy and the highlighted tutorial tab bar, and omits the production month label. Android evidence uses the real production component, therefore retains the selected patient's name, centered month label and no tutorial overlay. These product-only differences are not copied into Android.

## Evidence

| File | SHA-256 |
|---|---|
| `ios-ui-206-caregiver-history-source-light.png` | `53a7fc92978214862b00ab1f7b5408f31768d2a9fa571db59638b0f84bec5ef5` |
| `android-ui-206-caregiver-history-source-calibrated-light.png` | `a612b62fdb37a0193c3b27df333abda5e625d9ffdbab58d130ebd41577ad6cc6` |
| `ui-206-light-side-by-side.png` | `f4f058937b36fb300094902d74fec5397005b98fdb99999d413409ac42347969` |

## Verification

- Current iOS Debug reference build: pass.
- `CaregiverHistoryScreenTest`: 11/11 on API 35.
- Caregiver 130%/200%, light/dark and accessibility regression group: 17/17 on API 35.
- JVM tests, Debug assembly and Lint: pass.
- Full API-35 instrumentation suite: 185/185 pass, including the new free/billing month-navigation contract.

## Remaining release evidence

Matched production iOS loading/error/retention/PDF, dark and large-text pairs remain. Physical notification tap/process-death, TalkBack traversal and signed Play entitlement verification remain Gate I requirements.
