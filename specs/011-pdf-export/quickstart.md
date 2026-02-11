# Quickstart: PDF Export of Medication History (011)

## Scope

- Premium caregivers can export medication history (scheduled slots + PRN) as a PDF for a selected date range (up to 90 days).
- Free caregivers see a lock UI with paywall navigation when tapping the export button.
- Patient mode has zero PDF export UI.
- PDF is generated on-device (iOS) — no server-side PDF rendering.
- A new backend endpoint returns aggregated history data for the selected range in a single request.

## Prerequisites

- Feature 008 (Billing Foundation) must be fully implemented and deployed.
- Feature 004 (History Schedule View) must be fully implemented and deployed.
- Feature 007 (PRN Medications) must be fully implemented and deployed.
- Sandbox Apple ID configured for StoreKit2 testing.

## How to Test PDF Export

### Premium Caregiver — Export PDF

1. Open the app in caregiver mode.
2. Purchase premium via sandbox (Settings → "アップグレード"), or restore a previous purchase.
3. Navigate to the history tab.
4. Tap the "PDF出力" button (top bar).
5. The period picker sheet appears with presets:
   - 今月 (1st of current month to today)
   - 先月 (1st to last day of previous month)
   - 直近30日 (last 30 days including today)
   - 直近90日 (last 90 days including today)
   - カスタム (custom From/To date pickers)
6. Select a preset (e.g., "直近30日").
7. The range display shows: "2026/01/13〜2026/02/11（30日間）".
8. Tap "PDFを作成して共有".
9. The full-screen "更新中" overlay appears while data is fetched and the PDF is generated.
10. The share sheet opens with the generated PDF.
11. Share via AirDrop, Mail, Files, etc., or tap "Cancel" to dismiss.
12. After dismissing the share sheet, you return to the period picker.

### Premium Caregiver — Custom Date Range

1. In the period picker, select "カスタム".
2. Set a start date (From) and end date (To) using the date pickers.
3. Validation rules:
   - End date must be today or earlier (Asia/Tokyo).
   - Start date must be on or before the end date.
   - Range must be 90 days or fewer (inclusive).
4. If validation fails, an inline error message appears and the generate button is disabled.
5. If valid, the range and day count are displayed; tap "PDFを作成して共有".

### Free Caregiver — Lock UI

1. Open the app in caregiver mode (ensure no premium purchase — use a fresh sandbox account).
2. Navigate to the history tab.
3. Tap the "PDF出力" button.
4. The lock UI appears with:
   - Title: "プレミアムでPDF出力"
   - Body: "服用履歴のPDF出力はプレミアムでご利用いただけます"
   - Buttons: "アップグレード" / "購入を復元" / "閉じる"
5. Tap "アップグレード" — the paywall sheet is presented.
6. Complete the sandbox purchase, then re-tap "PDF出力" — the period picker now appears.
7. Alternatively, tap "閉じる" to dismiss the lock UI and return to the history tab.

### Patient Mode — No Export UI

1. Sign in as a patient.
2. Navigate to the history tab.
3. There is NO "PDF出力" button or export menu item anywhere.
4. Navigate through month views and day details — no export UI is present.
5. This confirms patient mode has zero PDF export surface.

## PDF Content

The generated PDF contains:

### Page 1 — Summary

- Title: "服用履歴レポート"
- Patient name: "対象者：{displayName}"
- Period: "期間：{from}〜{to}"
- Generated timestamp: "作成日時：{timestamp}" (Asia/Tokyo)
- Summary counts:
  - 定時 (scheduled): TAKEN / MISSED / PENDING counts
  - 頓服 (PRN): total record count
  - 服用率 (adherence rate): TAKEN / (TAKEN + MISSED) × 100% (PENDING excluded)
  - If no TAKEN or MISSED records, adherence shows "—"

### Page 2+ — Daily Detail

- One section per day (date header: "YYYY/MM/DD")
- Slot breakdown (朝 / 昼 / 夜 / 眠前):
  - Medication name, dosage, dose count, status (服用済 / 未服用 / 未記録), recorded time
- PRN section (if any):
  - Medication name, dosage, quantity, recorded time, recorder (患者 / 家族)
- Days with no records show "記録なし" or are omitted

## Period Presets

| Preset | From | To |
|--------|------|----|
| 今月 | 1st of current month (Tokyo) | Today (Tokyo) |
| 先月 | 1st of previous month (Tokyo) | Last day of previous month (Tokyo) |
| 直近30日 | Today - 29 days (Tokyo) | Today (Tokyo) |
| 直近90日 | Today - 89 days (Tokyo) | Today (Tokyo) |
| カスタム | User-defined | User-defined |

