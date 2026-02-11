# Implementation Plan: PDF Export of Medication History

**Branch**: `011-pdf-export` | **Date**: 2026-02-11 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/011-pdf-export/spec.md`

## Summary

Using the billing foundation from 008, the history views from 004, and PRN records from 007, add PDF export of medication history for premium caregivers. A new backend endpoint (`GET /api/patients/{patientId}/history/report?from=&to=`) returns all slot and PRN data for up to 90 days in a single request. The iOS app adds a "PDF出力" button to the caregiver history tab, a period picker sheet (presets + custom), on-device PDF generation via `UIGraphicsPDFRenderer`, and share sheet presentation. Free caregivers see a lock UI with paywall navigation. Patient mode has zero export UI. No new database tables; no server-side PDF rendering.

## Technical Context

**Language/Version**: TypeScript (Node.js >=20, Next.js 16, Prisma 7.3), Swift 6.2 (SwiftUI, iOS 26 SDK)  
**Primary Dependencies**: Next.js App Router, Prisma, Supabase Auth (JWT); SwiftUI, StoreKit2, XCTest, UIGraphicsPDFRenderer  
**Storage**: PostgreSQL via Prisma (`api/prisma/schema.prisma`) — no new tables  
**Testing**: Vitest (API integration/contract), XCTest (iOS unit + UI smoke)  
**Target Platform**: Web API (Vercel) + iOS app  
**Project Type**: Mobile + API  
**Performance Goals**: Report API < 3s p95 for 90-day range; on-device PDF generation < 10s for 90 days dense data  
**Constraints**: Full-screen "更新中" overlay during fetch + generate; caregiver-only (patient mode zero UI); Asia/Tokyo timezone for all date computation; max 90 days (inclusive)  
**Scale/Scope**: MVP: 1 new endpoint, 5 period presets, on-device PDF (summary + daily detail), 1 feature gate (`pdfExport`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Spec-Driven Development**: Pass (spec at `specs/011-pdf-export/spec.md` is source of truth).
- **Traceability**: Pass (every task maps to FR/NFR + acceptance scenarios + tests).
- **Test strategy**: Pass (tests-first; Vitest for backend; XCTest for iOS; no external calls in CI).
- **Security & privacy**: Pass (server-side caregiver auth + scope enforcement; patient sessions rejected; no PII in logs).
- **Performance guardrails**: Pass (report API reuses existing schedule + PRN queries for bounded 90-day range; PDF generation on-device with overlay feedback).
- **UX/accessibility**: Pass (reuses SchedulingRefreshOverlay / FullScreenContainer; localized strings; VoiceOver labels; lock UI follows HistoryRetentionLockView pattern).
- **Documentation**: Pass (quickstart, data model, contracts updated in same branch).

## Project Structure

### Documentation (this feature)

```text
specs/011-pdf-export/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── openapi.yaml     # Phase 1 output — report endpoint + errors
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
api/
├── app/api/patients/[patientId]/history/
│   └── report/
│       └── route.ts                              # NEW: GET /api/patients/{patientId}/history/report
├── src/
│   ├── services/
│   │   └── reportService.ts                      # NEW: aggregate slot+PRN data for date range
│   ├── validators/
│   │   └── reportValidator.ts                    # NEW: from/to/range validation
│   └── errors/
│       └── invalidRangeError.ts                  # NEW: INVALID_RANGE error class
└── tests/
    ├── integration/
    │   └── history-report.test.ts                # NEW: report endpoint integration tests
    └── contract/
        └── history-report.contract.test.ts       # NEW: report response shape contract

