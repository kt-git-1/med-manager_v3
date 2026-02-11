# Tasks: PDF Export of Medication History

**Input**: Design documents from `/specs/011-pdf-export/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/openapi.yaml

**Tests**: Tests are REQUIRED (spec Testing Requirements mandate integration, contract, unit, and UI smoke tests). Phase 1 is tests-first.

**Organization**: Tasks are grouped into 4 phases (Tests-first, Backend, iOS, Docs) with user story labels for traceability.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 = Premium caregiver exports PDF, US2 = Free caregiver lock + paywall, US3 = Patient mode zero export UI, US4 = Backend report endpoint + validation, US5 = Docs / contracts

## Path Conventions

- **Backend**: `api/` (Next.js App Router + Prisma)
- **iOS**: `ios/MedicationApp/` (SwiftUI + StoreKit2)

## Test Commands

- **iOS**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- **Backend**: `cd api && npm test`

---

## Phase 1: Tests (Required First)

**Purpose**: Write all tests before implementation. Tests MUST fail initially and pass after their corresponding implementation phase completes.

### Backend Tests

- [x] T001 [P] [US4] Create test fixtures and mock setup for history report integration tests in `api/tests/integration/history-report.test.ts`
  - **Why**: Integration tests need to mock auth verifiers, Prisma queries, schedule/PRN services, and patient repo to test report endpoint validation, auth, and response structure (FR-007, FR-008, FR-009, FR-010, NFR-001)
  - **Files**: `api/tests/integration/history-report.test.ts`
  - **Done**: (1) Mock `verifySupabaseJwt` for caregiver sessions, (2) Mock `patientSessionVerifier` for patient sessions, (3) Mock `getPatientRecordForCaregiver` for scope assertion (returns patient with `displayName`), (4) Mock `getScheduleWithStatus` to return minimal schedule data, (5) Mock `groupDosesByLocalDate` + `resolveSlot` for slot grouping, (6) Mock `listPrnHistoryItemsByRange` for PRN data, (7) Mock `getTodayTokyo` to return a fixed date for deterministic tests, (8) Helper functions `caregiverHeaders()` and `patientHeaders()` for auth, (9) Helper `mockTodayTokyo(dateStr)` for date pinning
  - **Test**: `cd api && npm test`

- [x] T002 [P] [US4] Create integration test cases for report endpoint validation, auth, and data response in `api/tests/integration/history-report.test.ts`
  - **Why**: Validates server-side range validation, auth enforcement, and response structure for valid/invalid requests (FR-007 through FR-010, NFR-001, SC-006, SC-007)
  - **Files**: `api/tests/integration/history-report.test.ts`
  - **Done**: Test cases for: (1) valid range (30 days) → 200 with `{ patient: { id, displayName }, range: { from, to, timezone: "Asia/Tokyo", days }, days: [...] }`, (2) valid range 90 days (boundary) → 200, (3) missing `from` param → 400 `INVALID_RANGE`, (4) missing `to` param → 400 `INVALID_RANGE`, (5) `to` > todayTokyo → 400 `INVALID_RANGE`, (6) `from` > `to` → 400 `INVALID_RANGE`, (7) range > 90 days (91) → 400 `INVALID_RANGE`, (8) patient session → 403 (existing auth rejection), (9) wrong caregiver (unlinked patient) → 404 (concealment), (10) unauthenticated → 401
  - **Test**: `cd api && npm test`

- [x] T003 [P] [US4] Create contract test for report response shape in `api/tests/contract/history-report.contract.test.ts`
  - **Why**: Validates the stable response schema that the iOS client relies on for DTO decoding (contracts/openapi.yaml HistoryReportResponse)
  - **Files**: `api/tests/contract/history-report.contract.test.ts`
  - **Done**: Test cases for: (1) response status is 200, (2) `patient` has `id` (string) and `displayName` (string), (3) `range` has `from` (YYYY-MM-DD), `to` (YYYY-MM-DD), `timezone` ("Asia/Tokyo"), `days` (integer), (4) `days` is array, each element has `date` (YYYY-MM-DD), `slots` object with `morning`/`noon`/`evening`/`bedtime` arrays, `prn` array, (5) slot items have `medicationId`, `name`, `dosageText`, `doseCount`, `status` (one of TAKEN/MISSED/PENDING), optional `recordedAt`, (6) PRN items have `medicationId`, `name`, `dosageText`, `quantity`, `recordedAt`, `recordedBy` (PATIENT or CAREGIVER), (7) error response for invalid range has `code: "INVALID_RANGE"` and `message` string
  - **Test**: `cd api && npm test`

### iOS Unit Tests

- [x] T004 [P] [US1] Create unit tests for period preset calculations (Asia/Tokyo) in `ios/MedicationApp/Tests/PDFExport/PeriodPresetTests.swift`
  - **Why**: Validates that preset date ranges compute correctly in Asia/Tokyo timezone, including month/year boundaries (FR-003, FR-004, spec iOS Unit Tests)
  - **Files**: `ios/MedicationApp/Tests/PDFExport/PeriodPresetTests.swift`
  - **Done**: Test cases for: (1) "今月" preset on 2026-02-11 → from: 2026-02-01, to: 2026-02-11, (2) "先月" preset on 2026-02-11 → from: 2026-01-01, to: 2026-01-31, (3) "直近30日" preset on 2026-02-11 → from: 2026-01-13, to: 2026-02-11 (inclusive 30 days), (4) "直近90日" preset on 2026-02-11 → from: 2025-11-14, to: 2026-02-11 (inclusive 90 days), (5) "今月" on January 1st → from: 2026-01-01, to: 2026-01-01 (single day), (6) "先月" on January 2026 → from: 2025-12-01, to: 2025-12-31 (year boundary), (7) adherence rate: TAKEN=8, MISSED=2, PENDING=5 → rate = 80%, (8) adherence rate: TAKEN=0, MISSED=0, PENDING=3 → "—" (not applicable), (9) `FeatureGate.pdfExport` requires `.premium` tier, (10) `FeatureGate.isUnlocked(.pdfExport, for: .premium)` returns true, `for: .free` returns false
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T005 [P] [US1] Create unit tests for range validation logic in `ios/MedicationApp/Tests/PDFExport/RangeValidationTests.swift`
  - **Why**: Validates client-side range validation rules match server-side enforcement (FR-005, spec iOS Unit Tests)
  - **Files**: `ios/MedicationApp/Tests/PDFExport/RangeValidationTests.swift`
  - **Done**: Test cases for: (1) to > todayTokyo → invalid with error "終了日は今日以前を指定してください", (2) from > to → invalid with error "開始日は終了日以前を指定してください", (3) range = 91 days → invalid with error "期間は90日以内で指定してください", (4) range = 90 days (exactly) → valid, (5) range = 1 day (from == to) → valid, (6) to = todayTokyo → valid, (7) valid range → `isValid` true, `validationError` nil, `dayCount` correct, `rangeText` formatted as "YYYY/MM/DD〜YYYY/MM/DD"
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### iOS UI Smoke Tests

- [x] T006 [P] [US1] Create UI smoke test for premium caregiver PDF export flow in `ios/MedicationApp/Tests/PDFExport/PDFExportUITests.swift`
  - **Why**: Validates the end-to-end premium caregiver flow: button → picker → generate → share sheet (FR-001, FR-014, FR-015, SC-001)
  - **Files**: `ios/MedicationApp/Tests/PDFExport/PDFExportUITests.swift`
  - **Done**: Test cases for: (1) premium caregiver history tab shows "PDF出力" button with accessibilityIdentifier "PDFExportButton", (2) tapping button presents PeriodPickerSheet, (3) selecting a preset shows range text and enables "PDFを作成して共有" button, (4) tapping generate shows "更新中" overlay (accessibilityIdentifier "SchedulingRefreshOverlay" or "PDFExportOverlay"), (5) after generation completes, share sheet is presented
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T007 [P] [US2] Create UI smoke test for free caregiver lock UI and paywall redirect in `ios/MedicationApp/Tests/PDFExport/PDFExportUITests.swift`
  - **Why**: Validates the free caregiver gate: button → lock UI → paywall path (FR-016, SC-004)
  - **Files**: `ios/MedicationApp/Tests/PDFExport/PDFExportUITests.swift`
  - **Done**: Test cases for: (1) free caregiver history tab shows "PDF出力" button, (2) tapping button presents PDFExportLockView (accessibilityIdentifier "PDFExportLockView"), (3) lock view contains "アップグレード" button, (4) lock view contains "購入を復元" button, (5) lock view contains "閉じる" button, (6) tapping "アップグレード" presents PaywallView sheet, (7) tapping "閉じる" dismisses lock view
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T008 [P] [US3] Create UI smoke test for patient mode with zero export UI in `ios/MedicationApp/Tests/PDFExport/PDFExportPatientTests.swift`
  - **Why**: Validates that patient mode has absolutely no PDF export button or menu item (FR-002, SC-005)
  - **Files**: `ios/MedicationApp/Tests/PDFExport/PDFExportPatientTests.swift`
  - **Done**: Test cases for: (1) patient history tab does NOT contain element with accessibilityIdentifier "PDFExportButton", (2) patient navigates through month view and day detail — no export-related UI element present, (3) no "PDF出力" text appears anywhere in history screens
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

**Checkpoint**: All test files exist and define expected behavior. Tests fail because backend report endpoint and iOS PDF export module do not exist yet.

---

## Phase 2: Backend — Report Endpoint (US4)

**Purpose**: Add InvalidRangeError, report validator, report service, and report route. After this phase, backend tests (T001-T003) must pass.

### Error Class & Validator

- [x] T009 [P] [US4] Create InvalidRangeError class in `api/src/errors/invalidRangeError.ts`
  - **Why**: Domain error for 400 responses with stable `INVALID_RANGE` code, following `HistoryRetentionError` pattern from `api/src/errors/historyRetentionError.ts` (FR-009, data-model.md InvalidRangeError)
  - **Files**: `api/src/errors/invalidRangeError.ts`
  - **Done**: (1) `InvalidRangeError` extends `Error`, (2) has properties: `code: "INVALID_RANGE"`, `statusCode: 400`, (3) constructor accepts optional message, defaults to `"指定された期間が不正です。"`, (4) file is importable from route and validator
  - **Test**: `cd api && npm test`

- [x] T010 [P] [US4] Create report validator in `api/src/validators/reportValidator.ts`
  - **Why**: Centralised range validation that throws `InvalidRangeError` on failure — reuses `getTodayTokyo()` from `historyRetentionService.ts` for consistent timezone handling (FR-009, research decision 4)
  - **Files**: `api/src/validators/reportValidator.ts`
  - **Done**: (1) Exports `validateReportRange(from: string | null, to: string | null): void`, (2) Throws `InvalidRangeError` if: `from` is null/undefined, `to` is null/undefined, `from` or `to` not valid YYYY-MM-DD, `to` > todayTokyo, `from` > `to`, `(to - from + 1)` > 90 days, (3) Exports `MAX_REPORT_RANGE_DAYS = 90` constant, (4) Uses `getTodayTokyo()` from `api/src/services/historyRetentionService.ts` for Asia/Tokyo date
  - **Test**: `cd api && npm test`

### Report Service

- [x] T011 [US4] Implement report service in `api/src/services/reportService.ts`
  - **Why**: Orchestrates schedule + PRN data aggregation for the date range, assembling the spec response shape. Reuses existing `getScheduleWithStatus`, `groupDosesByLocalDate`, `resolveSlot`, and `listPrnHistoryItemsByRange` (FR-010, research decisions 2-3, data-model.md report assembly)
  - **Files**: `api/src/services/reportService.ts`
  - **Done**: (1) Exports `generateReport(patientId: string, from: string, to: string, patientDisplayName: string): Promise<HistoryReportResponse>`, (2) Converts `from`/`to` strings to Date range using `getMonthRange`-style logic with `Asia/Tokyo` timezone, (3) Calls `getScheduleWithStatus(patientId, rangeFrom, rangeTo, "Asia/Tokyo")` for scheduled doses, (4) Calls `groupDosesByLocalDate(doses, "Asia/Tokyo")` to group by date, (5) For each day in range: iterates grouped doses, calls `resolveSlot()` to assign to morning/noon/evening/bedtime, maps to `{ medicationId, name, dosageText, doseCount, status (uppercase), recordedAt (if TAKEN) }`, (6) Calls `listPrnHistoryItemsByRange({ patientId, from, to, timeZone: "Asia/Tokyo" })` for PRN data, maps to `{ medicationId, name, dosageText, quantity, recordedAt, recordedBy }`, (7) Returns `{ patient: { id, displayName }, range: { from, to, timezone: "Asia/Tokyo", days: N }, days: [...] }`, (8) Days with no scheduled doses and no PRN still appear with empty arrays
  - **Test**: `cd api && npm test`

### Route

- [x] T012 [US4] Implement report API route in `api/app/api/patients/[patientId]/history/report/route.ts`
  - **Why**: The single new endpoint that serves PDF export data. Follows the exact auth + scope + error handling pattern from `api/app/api/patients/[patientId]/history/month/route.ts` (FR-007, FR-008, NFR-001)
  - **Files**: `api/app/api/patients/[patientId]/history/report/route.ts`
  - **Done**: (1) `export const runtime = "nodejs"`, (2) GET handler: extract `from`/`to` from searchParams, (3) Auth: `getBearerToken` → `isCaregiverToken` check (throw `AuthError("Forbidden", 403)` if not caregiver) → `requireCaregiver(authHeader)` → `assertCaregiverPatientScope(session.caregiverUserId, patientId)`, (4) Scope assertion returns patient record — extract `displayName`, (5) Call `validateReportRange(from, to)` — throws `InvalidRangeError` on failure, (6) Optional: call `checkRetentionForDay(from, "caregiver", session.caregiverUserId)` for defense-in-depth, (7) Call `generateReport(patientId, from, to, displayName)`, (8) Return `new Response(JSON.stringify(result), { headers: { "content-type": "application/json" } })`, (9) Catch `InvalidRangeError` → 400 `{ code: error.code, message: error.message }`, (10) Catch `HistoryRetentionError` → 403 `{ code: "HISTORY_RETENTION_LIMIT", message, cutoffDate, retentionDays }`, (11) All other errors → `errorResponse(error)`
  - **Test**: `cd api && npm test`

### Verification

- [x] T013 [US4] Verify all backend tests pass (T001-T003) against implemented code
  - **Why**: Confirms report endpoint validation, auth enforcement, and response structure satisfy test expectations (SC-006, SC-007, SC-008)
  - **Files**: `api/tests/integration/history-report.test.ts`, `api/tests/contract/history-report.contract.test.ts`
  - **Done**: `cd api && npm test` exits 0 with all history-report integration and contract tests passing
  - **Test**: `cd api && npm test`

**Checkpoint**: Backend report endpoint complete. Valid caregiver requests return structured history data. Invalid ranges return 400 INVALID_RANGE. Patient sessions and unauthorized requests are rejected. Integration and contract tests pass.

---

## Phase 3: iOS — UI + PDF Generation + Gating (US1 + US2 + US3)

**Purpose**: Add report DTO, API client method, period picker, PDF generator, export button, lock view, and wire everything into the caregiver history tab. After this phase, iOS tests (T004-T008) must pass.

### Networking Layer

- [x] T014 [P] [US1] Create HistoryReportDTO structs in `ios/MedicationApp/Networking/DTOs/HistoryReportDTO.swift`
  - **Why**: Decodable DTOs matching the report API response shape. iOS DTO pattern follows `HistoryDTO.swift` (existing from 004).
  - **Files**: `ios/MedicationApp/Networking/DTOs/HistoryReportDTO.swift`
  - **Done**: (1) `HistoryReportResponseDTO: Decodable` with `patient: HistoryReportPatientDTO`, `range: HistoryReportRangeDTO`, `days: [HistoryReportDayDTO]`, (2) `HistoryReportPatientDTO: Decodable` with `id: String`, `displayName: String`, (3) `HistoryReportRangeDTO: Decodable` with `from: String`, `to: String`, `timezone: String`, `days: Int`, (4) `HistoryReportDayDTO: Decodable` with `date: String`, `slots: HistoryReportSlotsDTO`, `prn: [HistoryReportPrnItemDTO]`, (5) `HistoryReportSlotsDTO: Decodable` with `morning`, `noon`, `evening`, `bedtime` as `[HistoryReportSlotItemDTO]`, (6) `HistoryReportSlotItemDTO: Decodable` with `medicationId: String`, `name: String`, `dosageText: String`, `doseCount: Double`, `status: String`, `recordedAt: String?`, (7) `HistoryReportPrnItemDTO: Decodable` with `medicationId: String`, `name: String`, `dosageText: String`, `quantity: Double`, `recordedAt: String`, `recordedBy: String`
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T015 [US1] Add fetchCaregiverHistoryReport method to APIClient in `ios/MedicationApp/Networking/APIClient.swift`
  - **Why**: New API client method for the report endpoint, following the exact pattern of `fetchCaregiverHistoryMonth` (lines 295-316). Also adds `INVALID_RANGE` error handling in `mapErrorIfNeeded`.
  - **Files**: `ios/MedicationApp/Networking/APIClient.swift`
  - **Done**: (1) New method `func fetchCaregiverHistoryReport(patientId: String? = nil, from: String, to: String) async throws -> HistoryReportResponseDTO`, (2) Uses `resolvedCaregiverPatientId` for patient ID resolution, (3) Builds query items `[from, to]`, (4) Uses `makeHistoryRequest(path: "api/patients/\(patientId)/history/report", queryItems:)`, (5) Calls `mapErrorIfNeeded(response:data:)`, (6) Decodes with `JSONDecoder` (dateDecodingStrategy: .iso8601), (7) In `mapErrorIfNeeded` case 400 block: parse `{ code: "INVALID_RANGE" }` and throw a descriptive error (or let generic 400 handling apply)
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### ViewModel & PDF Generator

- [x] T016 [P] [US1] Create PeriodPickerViewModel with preset calculation and validation in `ios/MedicationApp/Features/PDFExport/PeriodPickerViewModel.swift`
  - **Why**: Encapsulates period preset computation (Asia/Tokyo), range validation, range display formatting, and the generate-and-share flow (FR-003, FR-004, FR-005, FR-006, research decision 6)
  - **Files**: `ios/MedicationApp/Features/PDFExport/PeriodPickerViewModel.swift`
  - **Done**: (1) `@Observable @MainActor` class, (2) `ReportPeriodPreset` enum: `thisMonth`, `lastMonth`, `last30Days`, `last90Days`, `custom`, (3) Static pure function `computePreset(_:today:calendar:) -> (from: Date, to: Date)` using `Calendar` with `TimeZone(identifier: "Asia/Tokyo")`, (4) `@Published` state: `selectedPreset`, `customFrom: Date`, `customTo: Date`, `validationError: String?`, `isValid: Bool` (computed), `rangeText: String` (computed, "YYYY/MM/DD〜YYYY/MM/DD"), `dayCount: Int` (computed, inclusive), `isGenerating: Bool`, (5) Validation logic: to <= todayTokyo, from <= to, dayCount <= 90; sets `validationError` with appropriate Japanese message on failure, (6) `func generateAndShare(apiClient:patientId:) async throws -> URL` — fetches report → calls `PDFGenerator.generate(from:)` → returns temp file URL, (7) Presets recomputed at submission time (not picker open time) per research decision 6
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T017 [P] [US1] Create PDFGenerator using UIGraphicsPDFRenderer in `ios/MedicationApp/Features/PDFExport/PDFGenerator.swift`
  - **Why**: On-device PDF generation with summary page and day-by-day detail. Uses `UIGraphicsPDFRenderer` (research decision 1). No third-party dependency (FR-011, FR-012, FR-013)
  - **Files**: `ios/MedicationApp/Features/PDFExport/PDFGenerator.swift`
  - **Done**: (1) `static func generate(from report: HistoryReportResponseDTO) throws -> URL`, (2) A4 page size (595 × 842 points), (3) Page 1 — summary: title "服用履歴レポート", patient name ("対象者：{displayName}"), period ("期間：{from}〜{to}"), generated timestamp ("作成日時：{timestamp}" in Asia/Tokyo), summary counts (定時: TAKEN/MISSED/PENDING, 頓服: PRN count), adherence rate (TAKEN/(TAKEN+MISSED), or "—" if denominator is 0), (4) Page 2+ — daily detail: date header "YYYY/MM/DD", slot sections (朝/昼/夜/眠前) with medication rows (name, dosageText, doseCount, status label 服用済/未服用/未記録, recordedAt if available), PRN section (頓服) with rows (name, dosageText, quantity, recordedAt, recorder 患者/家族), (5) Empty days show "記録なし", (6) Uses `NSAttributedString` for styled text, (7) Writes PDF to temp directory, returns file URL, (8) Page breaks when content exceeds page height
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Views

- [x] T018 [US1] Create PeriodPickerSheet view in `ios/MedicationApp/Features/PDFExport/PeriodPickerSheet.swift`
  - **Why**: SwiftUI sheet for period selection with presets, custom date pickers, validation display, and generate button. Triggers the fetch → generate → share flow (FR-003, FR-005, FR-006, FR-014, FR-015)
  - **Files**: `ios/MedicationApp/Features/PDFExport/PeriodPickerSheet.swift`
  - **Done**: (1) Accepts `PeriodPickerViewModel`, `APIClient`, `patientId: String`, (2) Preset selector (Picker or List), (3) When preset is `.custom`: two `DatePicker` controls for from/to, constrained to `...todayTokyo`, (4) Range display: "YYYY/MM/DD〜YYYY/MM/DD（N日間）" from viewModel computed properties, (5) Inline validation error text (red) from `viewModel.validationError`, (6) "PDFを作成して共有" button — disabled when `!viewModel.isValid` or `viewModel.isGenerating`, (7) On tap: set `isGenerating = true` → show SchedulingRefreshOverlay → call `viewModel.generateAndShare` → on success present `UIActivityViewController` via representable → dismiss overlay, (8) On error: dismiss overlay, show error alert, (9) `UIActivityViewController` representable presented via `.sheet(item:)` with the generated file URL
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T019 [P] [US2] Create PDFExportLockView for free caregiver paywall gate in `ios/MedicationApp/Features/PDFExport/PDFExportLockView.swift`
  - **Why**: Lock overlay shown when a free caregiver taps "PDF出力". Follows `HistoryRetentionLockView` pattern from `ios/MedicationApp/Features/History/HistoryRetentionLockView.swift` (FR-016, research decision 9)
  - **Files**: `ios/MedicationApp/Features/PDFExport/PDFExportLockView.swift`
  - **Done**: (1) Accepts `entitlementStore: EntitlementStore`, `onDismiss: () -> Void`, (2) Title: "プレミアムでPDF出力" (from `NSLocalizedString`), (3) Body: "服用履歴のPDF出力はプレミアムでご利用いただけます", (4) "アップグレード" button → presents `PaywallView(entitlementStore:)` via `.sheet(isPresented:)`, (5) "購入を復元" button → calls `entitlementStore.restore()`, (6) "閉じる" button → calls `onDismiss`, (7) accessibilityIdentifier "PDFExportLockView" on root container, (8) All buttons have distinct accessibility identifiers, (9) `@State private var showPaywall = false` for sheet control
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T020 [US1] Create PDFExportButton with gate logic in `ios/MedicationApp/Features/PDFExport/PDFExportButton.swift`
  - **Why**: Toolbar button visible only in caregiver mode. On tap: checks `FeatureGate.pdfExport` — premium → PeriodPickerSheet, free → PDFExportLockView, unknown → refresh + retry (FR-001, FR-002, spec edge case "entitlement unknown")
  - **Files**: `ios/MedicationApp/Features/PDFExport/PDFExportButton.swift`
  - **Done**: (1) Accepts `entitlementStore: EntitlementStore`, `sessionStore: SessionStore`, `patientId: String`, `apiClient: APIClient`, (2) Renders toolbar button only when `sessionStore.mode == .caregiver` — otherwise returns `EmptyView()`, (3) Button label: `Image(systemName: "square.and.arrow.up")` or `Text("PDF出力")`, (4) accessibilityIdentifier "PDFExportButton", (5) On tap: if `FeatureGate.isUnlocked(.pdfExport, for: entitlementStore.state)` → set `showPicker = true`, else if `entitlementStore.state == .unknown` → show overlay, refresh, retry gate check, else → set `showLock = true`, (6) `.sheet(isPresented: $showPicker) { PeriodPickerSheet(...) }`, (7) `.sheet(isPresented: $showLock) { PDFExportLockView(...) }`, (8) `@State` vars for `showPicker`, `showLock`, `showOverlay`
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### View Wiring

- [x] T021 [US1] Wire PDFExportButton into CaregiverHistoryView or HistoryMonthView toolbar in `ios/MedicationApp/Features/History/CaregiverHistoryView.swift` or `ios/MedicationApp/Features/History/HistoryMonthView.swift`
  - **Why**: Adds the export entry point to the caregiver history tab's navigation bar (plan task 16)
  - **Files**: `ios/MedicationApp/Features/History/CaregiverHistoryView.swift` or `ios/MedicationApp/Features/History/HistoryMonthView.swift`
  - **Done**: (1) Add `.toolbar { ToolbarItem(placement: .topBarTrailing) { PDFExportButton(entitlementStore:sessionStore:patientId:apiClient:) } }` to the appropriate view, (2) Pass `entitlementStore`, `sessionStore`, resolved `patientId`, and `apiClient` from the view's environment or init, (3) Button only renders for caregiver mode (enforced inside PDFExportButton), (4) Does not affect patient mode history view
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Localization

- [x] T022 [P] [US1] Add PDF export localization strings to `ios/MedicationApp/Resources/Localizable.strings`
  - **Why**: All user-facing copy must be localized (constitution III, spec UX Copy section)
  - **Files**: `ios/MedicationApp/Resources/Localizable.strings`
  - **Done**: Keys added: (1) `"pdfexport.button" = "PDF出力";`, (2) `"pdfexport.lock.title" = "プレミアムでPDF出力";`, (3) `"pdfexport.lock.body" = "服用履歴のPDF出力はプレミアムでご利用いただけます";`, (4) `"pdfexport.lock.upgrade" = "アップグレード";`, (5) `"pdfexport.lock.restore" = "購入を復元";`, (6) `"pdfexport.lock.close" = "閉じる";`, (7) `"pdfexport.picker.thisMonth" = "今月";`, (8) `"pdfexport.picker.lastMonth" = "先月";`, (9) `"pdfexport.picker.last30" = "直近30日";`, (10) `"pdfexport.picker.last90" = "直近90日";`, (11) `"pdfexport.picker.custom" = "カスタム";`, (12) `"pdfexport.picker.generate" = "PDFを作成して共有";`, (13) `"pdfexport.picker.rangeFormat" = "%@ 〜 %@（%d日間）";`, (14) `"pdfexport.validation.toFuture" = "終了日は今日以前を指定してください";`, (15) `"pdfexport.validation.fromAfterTo" = "開始日は終了日以前を指定してください";`, (16) `"pdfexport.validation.rangeExceeded" = "期間は90日以内で指定してください";`, (17) `"pdfexport.pdf.title" = "服用履歴レポート";`, (18) `"pdfexport.pdf.patient" = "対象者：%@";`, (19) `"pdfexport.pdf.period" = "期間：%@〜%@";`, (20) `"pdfexport.pdf.generated" = "作成日時：%@";`, (21) `"pdfexport.pdf.summary" = "集計";`, (22) `"pdfexport.pdf.scheduled" = "定時";`, (23) `"pdfexport.pdf.prn" = "頓服";`, (24) `"pdfexport.pdf.adherence" = "服用率";`, (25) `"pdfexport.pdf.noRecords" = "記録なし";`, (26) slot labels: `"pdfexport.pdf.slot.morning"`, `"pdfexport.pdf.slot.noon"`, `"pdfexport.pdf.slot.evening"`, `"pdfexport.pdf.slot.bedtime"` = 朝/昼/夜/眠前, (27) status labels: `"pdfexport.pdf.status.taken"`, `"pdfexport.pdf.status.missed"`, `"pdfexport.pdf.status.pending"` = 服用済/未服用/未記録, (28) recorder labels: `"pdfexport.pdf.recorder.patient"`, `"pdfexport.pdf.recorder.caregiver"` = 患者/家族
  - **Test**: Build succeeds; strings referenced by PDFExportLockView, PeriodPickerSheet, PDFGenerator

### Audit & Verification

- [x] T023 [US3] Verify patient mode has zero export UI elements — audit PDFExportButton guard and history views
  - **Why**: Patient mode must never show export entry points (FR-002, SC-005). Confirm PDFExportButton returns `EmptyView()` for patient mode and no export UI leaks into patient history screens.
  - **Files**: `ios/MedicationApp/Features/PDFExport/PDFExportButton.swift`, `ios/MedicationApp/Features/History/CaregiverHistoryView.swift`
  - **Done**: (1) PDFExportButton in patient mode (`sessionStore.mode != .caregiver`) returns `EmptyView()`, (2) Patient-mode history views (if separate from caregiver) do not reference PDFExportButton, (3) UI test T008 passes confirming no export elements in patient mode
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T024 [US1] Verify "更新中" overlay blocks interaction during report fetch and PDF generation
  - **Why**: Full-screen overlay must block all taps during async operations (FR-014, NFR-002)
  - **Files**: `ios/MedicationApp/Features/PDFExport/PeriodPickerSheet.swift`, `ios/MedicationApp/Features/PDFExport/PDFExportButton.swift`
  - **Done**: (1) During `isGenerating == true`, SchedulingRefreshOverlay or equivalent is displayed, (2) Overlay covers entire screen using FullScreenContainer pattern, (3) User interaction is blocked until generation completes or fails, (4) On failure, overlay is dismissed and error is presented
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T025 [US1] Verify all iOS tests pass (T004-T008) against implemented code
  - **Why**: Confirms PDF export UI, gating, and period logic satisfy test expectations (SC-001, SC-004, SC-005, SC-008)
  - **Files**: `ios/MedicationApp/Tests/PDFExport/`
  - **Done**: `xcodebuild test` exits 0 with all PDFExport tests passing: PeriodPresetTests, RangeValidationTests, PDFExportUITests (premium + free flows), PDFExportPatientTests
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

**Checkpoint**: Full iOS and backend implementations complete. All tests (T001-T008) pass. Premium caregivers can generate and share PDFs. Free caregivers see lock UI with paywall. Patient mode has zero export UI. Overlay blocks interaction during async operations.

---

## Phase 4: Docs / QA Readiness (US5)

**Purpose**: Verify and finalize design documentation against implemented behavior. Docs were pre-generated during planning; this phase confirms accuracy post-implementation.

- [x] T026 [P] [US5] Verify and finalize quickstart.md, contracts/openapi.yaml, and data-model.md against implementation in `specs/011-pdf-export/`
  - **Why**: Design documents must accurately describe the implemented report endpoint, error contract, and response model (NFR-004)
  - **Files**: `specs/011-pdf-export/quickstart.md`, `specs/011-pdf-export/contracts/openapi.yaml`, `specs/011-pdf-export/data-model.md`
  - **Done**: (1) quickstart.md sandbox test steps verified against implemented flows (premium export, free lock, patient no-UI), (2) openapi.yaml report endpoint schema matches implemented route (`GET /api/patients/{patientId}/history/report`), response shape matches `HistoryReportResponse`, error codes `INVALID_RANGE`/`NOT_AUTHORIZED`/`HISTORY_RETENTION_LIMIT` match implementation, (3) data-model.md `HistoryReportResponse` field descriptions match actual response, `InvalidRangeError` properties match implementation, (4) File locations table matches actual paths
  - **Test**: `cd api && npm test` (contract tests T003 validate response shape)

- [x] T027 [P] [US5] Run full test suite validation — both backend and iOS
  - **Why**: Final green-light confirmation that all behavioral contracts hold across both platforms (SC-008)
  - **Files**: `api/tests/`, `ios/MedicationApp/Tests/`
  - **Done**: (1) `cd api && npm test` exits 0 — all history-report + existing tests pass, (2) `xcodebuild test` exits 0 — all PDFExport + existing tests pass, (3) No regressions in 004 history tests, 007 PRN tests, 008 billing tests, 009 limit tests, 010 retention tests
  - **Test**: Both commands exit 0

**Checkpoint**: All documentation finalized and verified against implementation. Full test suites green.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Tests)**: No dependencies — start immediately
- **Phase 2 (Backend)**: Depends on T001 (fixture setup) being available; implementation makes T002-T003 pass
- **Phase 3 (iOS)**: Depends on Phase 2 completion (backend endpoint must exist for integration); implementation makes T004-T008 pass
- **Phase 4 (Docs)**: Depends on Phase 2 + Phase 3 completion (docs verified against implementation)

### User Story Dependencies

- **US1 (Premium export)**: Requires US4 backend; core flow across backend + iOS
- **US2 (Free lock/paywall)**: Independent of US4 backend (client-side gate only); shares PDFExportButton with US1
- **US3 (Patient no UI)**: Independent — verified by guard in PDFExportButton
- **US4 (Backend endpoint)**: Independent — can be built and tested without iOS
- **US5 (Docs)**: Depends on US1 + US4 being complete

### Within Each Phase

- Tasks marked [P] can run in parallel (different files, no shared state)
- Unmarked tasks depend on prior tasks in same phase completing

### Parallel Opportunities

```text
# Phase 1: All test files can be written in parallel
T001 | T002 | T003 | T004 | T005 | T006 | T007 | T008