All presets are computed at submission time (when "PDFを作成して共有" is tapped), not when the picker opens. This handles the midnight boundary correctly.

## Validation Rules

| Rule | Condition | Error Message |
|------|-----------|---------------|
| End date not in future | to <= todayTokyo | 終了日は今日以前を指定してください |
| Start before end | from <= to | 開始日は終了日以前を指定してください |
| Max 90 days | (to - from + 1) <= 90 | 期間は90日以内で指定してください |

Invalid ranges disable the generate button and show the error inline.

## Backend Report Endpoint

### Request

```
GET /api/patients/{patientId}/history/report?from=2026-01-01&to=2026-01-30
Authorization: Bearer caregiver-{jwt}
```

### Success Response (200)

```json
{
  "patient": { "id": "uuid", "displayName": "太郎" },
  "range": { "from": "2026-01-01", "to": "2026-01-30", "timezone": "Asia/Tokyo", "days": 30 },
  "days": [
    {
      "date": "2026-01-01",
      "slots": {
        "morning": [{ "medicationId": "uuid", "name": "アムロジピン", "dosageText": "5mg", "doseCount": 1, "status": "TAKEN", "recordedAt": "2026-01-01T08:15:00+09:00" }],
        "noon": [], "evening": [], "bedtime": []
      },
      "prn": [{ "medicationId": "uuid", "name": "ロキソプロフェン", "dosageText": "60mg", "quantity": 1, "recordedAt": "2026-01-01T14:30:00+09:00", "recordedBy": "PATIENT" }]
    }
  ]
}
```

### Error Responses

| HTTP | Code | When |
|------|------|------|
| 400 | `INVALID_RANGE` | Missing params, to > today, from > to, range > 90 days |
| 401 | — | No valid session |
| 403 | `NOT_AUTHORIZED` | Patient session or wrong caregiver |
| 403 | `HISTORY_RETENTION_LIMIT` | Free caregiver's from < retention cutoff (optional, 010 alignment) |
| 404 | — | Patient not found (concealment) |

## "更新中" Overlay Behavior

- The overlay blocks all user interaction during:
  1. Report data fetching from the backend
  2. On-device PDF generation
- After both complete, the overlay is dismissed and the share sheet is presented.
- If a network error occurs, the overlay is dismissed and the existing error handling (retry prompt) applies.

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
- To test premium path: complete a sandbox purchase, then tap "PDF出力" and generate a PDF.
- To test restore: delete the app, reinstall, tap "購入を復元" in lock UI, then verify export works.
- To verify patient mode: switch to patient mode and confirm the export button is absent.
- To test with dose data: record several doses and PRN entries across multiple days, then generate a PDF to verify the content.

## Key File Locations

| Component | File |
|-----------|------|
| Report Route | `api/app/api/patients/[patientId]/history/report/route.ts` |
| Report Service | `api/src/services/reportService.ts` |
| Report Validator | `api/src/validators/reportValidator.ts` |
| InvalidRangeError | `api/src/errors/invalidRangeError.ts` |
| iOS APIClient (report fetch) | `ios/MedicationApp/Networking/APIClient.swift` |
| iOS Report DTO | `ios/MedicationApp/Networking/DTOs/HistoryReportDTO.swift` |
| iOS PDFExportButton | `ios/MedicationApp/Features/PDFExport/PDFExportButton.swift` |
| iOS PDFExportLockView | `ios/MedicationApp/Features/PDFExport/PDFExportLockView.swift` |
| iOS PeriodPickerSheet | `ios/MedicationApp/Features/PDFExport/PeriodPickerSheet.swift` |
| iOS PeriodPickerViewModel | `ios/MedicationApp/Features/PDFExport/PeriodPickerViewModel.swift` |
| iOS PDFGenerator | `ios/MedicationApp/Features/PDFExport/PDFGenerator.swift` |
| FeatureGate (existing) | `ios/MedicationApp/Features/Billing/FeatureGate.swift` |
| PaywallView (existing) | `ios/MedicationApp/Features/Billing/PaywallView.swift` |
| EntitlementStore (existing) | `ios/MedicationApp/Features/Billing/EntitlementStore.swift` |
| Localization | `ios/MedicationApp/Resources/Localizable.strings` |
| Integration Tests | `api/tests/integration/history-report.test.ts` |
| Contract Tests | `api/tests/contract/history-report.contract.test.ts` |
| iOS Unit Tests | `ios/MedicationApp/Tests/PDFExport/PeriodPresetTests.swift`, `RangeValidationTests.swift` |
| iOS UI Smoke Tests | `ios/MedicationApp/Tests/PDFExport/PDFExportUITests.swift`, `PDFExportPatientTests.swift` |