ios/MedicationApp/
├── Features/
│   ├── PDFExport/
│   │   ├── PDFExportButton.swift                 # NEW: nav bar button (caregiver-only gate)
│   │   ├── PDFExportLockView.swift               # NEW: free caregiver lock UI → paywall
│   │   ├── PeriodPickerSheet.swift               # NEW: presets + custom date picker sheet
│   │   ├── PeriodPickerViewModel.swift           # NEW: preset calc, validation, report fetch
│   │   └── PDFGenerator.swift                    # NEW: UIGraphicsPDFRenderer PDF builder
│   └── History/
│       ├── CaregiverHistoryView.swift            # MODIFY: add PDFExportButton to toolbar
│       └── HistoryMonthView.swift                # MODIFY: alternative PDFExportButton placement
├── Networking/
│   ├── APIClient.swift                           # MODIFY: add fetchCaregiverHistoryReport
│   └── DTOs/
│       └── HistoryReportDTO.swift                # NEW: report response DTO
├── Resources/
│   └── Localizable.strings                       # MODIFY: add PDF export localization keys
└── Tests/
    └── PDFExport/
        ├── PeriodPresetTests.swift               # NEW: preset date calculation unit tests
        ├── RangeValidationTests.swift            # NEW: range validation unit tests
        ├── PDFExportUITests.swift                # NEW: premium/free flow UI smoke tests
        └── PDFExportPatientTests.swift           # NEW: patient mode zero-UI test