# Phase 2: Error class and validator in parallel, then sequential service/route
(T009 | T010) -> T011 -> T012 -> T013

# Phase 3: DTO, ViewModel, PDFGenerator, LockView, and localization in parallel, then sequential wiring
(T014 | T016 | T017 | T019 | T022) -> T015 -> T018 -> T020 -> T021 -> T023 -> T024 -> T025

# Phase 4: All docs in parallel
T026 | T027
```

---

## Implementation Strategy

### MVP First (Phase 1 + Phase 2)

1. Write all tests (Phase 1) — establish behavioral contract
2. Implement backend report endpoint (Phase 2) — backend tests pass
3. **STOP and VALIDATE**: Run `cd api && npm test` — all green

### Full Feature (Phase 3 + Phase 4)

4. Implement iOS DTO, API client, ViewModel, PDF generator, views, wiring (Phase 3) — iOS tests pass
5. **STOP and VALIDATE**: Run full iOS test suite — all green
6. Finalize docs (Phase 4)
7. **FINAL VALIDATION**: Both test suites green, quickstart walkthrough passes

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 27 |
| Phase 1 (Tests) | 8 |
| Phase 2 (Backend) | 5 |
| Phase 3 (iOS) | 12 |
| Phase 4 (Docs) | 2 |
| Parallel opportunities | 8 (Phase 1) + 2 (Phase 2) + 5 (Phase 3) + 2 (Phase 4) |
| US1 tasks | 14 |
| US2 tasks | 2 |
| US3 tasks | 2 |
| US4 tasks | 8 |
| US5 tasks | 2 |
| Non-goals excluded | 90-day+ split PDF, server-side PDF, patient billing UI, CSV/Excel export, scheduled auto-export, custom PDF styling |

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Tests MUST fail before implementation and pass after
- Commit after each task or logical group
- Stop at any checkpoint to validate independently
- Backend reuses: `scheduleService.getScheduleWithStatus`, `scheduleResponse.resolveSlot` + `groupDosesByLocalDate`, `prnDoseRecordService.listPrnHistoryItemsByRange`, `historyRetentionService.getTodayTokyo`
- iOS reuses: `FeatureGate.pdfExport` (from 008), `PaywallView`, `EntitlementStore`, `SchedulingRefreshOverlay` / `FullScreenContainer`
- All dates computed in Asia/Tokyo timezone (server and client)
- Presets recomputed at submission time, not picker open time (midnight boundary edge case)
- PDF generated on-device via `UIGraphicsPDFRenderer` — no server-side PDF rendering
- MAX_REPORT_RANGE_DAYS = 90 (inclusive, named constant for future adjustment)
- Share sheet via `UIActivityViewController` representable (not `ShareLink` — PDF URL is async)
