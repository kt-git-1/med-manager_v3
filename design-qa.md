# Medication registration image-to-code QA

## Evidence

- Source visual truth: `/Users/kaito/.codex/generated_images/019f5a2c-62d4-7b23-b53d-61601c7e2b02/exec-b97df027-12e0-4ef7-a921-1c557ef3e5d6.png`
- Final implementation screenshot: `/tmp/medication-form-true-final-top.png`
- Full-view comparison: `/tmp/medication-form-true-final-comparison.png`
- Focused lower-state screenshot: `/tmp/medform-compact-final-attachments-2116/A9BDFB88-25E4-4E65-872D-A72463A73E13.png`
- Viewport: iPhone 17e simulator, portrait, 390 x 844 points at 3x density.
- Source pixels: 853 x 1844. Implementation pixels: 1170 x 2532.
- Normalization: source resized to 1170 x 2532 and compared beside the 3x simulator capture.
- State: scheduled medication, `血圧の薬 5 mg`, one tablet per intake, morning and evening, 30-day supply, calculated inventory 60 tablets.

## Findings

- No actionable P0, P1, or P2 mismatch remains.
- The three primary regions now follow the selected image in the same order and hierarchy: `基本情報`, `飲むタイミング`, `お薬の数量`, followed by `この内容で登録`.
- Production-only medication type, weekday, period, and memo controls remain available below the primary flow in a collapsed `薬の種類・期間・メモ` disclosure. They do not alter the selected visual's primary composition.
- The real iPhone viewport permits a small vertical scroll to reach every trailing control. This preserves native text rendering and touch targets while keeping the visible composition aligned with the source.

## Required fidelity surfaces

- Fonts and typography: native Japanese system text is used with fixed display sizes for the reference header and calculator. Weight and hierarchy match the source; no clipping or truncation was observed.
- Spacing and layout rhythm: margins, card order, compact card padding, timing grid, formula row, quantity result, and registration button were aligned against the normalized full-view comparison.
- Colors and visual tokens: the existing app teal, orange, pale blue background, white cards, gray secondary text, and subtle borders/shadows match the source palette.
- Image quality and asset fidelity: all icons are native SF Symbols at device resolution. No placeholder, improvised raster, emoji, or custom SVG was introduced.
- Copy and content: `お薬を登録`, `薬の情報と飲み方を入力します`, `基本情報`, `飲むタイミング`, `お薬の数量`, `登録時の在庫`, and `この内容で登録` are present. The concept-only `1 / 3` indicator was intentionally removed because the production flow is not a three-step wizard.

## Interaction evidence

- Focused unit tests: 11 passed with 0 failures.
  - Daily, fractional-dose, weekly selected-day, PRN, and recalculation behavior remain covered.
- Focused UI test: 1 passed with 0 failures.
- Verifies the corrected header and primary sections.
  - Verifies 30 days, formula terms `1錠`, `2回`, `30日`, calculated inventory `60`, and the registration action.
- Staging simulator build: passed.

## Comparison history

- Initial implementation had P1 visual drift: an extra introduction card, oversized form rows, medication type and period mixed into the main flow, and a fixed action covering the quantity card.
- First correction replaced the long Form layout with the selected image's three-card hierarchy and moved production-only fields into a collapsed disclosure.
- Second correction removed the fixed material action, restored the image's inline registration action, changed `時間帯` to `飲むタイミング`, matched the orange outline clock treatment, and restored the horizontal calculation divider.
- Final correction reduced card padding, field height, typography, and calculator spacing. The normalized final comparison shows the same composition and all primary content without overlap.
- Product correction removed the misleading `1 / 3` label because registration is completed on one scrolling screen.

## Follow-up polish

- [P3] SF Symbol artwork differs slightly from the generated concept's illustrative icon glyphs, while retaining the same semantic shape and color.
- [P3] The native `mg` picker includes the platform chevron, which is required to communicate that the unit can be changed.

## Implementation checklist

- [x] Match the selected header and back action.
- [x] Match the compact basic-information card.
- [x] Match the four timing choices and selected states.
- [x] Match the 30-day calculation and 60-tablet result.
- [x] Place the registration action after the quantity card without overlap.
- [x] Preserve scheduled, weekly, PRN, editing, date, memo, and delete behavior.
- [x] Pass unit, UI, visual, and Staging-build verification.

final result: passed
