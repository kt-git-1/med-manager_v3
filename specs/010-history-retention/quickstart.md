# Quickstart: History Retention Limit (010)

## Scope

- Free users (patient and caregiver modes) can view the most recent 30 days of medication history.
- Premium users can view all history without restriction.
- The restriction is enforced server-side — client modifications cannot bypass it.
- Patient mode has zero billing/paywall UI; it displays an informational lock screen instead.
- If the linked caregiver has premium, the patient automatically gets full history access.
- No database data is deleted — this is a view restriction only.

## Prerequisites

- Feature 008 (Billing Foundation) must be fully implemented and deployed.
- Feature 004 (History Schedule View) must be fully implemented and deployed.
- Sandbox Apple ID configured for StoreKit2 testing.

## How to Test History Retention

### Free Caregiver — Recent History (Allowed)

1. Open the app in caregiver mode (ensure no premium purchase).
2. Navigate to the history tab.
3. The banner reads: "無料：直近30日まで（{cutoffDate}〜今日）".
4. View the current month or a day within the last 30 days.
5. History loads successfully with dose data.

### Free Caregiver — Old History (Blocked)

1. With the same free caregiver, navigate to a month older than 30 days (e.g., swipe back 2 months).
2. The lock overlay appears with the message: "30日より前の履歴はプレミアムで閲覧できます".
3. The overlay shows 3 buttons: "アップグレード" / "購入を復元" / "閉じる".
4. Tap "アップグレード" — the paywall sheet is presented.
5. Tap "閉じる" to dismiss and return to recent history.

### Premium Caregiver — Unlimited History

1. Purchase premium via sandbox (or restore a previous purchase).
2. Navigate to the history tab.
3. The banner reads: "全期間表示中".
4. Navigate to any month or day, including dates older than 30 days.
5. History loads successfully without any lock overlay.

### Free Patient — Old History (Blocked, No Billing UI)

1. Sign in as a patient linked to a free caregiver.
2. Navigate to the history tab.
3. The banner reads: "無料：直近30日まで（{cutoffDate}〜今日）".
4. Navigate to a month older than 30 days.
5. The lock overlay appears with: "30日より前の履歴はプレミアムで閲覧できます。家族がプレミアムの場合は自動で表示されます。"
6. There are NO "アップグレード" or "購入を復元" buttons — only a "更新" (refresh) button.
7. No purchase or paywall UI is visible anywhere.

### Patient Linked to Premium Caregiver — Unlimited History

1. Ensure the linked caregiver has purchased premium (via caregiver mode sandbox purchase).
2. Sign in as the patient.
3. Navigate to the history tab.
4. The banner reads: "全期間表示中".
5. Navigate to any month or day — history loads successfully.
6. The patient did not need to purchase anything; premium is inherited from the caregiver.

### Patient Refreshes After Caregiver Upgrades

1. Start with a free patient linked to a free caregiver.
2. Navigate to old history — lock overlay appears.
3. In a separate session, purchase premium as the caregiver.
4. Back in patient mode, tap "更新" on the lock overlay (or re-navigate to the locked month).
5. The history now loads successfully — the caregiver's premium status is reflected.

## Where the Retention Gate Triggers

| Endpoint | Trigger | Free Result | Premium Result |
|----------|---------|-------------|----------------|
| Month (patient) | `firstDayOfMonth < cutoffDate` | 403 HISTORY_RETENTION_LIMIT | 200 with data |
| Day (patient) | `date < cutoffDate` | 403 HISTORY_RETENTION_LIMIT | 200 with data |
| Month (caregiver) | `firstDayOfMonth < cutoffDate` | 403 HISTORY_RETENTION_LIMIT | 200 with data |
| Day (caregiver) | `date < cutoffDate` | 403 HISTORY_RETENTION_LIMIT | 200 with data |

The same rule applies to both patient and caregiver modes. The only difference is how premium is resolved:
- **Caregiver**: directly from `CaregiverEntitlement`
- **Patient**: via `CaregiverPatientLink` → linked caregiver → `CaregiverEntitlement`

## Backend Enforcement

