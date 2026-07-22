# Caregiver late-record and grouped-history design QA

## Evidence

- Source visual truth: `/Users/kaito/.codex/generated_images/019f5a2c-62d4-7b23-b53d-61601c7e2b02/exec-9a9885a1-628d-47e1-8d96-3325af444d4a.png`
- Current-history structural reference: `/Users/kaito/Desktop/Screenshot 2026-07-22 at 18.17.31.png`
- Today implementation screenshot: `/tmp/caregiver-today-implementation.jpeg`
- History implementation screenshot: `/tmp/caregiver-history-implementation.jpeg`
- Expanded-history interaction screenshot: `/tmp/caregiver-redesign-attachments-final/78188E3D-A3AF-4A6D-B309-84253DF2A20B.png`
- Proxy-recording interaction screenshot: `/tmp/caregiver-redesign-attachments-final/2BA67594-E745-4D14-9408-1E7DF8AC38F2.png`
- Combined full-view comparison: `/tmp/caregiver-design-qa-comparison.jpg`
- Viewport: iPhone 17e simulator, 390 x 844 points.
- Source pixels: 1700 x 925 design board containing two app screens.
- Implementation pixels: 410 x 867 simulator-window captures for the full-view comparison; 1170 x 2532 at 3x density for focused interaction captures.
- Density normalization: the combined comparison preserves source aspect ratio and scales both simulator-window captures to equal heights. Focused captures are compared at native 3x density.
- State: caregiver Today with one late patient record and one proxy-recordable slot; caregiver History on 2026-07-22 with noon expanded.

## Findings

- No actionable P0, P1, or P2 mismatch remains.
- The implementation intentionally differs from the generated reference by showing the proxy-record action only on an unrecorded, recordable slot. The generated image incorrectly placed the action on recorded slots; preserving the production eligibility condition is the accepted product constraint.
- The History screen preserves the existing selected-date summary and bottom navigation. Only the list below the second date heading changes to grouped 朝・昼・夜・眠前 rows, matching the clarified scope.
- Recorded History groups are compact by default and expand on tap. The source board depicts expanded rows, while the implementation interaction was verified separately in the focused capture and UI test. This is an accepted responsive-density choice for the 390-point viewport.

## Required fidelity surfaces

- Fonts and typography: existing SwiftUI system typography, weights, Japanese wrapping, and accessibility-friendly sizes are retained. The late summary wraps to two lines on the real viewport but remains readable and does not clip.
- Spacing and layout rhythm: 16–20 point page margins, 10–16 point group spacing, rounded existing caregiver cards, and persistent bottom navigation remain consistent. No control is hidden by overflow.
- Colors and visual tokens: existing `CaregiverUI` and slot colors are reused. Late records are orange, taken records teal, pending records gray, and missed records remain red.
- Image quality and asset fidelity: existing SF Symbols and `MedicationSymbolView` are reused at native resolution. No raster placeholder, improvised SVG, or new decorative asset is introduced.
- Copy and content: scheduled time, actual record time, delay duration, and recorder are visible. `本人が記録` and `家族が代理で記録` remain distinct.

## Interaction evidence

- Focused unit tests: 9 passed, including exact actual-time aggregation, 5時間21分 late calculation, partial-record preservation, proxy bulk recording, and history-refresh behavior.
- Focused UI tests: 2 passed.
  - Today shows the late summary and actual time; the recorded morning slot has no proxy button; the unrecorded noon slot retains a hittable proxy-record button.
  - History exposes all four time-period groups; tapping noon reveals both medicines and the actual time and delay.

## Comparison history

- Initial implementation build failed because grouped DTO rows lacked stable SwiftUI identifiers. Fixed by adding stable composite `historyRowID` values; the next build passed.
- Initial UI-test queries assumed SwiftUI element types and failed to locate flattened accessibility nodes. Fixed by querying the combined accessible labels and retested; both targeted UI tests passed.
- Post-fix visual evidence is recorded in the expanded-history and proxy-recording screenshots above.

## Follow-up polish

- [P3] The Today late-summary sentence wraps after `本人` on the narrow real viewport. A later copy-polish pass could split the time and recorder into separate semantic lines, but the current layout is readable and complete.

## Implementation checklist

- [x] Preserve proxy recording eligibility and confirmation flow.
- [x] Show late status, actual time, delay, and recorder in caregiver Today.
- [x] Preserve the existing History summary and bottom navigation.
- [x] Group the selected day's scheduled medicines by time period.
- [x] Make time-period rows expandable and retain missed-dose backfill.
- [x] Pass build, unit tests, focused UI tests, and visual comparison.

final result: passed
