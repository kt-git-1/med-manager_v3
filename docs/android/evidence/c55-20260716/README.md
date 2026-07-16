# C55 UI-207 PDF reachability and current-runtime evidence

**Captured:** 2026-07-16

**iOS reference:** final recheck against `main@3e52fb2`; `PDFExportButton`, lock, period picker, view model, generator, localized copy and `billingEnabled=false` are byte-identical to the initially audited `staging@2b7d1fe` and Android-worktree copies

**Android baseline:** `android-dev@81ce0d9` plus the C55 change

**Device:** Pixel-compatible API 35 ARM emulator, 1080 x 2400

## Product reachability

| Context | Current iOS | Android C55 | Result |
|---|---|---|---|
| Initial release / Caregiver | `billingEnabled=false`, no toolbar export button | `BILLING_ENABLED=false`, no report action | Contract-identical absence, automated |
| Patient mode | `sessionStore.mode != .caregiver`, no export UI | report component is not owned by Patient routes | Contract-identical absence, automated |
| Billing-enabled / free entitlement | lock sheet | Material lock dialog | Copy and close contract aligned |
| Billing-enabled / premium | menu picker, generate, share | Material menu bottom sheet, generate, Sharesheet | Behavior aligned; controls remain platform-native |

The billing-enabled captures are component evidence, not a claim that the initial release exposes paid functionality.

## Captures

| State | Evidence |
|---|---|
| Free entitlement lock / light | `android-ui-207-pdf-lock-light-matched.png` |
| Premium period picker / light | `android-ui-207-pdf-picker-light-matched.png` |
| Premium picker / dark and true Android 200% text | `android-ui-207-pdf-picker-dark-font-2.0-matched.png` |
| Actual A4 summary page rendered from generated PDF | `android-ui-207-pdf-render-summary-matched.png` |

## Closed residuals

- The horizontal preset strip clipped the final option. It is now the Android-native analogue of the iOS menu Picker and exposes all five choices without horizontal discovery.
- User-facing action, lock, preset and validation copy now matches current iOS localization.
- On-device generation or Sharesheet exceptions no longer escape the coroutine; the picker remains present with retryable feedback.
- The generated report now has the current iOS two-page minimum, standalone summary, generated timestamp, scheduled/PRN counts, TAKEN+MISSED adherence denominator and exact detail labels.
- Private-cache replacement, `%PDF` signature, two-page count and FileProvider `content://` authority are asserted on API 35.

## Accepted platform differences

- Android uses a Material bottom sheet, exposed dropdown and AlertDialog; iOS uses SwiftUI Form/menu, sheet and Activity view. Navigation semantics, copy, states and ordered actions are the parity contract, not cross-platform pixel identity.
- Custom dates remain ISO text inputs on Android to preserve keyboard and TalkBack editing across minSdk 26; validation and inclusive range behavior are identical to iOS DatePicker limits.
- Physical Sharesheet destinations, OEM document viewers and full TalkBack traversal remain Gate I and are not inferred from emulator evidence.

## Verification

- Focused `CaregiverReportUiTest`: 10/10 on API 35.
- Full API-35 instrumentation: 256/256, 0 skipped/failed.
- JVM: 186/186, 0 skipped/failed.
- Debug/Release assembly and Lint: passed.