The server enforces the retention limit on all 4 history endpoints:

- If the user is NOT premium and the requested date/month is before cutoffDate, the request is rejected with:
  ```
  HTTP 403
  {
    "code": "HISTORY_RETENTION_LIMIT",
    "message": "履歴の閲覧は直近30日間に制限されています。",
    "cutoffDate": "2026-01-12",
    "retentionDays": 30
  }
  ```
- The iOS app intercepts this error and shows the appropriate lock UI.
- This ensures a modified client cannot bypass the retention limit.

## Patient Mode

- Patient mode displays an informational lock screen when accessing old history.
- No paywall, upgrade, or purchase buttons appear in patient mode.
- The lock message explains that the caregiver's premium status controls access.
- If the caregiver upgrades, the patient's access is updated on the next API call.

## Error Contract

| Field | Type | Description |
|-------|------|-------------|
| `code` | string | Stable identifier: `"HISTORY_RETENTION_LIMIT"` |
| `message` | string | Human-readable (informational, do not parse) |
| `cutoffDate` | string (date) | Earliest viewable date, YYYY-MM-DD, Asia/Tokyo |
| `retentionDays` | number | Days of history available on free plan (currently 30) |

## "更新中" Overlay Behavior

- All history fetch operations show the full-screen "更新中" overlay blocking user interaction.
- If the entitlement state is unknown when navigating to old history, the app refreshes entitlements first.
- After refresh, the gate decision is applied (lock overlay or data loads).

## Local Development

### API

```bash
cd api
npm test
```

### iOS

```bash
xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test
```

### Sandbox Testing Tips

- Use a Sandbox Apple ID configured in App Store Connect.
- To test free path: ensure no premium purchase exists (or use a fresh sandbox account).
- To test premium caregiver path: complete a sandbox purchase, then navigate to old history.
- To test patient inheriting premium: purchase as caregiver, then switch to patient mode and verify full access.
- To test patient refresh after caregiver upgrade: start as free patient, lock old history, purchase as caregiver in another session, then tap "更新" as patient.
- To test restore: delete the app, reinstall, tap "購入を復元" in Settings, then verify history access.

## Key File Locations

| Component | File |
|-----------|------|
| Retention Constant | `api/src/services/historyRetentionConstants.ts` |
| HistoryRetentionError | `api/src/errors/historyRetentionError.ts` |
| Retention Service | `api/src/services/historyRetentionService.ts` |
| Patient History Month Route | `api/app/api/patient/history/month/route.ts` |
| Patient History Day Route | `api/app/api/patient/history/day/route.ts` |
| Caregiver History Month Route | `api/app/api/patients/[patientId]/history/month/route.ts` |
| Caregiver History Day Route | `api/app/api/patients/[patientId]/history/day/route.ts` |
| iOS APIError Case | `ios/MedicationApp/Networking/APIError.swift` |
| iOS APIClient (error parsing) | `ios/MedicationApp/Networking/APIClient.swift` |
| iOS HistoryViewModel | `ios/MedicationApp/Features/History/HistoryViewModel.swift` |
| iOS HistoryMonthView (banner) | `ios/MedicationApp/Features/History/HistoryMonthView.swift` |
| iOS HistoryRetentionLockView | `ios/MedicationApp/Features/History/HistoryRetentionLockView.swift` |
| FeatureGate (cutoff helper) | `ios/MedicationApp/Features/Billing/FeatureGate.swift` |
| PaywallView (existing) | `ios/MedicationApp/Features/Billing/PaywallView.swift` |
| EntitlementStore (existing) | `ios/MedicationApp/Features/Billing/EntitlementStore.swift` |
| Localization | `ios/MedicationApp/Resources/Localizable.strings` |
| Integration Tests | `api/tests/integration/history-retention.test.ts` |
| Contract Tests | `api/tests/contract/history-retention.contract.test.ts` |
| iOS Unit Tests | `ios/MedicationApp/Tests/History/HistoryRetentionTests.swift` |
| iOS UI Smoke Tests | `ios/MedicationApp/Tests/History/HistoryRetentionUITests.swift` |