```

**Structure Decision**: Mobile + API split between `api/` (Next.js + Prisma) and `ios/MedicationApp/` (SwiftUI). This feature adds a new report route under the existing caregiver history path, a new `reportService` module, and a new `Features/PDFExport/` module on iOS. It modifies `CaregiverHistoryView` / `HistoryMonthView` to wire the export button and `APIClient` for the new endpoint.

## Complexity Tracking

No constitution violations. No new abstractions or layers introduced beyond the report service and PDF generator.

## Phase 0: Outline & Research

### Research Tasks

All unknowns resolved from 008/004/007 foundation and codebase inspection:

- **PDF rendering approach**: `UIGraphicsPDFRenderer` (built-in iOS, no third-party dependency). Standard A4 page size (595 × 842 points). Rationale: native API, no external dependency, sufficient for tabular medication data. Alternative considered: PDFKit (more complex, overkill for structured text/tables).
- **Report API design**: Single new endpoint `GET /api/patients/{patientId}/history/report?from=&to=` under the existing caregiver history path. Reuses existing services: `scheduleService.getScheduleWithStatus` for scheduled doses, `scheduleResponse.resolveSlot` + `groupDosesByLocalDate` for slot grouping, `prnDoseRecordService.listPrnHistoryItemsByRange` for PRN data. New `reportService.ts` orchestrates these into the spec response shape.
- **Date range iteration**: Server iterates from `from` to `to` (inclusive) using Asia/Tokyo date keys, same approach as the month endpoint's day-by-day loop in `api/app/api/patients/[patientId]/history/month/route.ts`.
- **Validation error pattern**: New `InvalidRangeError` class extending `Error` with `statusCode: 400`, `code: "INVALID_RANGE"`. Same pattern as `HistoryRetentionError` in `api/src/errors/historyRetentionError.ts`.
- **PDF sharing mechanism**: `UIActivityViewController` via a SwiftUI representable wrapper. The existing `ShareLink` usage in `PatientLinkCodeView.swift` handles static strings; PDF requires dynamic file URL presentation after async generation. The representable is presented via `.sheet` after PDF generation completes.
- **Period picker presets**: Pure functions computing `(from, to)` tuples using `Calendar` with `TimeZone(identifier: "Asia/Tokyo")`, matching `AppConstants.defaultTimeZone`. Presets are recomputed at submission time (not picker open time) to handle midnight boundary.
- **Retention alignment**: The report endpoint calls `checkRetentionForDay(from, "caregiver", caregiverId)` on the earliest requested date. Since PDF export requires premium and retention only blocks free users, this check is effectively a no-op for the expected flow but provides defense-in-depth if gate logic changes.
- **No transaction needed**: Report data assembly is read-only (schedule generation + dose record lookup + PRN lookup). No need for Prisma `$transaction`.
- **Patient displayName resolution**: Query existing `Patient` record via `patientRepo` — the patient's `displayName` field is already available from the patient record fetched during scope assertion.

### Output

- `research.md` with all decisions, rationales, and alternatives consolidated.

## Phase 1: Design & Contracts

### Data Model

No new database entities. Report logic uses existing entities from features 002, 003, 004, 007, and 008:
- `DoseRecord` — scheduled dose records with status (TAKEN, MISSED, PENDING)
- `PrnDoseRecord` — as-needed dose records with quantity, timestamp, and actorType
- `Medication` / `PrnMedication` — medication name, dosage text
- `CaregiverEntitlement` — premium status lookup
- `CaregiverPatientLink` — patient-to-caregiver resolution (1:1)
- `Patient` — patient displayName

New artifacts:
- `InvalidRangeError` class (backend domain error, statusCode 400)
- `HistoryReportResponse` model (API transport, not persisted)
- `MAX_REPORT_RANGE_DAYS = 90` constant

### API Contracts

One new endpoint:

`GET /api/patients/{patientId}/history/report?from=YYYY-MM-DD&to=YYYY-MM-DD`

Success response (200):

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

Error responses: 400 `INVALID_RANGE`, 403 `NOT_AUTHORIZED`, 403 `HISTORY_RETENTION_LIMIT`

### Agent Context Update

- Run `.specify/scripts/bash/update-agent-context.sh cursor-agent`

### Output

- `data-model.md`
- `contracts/openapi.yaml`
- `quickstart.md`
- Updated agent context

### Constitution Check (Post-Design)

- Spec-Driven Development: Pass
- Traceability: Pass (every task maps to FR + test)
- Test strategy: Pass (tests written first; no external calls)
- Security & privacy: Pass (server-side auth + scope enforcement; patient sessions rejected; no PII in logs)
- Performance guardrails: Pass (bounded 90-day range; reuses existing indexed queries)
- UX/accessibility: Pass (reuses overlay; localized strings; VoiceOver labels; lock UI follows established pattern)
- Documentation: Pass (quickstart, data model, contracts updated)

## Phase 2: Implementation Plan (Tasks)

### Phase 1: Tests / Contracts (test-first)

1) Backend integration tests for history report
   - **Files**: `api/tests/integration/history-report.test.ts`
   - **Covers**: valid range → 200 with expected structure (patient, range, days[]); invalid range (missing params, to > todayTokyo, from > to, range > 90) → 400 INVALID_RANGE; patient session → 403; wrong caregiver → 404; premium caregiver 90-day range → 200
   - **Tests**: `cd api && npm test`

2) Backend contract test for report response shape
   - **Files**: `api/tests/contract/history-report.contract.test.ts`
   - **Covers**: Response body `{ patient: { id, displayName }, range: { from, to, timezone, days }, days: [{ date, slots, prn }] }` with status 200; error codes are stable strings; date formats are YYYY-MM-DD
   - **Tests**: `cd api && npm test`

3) iOS unit tests for period presets + validation
   - **Files**: `ios/MedicationApp/Tests/PDFExport/PeriodPresetTests.swift`, `ios/MedicationApp/Tests/PDFExport/RangeValidationTests.swift`
   - **Covers**: preset calculations in Asia/Tokyo (this month, last month, last 30 days, last 90 days including month/year boundaries); range validation (to <= today, from <= to, range <= 90, exactly 90 valid, 91 invalid); adherence rate formula (TAKEN/(TAKEN+MISSED), edge case 0/0 → "—"); FeatureGate.pdfExport returns correct access for premium vs free
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

4) iOS UI smoke tests
   - **Files**: `ios/MedicationApp/Tests/PDFExport/PDFExportUITests.swift`, `ios/MedicationApp/Tests/PDFExport/PDFExportPatientTests.swift`
   - **Covers**: caregiver premium → button → picker → generate → share sheet; caregiver free → button → lock UI → paywall; patient mode → no export button; overlay blocks interaction during fetch/generate
   - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 2: Backend (report endpoint)

5) InvalidRangeError class
   - **Files**: `api/src/errors/invalidRangeError.ts`
   - **Action**: `InvalidRangeError` extends `Error` with `statusCode: 400`, `code: "INVALID_RANGE"`. Same pattern as `HistoryRetentionError`.
   - **Tests**: `cd api && npm test`

6) Report validator
   - **Files**: `api/src/validators/reportValidator.ts`
   - **Action**: `validateReportRange(from, to)` — checks required, valid date format, `to` <= todayTokyo, `from` <= `to`, `(to - from + 1)` <= 90. Throws `InvalidRangeError` on failure. Uses `getTodayTokyo()` from `historyRetentionService.ts`.
   - **Tests**: `cd api && npm test`

7) Report service
   - **Files**: `api/src/services/reportService.ts`
   - **Action**: `generateReport(patientId, from, to)` — converts date strings to Date range, calls `getScheduleWithStatus(patientId, rangeFrom, rangeTo, "Asia/Tokyo")` for scheduled doses, calls `groupDosesByLocalDate` + `resolveSlot` for slot grouping per day, calls `listPrnHistoryItemsByRange` for PRN data, fetches patient `displayName` from repo. Assembles into spec response shape with per-day `{ date, slots: { morning[], noon[], evening[], bedtime[] }, prn[] }`. Each slot item includes `{ medicationId, name, dosageText, doseCount, status, recordedAt }` (recordedAt from DoseRecord.takenAt if status is TAKEN). Each PRN item includes `{ medicationId, name, dosageText, quantity, recordedAt, recordedBy }`.
   - **Tests**: `cd api && npm test`

8) Report API route
   - **Files**: `api/app/api/patients/[patientId]/history/report/route.ts`
   - **Action**: `requireCaregiver` → `assertCaregiverPatientScope` → `validateReportRange(from, to)` → (optional) `checkRetentionForDay(from, "caregiver", caregiverId)` → `reportService.generateReport(patientId, from, to)` → JSON response. Catch `InvalidRangeError` → 400 JSON `{ code, message }`. Catch `HistoryRetentionError` → 403 JSON `{ code, message, cutoffDate, retentionDays }`. All other errors → `errorResponse()`.
   - **Tests**: `cd api && npm test`

9) Verify all backend tests pass
   - **Tests**: `cd api && npm test`

### Phase 3: iOS (UI + PDF generation)

10) HistoryReportDTO
    - **Files**: `ios/MedicationApp/Networking/DTOs/HistoryReportDTO.swift`
    - **Action**: Decodable structs: `HistoryReportResponseDTO` (patient, range, days), `HistoryReportPatientDTO` (id, displayName), `HistoryReportRangeDTO` (from, to, timezone, days), `HistoryReportDayDTO` (date, slots, prn), `HistoryReportSlotItemDTO` (medicationId, name, dosageText, doseCount, status, recordedAt?), `HistoryReportPrnItemDTO` (medicationId, name, dosageText, quantity, recordedAt, recordedBy).
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

11) APIClient.fetchCaregiverHistoryReport
    - **Files**: `ios/MedicationApp/Networking/APIClient.swift`
    - **Action**: New method using `makeHistoryRequest(path: "api/patients/\(patientId)/history/report", queryItems: [from, to])`. Decode `HistoryReportResponseDTO`. Add `INVALID_RANGE` error code parsing in `mapErrorIfNeeded` (new case or generic 400 handling).
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

12) PeriodPickerViewModel
    - **Files**: `ios/MedicationApp/Features/PDFExport/PeriodPickerViewModel.swift`
    - **Action**: `@Observable @MainActor` class. `ReportPeriodPreset` enum (thisMonth, lastMonth, last30Days, last90Days, custom). Pure functions `computePreset(_:today:calendar:)` → `(from: Date, to: Date)`. `@Published` state: `selectedPreset`, `customFrom`, `customTo`, `validationError`, `isValid`, `rangeText`, `dayCount`, `isGenerating`. Validation logic. `generateAndShare(apiClient:patientId:)` async method: fetch report → generate PDF → present share sheet.
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

13) PDFGenerator
    - **Files**: `ios/MedicationApp/Features/PDFExport/PDFGenerator.swift`
    - **Action**: `static func generate(from report: HistoryReportResponseDTO) throws -> URL`. Uses `UIGraphicsPDFRenderer` with A4 page size. Page 1: title "服用履歴レポート", patient name, period, timestamp (Tokyo), summary table (TAKEN/MISSED/PENDING counts, PRN count, adherence rate). Page 2+: iterate days, draw date header, slot sections (朝/昼/夜/眠前) with medication rows, PRN section with rows. Returns temp file URL. Uses `NSAttributedString` for styled text rendering.
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

14) PeriodPickerSheet
    - **Files**: `ios/MedicationApp/Features/PDFExport/PeriodPickerSheet.swift`
    - **Action**: SwiftUI `.sheet` view. Preset selector (Picker or segmented-style list). Custom date pickers (DatePicker, `in: ...todayTokyo`). Range display (YYYY/MM/DD〜YYYY/MM/DD, N日間). Inline validation error text. "PDFを作成して共有" button (disabled when invalid). On tap: set isGenerating → show overlay → call viewModel.generateAndShare → on completion present UIActivityViewController via representable → dismiss overlay.
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

15) PDFExportButton + PDFExportLockView
    - **Files**: `ios/MedicationApp/Features/PDFExport/PDFExportButton.swift`, `ios/MedicationApp/Features/PDFExport/PDFExportLockView.swift`
    - **Action**: `PDFExportButton`: toolbar button visible only when `sessionStore.mode == .caregiver`. On tap: check `FeatureGate.isUnlocked(.pdfExport, for: entitlementStore.state)` — premium → show PeriodPickerSheet, free → show PDFExportLockView, unknown → overlay + refresh + retry. `PDFExportLockView`: follows `HistoryRetentionLockView` pattern — title "プレミアムでPDF出力", body, "アップグレード" → `.sheet { PaywallView }`, "購入を復元" → `entitlementStore.restore()`, "閉じる" → dismiss. Accessibility identifiers on all interactive elements.
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

16) Wire into CaregiverHistoryView / HistoryMonthView
    - **Files**: `ios/MedicationApp/Features/History/CaregiverHistoryView.swift` or `ios/MedicationApp/Features/History/HistoryMonthView.swift`
    - **Action**: Add `PDFExportButton` to `.toolbar` with `placement: .topBarTrailing`. Pass `entitlementStore`, `sessionStore`, and resolved `patientId`. Button is not rendered when `sessionStore.mode != .caregiver` (enforced inside PDFExportButton).
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

17) Localizable.strings
    - **Files**: `ios/MedicationApp/Resources/Localizable.strings`
    - **Action**: Add all PDF export localization keys: button label, lock UI (title/body/buttons), period picker (presets, validation errors, generate button), PDF content (title, labels, slot names, status names, recorder names).

18) Verify all iOS tests pass
    - **Tests**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Phase 4: Docs / Finalization

19) Generate spec artifacts
    - **Files**: `specs/011-pdf-export/research.md`, `data-model.md`, `quickstart.md`, `contracts/openapi.yaml`
    - **Action**: Write all design artifacts based on decisions documented in this plan.

20) Agent context update
    - **Action**: Run `.specify/scripts/bash/update-agent-context.sh cursor-agent`

## Acceptance Criteria

- Premium caregiver selects a period (<=90 days), generates a PDF containing both scheduled slots and PRN records, and reaches the share sheet.
- Free caregiver taps export → lock UI → paywall/restore; no PDF is ever generated.
- Patient mode has zero PDF export UI elements across all history screens.
- Backend enforces range validation (to <= todayTokyo, range <= 90 days, from <= to) and caregiver auth + scope.
- Full-screen "更新中" overlay blocks interaction during data fetch and PDF generation.
- All tests pass: iOS unit + UI smoke, backend integration + contract.

## Test Commands

- **iOS**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- **Backend**: `cd api && npm test`
