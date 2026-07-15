# C43 UI-101 Current-iOS Exceptional Today States — 2026-07-16

## Scope and capture contract

This checkpoint closes the remaining emulator-verifiable current-runtime UI-101 light-state pairs: scheduled inventory shortage, cached-content update, bounded long medication name, and notification-target routing. The iOS references use the unchanged production component tree from `main@1cf8aef` against an IPv6-local synthetic API. Android uses the production `PatientHomeScreen`/`TodayContent` tree on the API 35 1080 x 2400 emulator with the same dates, slots, medicine names, quantities and statuses.

No production API, real account, patient identity, medication record, dose record or Analytics payload was used. The notification-target reference was reached through the existing production `NotificationDeepLinkRouter` with a temporary DEBUG-only local capture hook; that hook was reverted immediately after capture and `git diff -- ios` is empty. The `Settings` breadcrumb visible above the iOS updating image is Simulator OS chrome created while foregrounding the app, not application UI.

## Matrix

| State | Current iOS | Android result | Diagnostic comparison |
|---|---|---|---|
| Inventory partial | [`ios`](ios-ui-101-patient-inventory-partial-light.png) | [`Android`](android-ui-101-patient-inventory-partial-light-matched.png) | [`side-by-side`](compare-ui-101-patient-inventory-partial-side-by-side.png), [`50% overlay`](compare-ui-101-patient-inventory-partial-overlay-50.png) |
| Cached-content updating | [`ios`](ios-ui-101-patient-updating-light.png) | [`Android`](android-ui-101-patient-updating-light-matched.png) | [`side-by-side`](compare-ui-101-patient-updating-side-by-side.png), [`50% overlay`](compare-ui-101-patient-updating-overlay-50.png) |
| Long medication name | [`ios`](ios-ui-101-patient-long-name-light.png) | [`Android`](android-ui-101-patient-long-name-light-matched.png) | [`side-by-side`](compare-ui-101-patient-long-name-side-by-side.png), [`50% overlay`](compare-ui-101-patient-long-name-overlay-50.png) |
| Notification target | [`ios`](ios-ui-101-patient-notification-target-light.png) | [`Android`](android-ui-101-patient-notification-target-light-matched.png) | [`side-by-side`](compare-ui-101-patient-notification-target-side-by-side.png), [`50% overlay`](compare-ui-101-patient-notification-target-overlay-50.png) |

Diagnostic images normalize both raw captures to 1080 x 2400 for alignment only. Raw PNGs remain authoritative. Platform status/navigation glyphs, OS clock values and the iOS updating capture's Simulator breadcrumb are excluded from app-parity judgment.

## Differences found and closed

1. Android had no iOS inventory-warning card above the next-dose hero. It now derives unique shortage medicines from non-taken scheduled doses, formats the first medicine as `薬名 用量`, supports the multiple-medicine fallback, and renders the exact `お薬がありません` hierarchy.
2. The hero rows now follow the SwiftUI `SlotMedicationRow` contract: `薬名 + 用量`, `1回n錠`, two-line ellipsis, semantic red problem treatment, inventory badge and the matching error indicator. Header pills, slot-specific hero border, `眠前`/`夜` copy and zero-padded `HH:mm` were calibrated at the same time.
3. The cached-content overlay now blocks input with a dimmed full screen and uses the same app illustration, neutral progress treatment, `更新中...` hierarchy, rounded elevated card and iOS-relative dimensions. The Android build generates the illustration resource from the pinned iOS `AppImage` asset so the two platforms cannot silently drift.
4. Long names are constrained to two lines in the next-dose medicine row and keep the primary bulk action reachable.
5. Notification routing no longer replaces the ordinary next-action candidate. Like current iOS, it preserves normal next-dose selection, reloads Today, scrolls to the exact payload slot, consumes the target once, and retains the short transition highlight window.

## Verification

- `PatientTodayContentTest`: 24/24 on API 35.
- Full API 35 instrumentation: 206/206, zero skipped and zero failed.
- JVM: 185/185, zero failed.
- `assembleDebug`: passed.
- `lintDebug`: passed.
- iOS working-tree diff after capture-hook removal: empty.

The current-runtime UI-101 initial loading/failure pair remains under C42; C43 closes inventory-partial, updating, long-name and notification-target light pairs. Physical TalkBack, OEM rendering/lifecycle and notification delivery/tap/process-death remain Gate I requirements.
