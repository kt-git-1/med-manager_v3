# Android UI Fidelity Specification

## 1. Fidelity target

Android must reproduce the iOS app's identity, hierarchy, density, content grouping, state emphasis, and accessibility intent at high fidelity. It must not blindly copy iOS system chrome where Android conventions improve usability.

### Must match

- Screen hierarchy and navigation destinations
- Japanese text and terminology
- Which information is prominent, secondary, hidden, or conditional
- Patient/caregiver color identity
- Card grouping, state colors, corner-radius family, spacing rhythm, and button hierarchy
- Empty/loading/error/confirmation/success/disabled states
- Large-text intent and minimum touch-target behavior
- Tutorial content attached to the real operational screen

### May be Android-native

- System back gesture and predictive back
- Runtime permission dialog timing and system wording
- Material text-field mechanics and keyboard actions
- System share sheet, browser, notification settings, and date/time pickers
- Navigation-bar/system-inset treatment

Any difference outside this list requires a recorded rationale in `parity-requirements.md`.

## 2. Canonical design tokens

Tokens are derived from `AppTheme`, `PatientUI`, and `CaregiverUI`; Android must centralize them rather than hardcode values inside screens.

| Token | iOS light reference | Android requirement |
|---|---|---|
| Primary teal | RGB 0, 140, 128 approximately | Shared semantic primary |
| Primary teal text | RGB 0, 110, 102 approximately | Accessible teal text |
| Patient error | RGB 219, 46, 51 approximately | Patient destructive/missed state |
| Caregiver error | RGB 209, 41, 41 approximately | Caregiver destructive state |
| Screen background | RGB 242, 250, 252 approximately | Full-screen grouped background |
| Card background | iOS grouped card semantic color | Theme-aware surface |
| Card radius | commonly 18 pt; variants 12/14/16/20/22/28 | Named radius scale, not ad hoc literals |
| Card stroke | primary at 10% | Theme-aware subtle outline |
| Patient shadow | black at 7% | Equivalent low elevation |
| Caregiver shadow | black at 6% | Equivalent low elevation |

Dark colors must be derived from the iOS dark semantic values and verified separately. Material defaults must not silently replace product colors.

## 3. Typography and accessibility

- Use Japanese system sans fonts unless the iOS asset explicitly uses another font.
- Preserve semantic hierarchy rather than copying raw point sizes across platforms.
- Patient mode defaults to larger readable text and must remain usable at Android font scale 1.0, 1.3, and 2.0.
- Essential content must not truncate at 1.3 font scale.
- At 2.0, wrapping/scrolling is allowed but primary actions must remain reachable.
- Interactive controls must have at least a 48 x 48 dp touch target.
- State cannot be communicated by color alone; text/icon/semantics must accompany it.
- TalkBack labels must describe medication, scheduled time, status, and available action.

## 4. Reference-capture protocol

For each screen/state:

1. Use deterministic data with matching names, times, counts, statuses, and dates on iOS and Android.
2. Set Japanese locale and Asia/Tokyo time zone.
3. Capture light mode at default text size.
4. Capture dark mode where supported.
5. Capture at large text/font scale.
6. Match viewport class: compact phone first, then a larger phone.
7. Store captures under ignored output during iteration; record stable evidence paths in the phase document.

Required states depend on the screen but normally include:

- Loading
- Empty
- Typical content
- Long Japanese content
- Error/retry
- Disabled/updating
- Confirmation dialog
- Success/partial success
- Permission denied where relevant

## 5. Comparison and acceptance

Each screen receives three reviews:

### Structural review

- Same sections and order
- Same conditional visibility
- Same navigation and action destinations
- Same state transitions

### Visual review

- Side-by-side inspection at matched data
- Semi-transparent overlay inspection after viewport normalization
- Pixel-diff used as diagnostic evidence, not as the only quality metric

### Interaction review

- Tap targets and scrolling
- Keyboard and IME actions
- Android back behavior
- Permission denial/retry
- Rotation/configuration or process recreation where applicable

A screen is visually accepted only when no material mismatch remains in information hierarchy, spacing rhythm, typography hierarchy, component shape, semantic color, or fixed-bottom overlap. Minor platform font rasterization and system-chrome differences are acceptable.

## 6. Component policy

Before full screens, establish shared production components:

- `PatientScreenBackground` / `CaregiverScreenBackground`
- Patient/caregiver headers
- Status badge and legend
- Medication/dose card
- Slot card and bulk-action footer
- Primary/secondary/destructive buttons
- Loading overlay, empty state, inline error, toast/banner
- Bottom navigation shells
- Confirmation dialog/sheet patterns

Fixtures and screenshot modes must call these same components. A separate simplified screenshot-only card is a parity defect.

## 7. UI definition of done

- iOS references and required states are listed.
- Shared tokens/components are used; unexplained literal colors/radii are absent.
- Compact and large phone captures pass review.
- Default and large font-scale captures pass review.
- Light and dark mode pass where scoped.
- TalkBack semantics and 48 dp targets are checked.
- Fixed bottom bars do not cover content or tutorial actions.
- The parity matrix row is updated to `VERIFIED` with evidence.
