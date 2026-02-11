# Feature Specification: PDF Export of Medication History

**Feature Branch**: `011-pdf-export`  
**Created**: 2026-02-11  
**Status**: Draft  
**Input**: User description: "家族モードで、選択した期間の服用履歴をPDFに出力し、共有できるようにする。Premium限定（無料はロック→Paywall誘導）。患者モードにはPDF出力UIを出さない。"  
**Dependencies**: 008-billing-foundation, 004-history-schedule-view, 007-prn-medications

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Premium Caregiver Exports History PDF (Priority: P1)

As a premium caregiver, I want to select a date range and export the patient's medication history as a PDF so that I can share it with a doctor, pharmacy, or other family members.

**Why this priority**: This is the core value proposition of the feature. Without the ability to generate and share a PDF, the feature delivers no user value. All other stories depend on this flow working.

**Independent Test**: Sign in as a premium caregiver with an active patient who has medication history. Tap the "PDF出力" button in the history tab, select a date range preset (e.g., "直近30日"), tap "PDFを作成して共有", wait for the overlay to complete, and verify the ShareSheet opens with a PDF file.

**Acceptance Scenarios**:

1. **Given** a premium caregiver viewing the history tab, **When** the caregiver taps "PDF出力", **Then** a period selection sheet is presented.
2. **Given** the period picker is open, **When** the caregiver selects a preset (e.g., "今月"), **Then** the date range and day count are displayed (YYYY/MM/DD〜YYYY/MM/DD, N日間) and the "PDFを作成して共有" button is enabled.
3. **Given** the period picker is open, **When** the caregiver selects "カスタム" and enters a valid custom range (from <= to, to <= todayTokyo, range <= 90 days), **Then** the range and day count are displayed and the button is enabled.
4. **Given** a valid date range is selected, **When** the caregiver taps "PDFを作成して共有", **Then** a full-screen "更新中" overlay blocks all interaction while the system fetches data and generates the PDF.
5. **Given** data fetch and PDF generation complete successfully, **Then** the overlay is dismissed and the iOS ShareSheet is presented with the generated PDF file.
6. **Given** the ShareSheet is presented, **When** the caregiver dismisses or completes sharing, **Then** the caregiver returns to the period picker sheet.

---

### User Story 2 — PDF Contains Slots and PRN Records (Priority: P2)

As a caregiver, I want the exported PDF to contain both scheduled (slot-based) and as-needed (PRN) medication records so that the document is a complete picture of the patient's medication history for the selected period.

**Why this priority**: The PDF must be comprehensive and accurate for it to be useful in medical or caregiving contexts. An incomplete PDF undermines trust and value.

**Independent Test**: Generate a PDF for a period that contains both scheduled slot doses and PRN doses. Open the PDF and verify the summary page shows aggregated counts and the detail pages show per-day slot and PRN breakdowns.

**Acceptance Scenarios**:

1. **Given** a generated PDF, **Then** the first page contains: the title "服用履歴レポート", the patient's display name, the selected period (YYYY/MM/DD〜YYYY/MM/DD), the generation timestamp in Asia/Tokyo, a summary of scheduled dose counts (TAKEN, MISSED, PENDING), a count of PRN records, and the adherence rate (TAKEN / (TAKEN + MISSED), excluding PENDING).
2. **Given** a generated PDF with multiple days of history, **Then** the detail pages contain a section for each day, with each day listing scheduled doses by slot (morning/noon/evening/bedtime) showing medication name, dosage text, dose count, status, and recorded time (if available).
3. **Given** a day with PRN records, **Then** the day's section includes a PRN subsection showing medication name, dosage text, quantity, recorded time, and recorder (患者 or 家族).
4. **Given** a day with no records (neither scheduled nor PRN), **Then** the day is either omitted from the PDF or shown with an indication of "記録なし".

---

### User Story 3 — Free Caregiver Lock and Paywall Redirect (Priority: P2)

As a free caregiver, when I tap the PDF export button, I see a lock screen that explains the feature requires premium and offers me a direct path to upgrade.

**Why this priority**: The paywall conversion funnel is essential for monetisation. Without it, free users would see the feature but have no path to unlock it. Ranked equal to US2 because the paywall gate is needed alongside the PDF content.

**Independent Test**: Sign in as a free caregiver. Tap "PDF出力" in the history tab. Verify the lock UI appears with "アップグレード", "購入を復元", and "閉じる" buttons. Tap "アップグレード" and verify the paywall sheet opens.

**Acceptance Scenarios**:

1. **Given** a free caregiver viewing the history tab, **When** the caregiver taps "PDF出力", **Then** a lock UI is displayed (instead of the period picker) explaining that PDF export requires premium.
2. **Given** the lock UI is displayed, **When** the caregiver taps "アップグレード", **Then** the paywall sheet is presented.
3. **Given** the lock UI is displayed, **When** the caregiver taps "購入を復元", **Then** the purchase restore flow is triggered.
4. **Given** the lock UI is displayed, **When** the caregiver taps "閉じる", **Then** the lock UI is dismissed and the caregiver returns to the history tab.

---

### User Story 4 — Patient Mode Has No Export UI (Priority: P3)

As a patient, I should not see any PDF export button or menu item, because purchasing and premium features are managed exclusively by the caregiver.

**Why this priority**: Ensuring zero PDF export surface in patient mode is a compliance requirement (no billing UI in patient mode per 008) but is lower priority than the active flows because it is a negative test (absence of UI).

**Independent Test**: Sign in as a patient. Navigate to the history tab. Verify that no "PDF出力" button, menu item, or export-related UI element is visible anywhere in the history screens.

**Acceptance Scenarios**:

1. **Given** a patient viewing the history tab, **Then** no "PDF出力" button or export menu item is rendered.
2. **Given** a patient navigating through any history screen (month view, day detail), **Then** no export-related UI element is present.

---

### Edge Cases

- **Midnight boundary in Asia/Tokyo**: When the user opens the period picker close to midnight JST, the presets must be computed based on the date at the time of submission (tap "PDFを作成して共有"), not at the time the picker was opened. If todayTokyo rolls over, presets should reflect the new date.
- **Empty data range**: If the selected range contains no medication records at all (no slots, no PRN), the PDF should still be generated with the summary page showing zero counts and the detail section indicating no records.
- **Network failure during report fetch**: If the API call fails for non-retention reasons (network error, server error), the "更新中" overlay is dismissed and the existing error handling (retry prompt) applies. The lock UI is NOT shown.
- **ShareSheet cancelled**: If the user dismisses the ShareSheet without sharing, no error is shown. The user returns to the period picker.
- **Large dataset (90 days, dense)**: PDF generation for a full 90-day range with many medications and PRN records must complete within a reasonable time. The "更新中" overlay provides feedback during generation.
- **Premium expires between picker open and generate tap**: When the user taps "PDFを作成して共有", the system should re-verify entitlement state before proceeding. If premium has expired, show the lock UI instead of generating.
- **Entitlement state unknown at tap time**: If the entitlement state has not yet been determined when the user taps "PDF出力", the app shows the "更新中" overlay, refreshes entitlements, then proceeds with the appropriate gate decision (picker or lock).
- **Alignment with 010 history retention**: If the report API is called by a free caregiver whose requested `from` date falls before the 30-day retention cutoff, the server returns `HISTORY_RETENTION_LIMIT` with `cutoffDate`. The client should display an appropriate message (not the PDF lock UI).
- **Invalid range entry in custom picker**: If the user enters a `from` after `to`, or a range exceeding 90 days, or a `to` in the future, the validation error text is shown inline and the generate button is disabled.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST restrict PDF export functionality to premium caregivers. The existing `FeatureGate.pdfExport` gate (requiring premium tier) MUST be used as the client-side check.
- **FR-002**: PDF export UI (button, menu item) MUST NOT be rendered in patient mode. The UI surface is zero — not hidden, not disabled, but completely absent.
- **FR-003**: The period picker MUST offer five selection options: "今月" (1st of current month to today), "先月" (1st to last day of previous month), "直近30日" (today minus 29 days to today, inclusive), "直近90日" (today minus 89 days to today, inclusive), and "カスタム" (user-defined From/To dates).
- **FR-004**: All date computations for period presets and validation MUST use the Asia/Tokyo timezone. "Today" means the current date in Asia/Tokyo.
- **FR-005**: Period validation MUST enforce: (a) `to` <= todayTokyo, (b) `from` <= `to`, (c) `(to - from + 1)` <= 90 days (inclusive count). Invalid ranges MUST disable the generate button and show an inline error message.
- **FR-006**: The period picker MUST display the selected range in the format "YYYY/MM/DD〜YYYY/MM/DD" along with the day count (e.g., "30日間").
- **FR-007**: A new backend endpoint MUST be created to return all history data for a given date range in a single request, scoped to a specific patient.
- **FR-008**: The report endpoint MUST require caregiver session authentication. Patient session requests MUST be rejected.
- **FR-009**: The report endpoint MUST validate query parameters: `from` and `to` are required, `to` <= todayTokyo, `from` <= `to`, range <= 90 days (inclusive). Validation failures MUST return HTTP 400 with error code `INVALID_RANGE`.
- **FR-010**: The report response MUST include: patient identity (id, display name), range metadata (from, to, timezone, day count), and a per-day array containing slot-based doses (grouped by morning/noon/evening/bedtime) and PRN records.
- **FR-011**: PDF generation MUST happen entirely on-device. No server-side PDF rendering is involved.
- **FR-012**: The PDF summary page (page 1) MUST contain: the title "服用履歴レポート", patient display name, selected period, generation timestamp (Asia/Tokyo), aggregated scheduled dose counts (TAKEN, MISSED, PENDING), PRN record count, and adherence rate calculated as TAKEN / (TAKEN + MISSED) with PENDING excluded.
- **FR-013**: The PDF detail pages (page 2 onwards) MUST contain day-by-day sections. Each day section lists scheduled doses grouped by slot (morning/noon/evening/bedtime) with medication name, dosage text, dose count, status, and recorded time (if available). Each day section with PRN records includes a PRN subsection with medication name, dosage text, quantity, recorded time, and recorder identity (patient or caregiver).
- **FR-014**: A full-screen "更新中" overlay MUST block all user interaction during data fetching from the report endpoint and during PDF generation on-device.
- **FR-015**: After successful PDF generation, the system MUST present the iOS share sheet with the generated PDF file.
- **FR-016**: When a free caregiver taps the PDF export button, a lock UI MUST be displayed with three actions: "アップグレード" (opens paywall), "購入を復元" (triggers restore flow), and "閉じる" (dismisses the lock UI).
- **FR-017**: (Alignment with 010) If the report endpoint receives a request where the `from` date falls before the free-tier retention cutoff, the server SHOULD return HTTP 403 with error code `HISTORY_RETENTION_LIMIT` and include the `cutoffDate` in the response body.

### Non-Functional Requirements *(mandatory)*

- **NFR-001 (Security)**: The report endpoint MUST enforce server-side authentication and authorisation. A caregiver session MUST only access patients linked to that caregiver. Patient sessions MUST be rejected entirely. The client-side feature gate is a UX convenience only.
- **NFR-002 (UX/Responsiveness)**: All asynchronous operations (data fetching and PDF generation) MUST display the full-screen "更新中" overlay to block interaction and provide visual feedback. The overlay follows the global UX pattern established in the application.
- **NFR-003 (Timezone Consistency)**: Every date computation — client-side preset calculation, server-side validation, PDF display timestamps — MUST use Asia/Tokyo. No UTC-based dates should appear in user-facing content.
- **NFR-004 (Documentation)**: quickstart.md MUST document the PDF export flow for premium caregivers, the period options, the 90-day limit, and sandbox testing steps. The OpenAPI contract MUST include the report endpoint and its error responses. data-model.md MUST document the report response model if applicable.

### Key Entities *(no new database entities)*

- **DoseRecord** (existing from 003): Individual scheduled dose records with status (TAKEN, MISSED, PENDING) per medication per slot per day. Used to populate the slot sections of the PDF.
- **PrnDoseRecord** (existing from 007): Individual PRN (as-needed) dose records with quantity, timestamp, and recorder. Used to populate the PRN sections of the PDF.
- **CaregiverEntitlement** (existing from 008): Stores purchase/entitlement records per caregiver. Used to determine premium status for the feature gate.
- **HistoryReportResponse** (new, API-only): The response model returned by the report endpoint. Contains patient info, range metadata, and the per-day history array. This is a transport model, not a persisted entity.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Premium caregivers can select a date range, generate a PDF, and reach the share sheet in under 30 seconds end-to-end (for up to 90 days of data).
- **SC-002**: The generated PDF accurately reflects 100% of scheduled slot records and PRN records for the selected date range.
- **SC-003**: The adherence rate on the PDF summary page matches the formula TAKEN / (TAKEN + MISSED) with PENDING excluded, verified against raw data.
- **SC-004**: Free caregivers tapping the export button are shown the lock UI with a paywall path 100% of the time — no PDF is ever generated for free users.
- **SC-005**: Patient-mode history screens contain zero PDF export UI elements across all tested screen states.
- **SC-006**: The report API rejects 100% of invalid range requests (range > 90 days, to > todayTokyo, from > to, missing params) with the stable `INVALID_RANGE` error code.
- **SC-007**: The report API rejects 100% of unauthorised requests (patient sessions, unauthenticated requests).
- **SC-008**: All required backend integration tests and iOS unit/UI smoke tests pass.

## Definitions

- **todayTokyo**: The current date in the Asia/Tokyo timezone.
- **Adherence Rate**: The percentage of scheduled doses that were taken, calculated as `TAKEN / (TAKEN + MISSED) * 100`. Doses with PENDING status are excluded from the calculation. If `(TAKEN + MISSED) = 0`, the rate is displayed as "—" (not applicable).
- **Slot**: One of four time-based medication periods in a day: morning (朝), noon (昼), evening (夜), bedtime (眠前).
- **PRN**: Pro re nata (as-needed) medication. Recorded separately from scheduled slot doses with quantity, timestamp, and recorder identity.
- **Recorder**: The identity of who recorded a PRN dose — either "PATIENT" (患者) or "CAREGIVER" (家族).

## Assumptions

- The billing foundation (feature 008) is fully implemented and deployed: EntitlementStore, FeatureGate (including `.pdfExport`), PaywallView, the "更新中" overlay pattern, and the backend `caregiver_entitlements` table and endpoints are all operational.
- The history/schedule view (feature 004) is fully implemented: the history tab UI, calendar month view, and day detail view are available for caregivers.
- PRN medications (feature 007) are fully implemented: PRN dose records exist in the database and can be queried by date range.
- The existing `FeatureGate.pdfExport` case (defined in 008) requires `.premium` tier for caregivers.
- The 1:1 patient-to-caregiver link constraint (feature 002) is in effect.
- The "更新中" full-screen overlay is a shared, reusable component already available from the global UX pattern.
- Performance of on-device PDF generation for up to 90 days of data is acceptable on supported iOS devices (specific devices TBD during planning).
- The maximum of 90 days is a product decision for MVP. The value should be implemented as a named constant for future adjustment.

## Non-Goals (Explicit)

- Adding any billing UI or purchase flow to patient mode.
- Supporting date ranges exceeding 90 days or splitting PDFs across multiple documents.
- Server-side PDF generation — all PDF rendering happens on-device.
- Customising PDF appearance (fonts, colours, branding) beyond the MVP layout.
- Exporting to formats other than PDF (CSV, Excel, etc.).
- Scheduling or automating PDF generation (e.g., monthly auto-export).

## API Error Contract

### Report Endpoint: `GET /api/patients/{patientId}/history/report?from=YYYY-MM-DD&to=YYYY-MM-DD`

**Authentication**: Caregiver session required.

**Success Response** (HTTP 200):

```json
{
  "patient": {
    "id": "uuid",
    "displayName": "太郎"
  },
  "range": {
    "from": "2026-01-01",
    "to": "2026-01-30",
    "timezone": "Asia/Tokyo",
    "days": 30
  },
  "days": [
    {
      "date": "2026-01-01",
      "slots": {
        "morning": [
          {
            "medicationId": "uuid",
            "name": "アムロジピン",
            "dosageText": "5mg",
            "doseCount": 1,
            "status": "TAKEN",
            "recordedAt": "2026-01-01T08:15:00+09:00"
          }
        ],
        "noon": [],
        "evening": [],
        "bedtime": []
      },
      "prn": [
        {
          "medicationId": "uuid",
          "name": "ロキソプロフェン",
          "dosageText": "60mg",
          "quantity": 1,
          "recordedAt": "2026-01-01T14:30:00+09:00",
          "recordedBy": "PATIENT"
        }
      ]
    }
  ]
}
```

### Error Responses

**Validation Failure** (HTTP 400):

```json
{
  "code": "INVALID_RANGE",
  "message": "指定された期間が不正です。"
}
```

Triggered when: `from` or `to` is missing, `to` > todayTokyo, `from` > `to`, or `(to - from + 1)` > 90 days.

**Unauthorised / Not Authorised** (HTTP 403 / 409):

```json
{
  "code": "NOT_AUTHORIZED",
  "message": "..."
}
```

Triggered when: patient session attempts to access the endpoint, or caregiver attempts to access a patient not linked to them. Uses the existing authorisation policy.

**History Retention Limit** (HTTP 403, optional alignment with 010):

```json
{
  "code": "HISTORY_RETENTION_LIMIT",
  "message": "履歴の閲覧は直近30日間に制限されています。",
  "cutoffDate": "2026-01-12",
  "retentionDays": 30
}
```

Triggered when: a free caregiver's requested `from` date falls before the 30-day retention cutoff. The `code` field is the same stable identifier used by the history month/day endpoints.

## UX Copy (Japanese)

### History Tab — Export Button

- **Button label**: PDF出力

### Lock UI — Free Caregiver

- **Title**: プレミアムでPDF出力
- **Body**: 服用履歴のPDF出力はプレミアムでご利用いただけます
- **Primary button**: アップグレード
- **Secondary button**: 購入を復元
- **Dismiss button**: 閉じる

### Period Picker Sheet

- **Preset options**:
  - 今月（{month}月1日〜今日）
  - 先月（{prevMonth}月1日〜{prevMonth}月{lastDay}日）
  - 直近30日
  - 直近90日
  - カスタム
- **Range display**: {from} 〜 {to}（{N}日間）
- **Validation errors**:
  - 終了日は今日以前を指定してください (to > todayTokyo)
  - 開始日は終了日以前を指定してください (from > to)
  - 期間は90日以内で指定してください (range > 90 days)
- **Generate button**: PDFを作成して共有
- **Generate button (disabled state)**: PDFを作成して共有 (greyed out)

### PDF Content

- **Title**: 服用履歴レポート
- **Patient label**: 対象者：{displayName}
- **Period label**: 期間：{from}〜{to}
- **Generated label**: 作成日時：{timestamp}
- **Summary section title**: 集計
- **Scheduled label**: 定時
- **PRN label**: 頓服
- **Adherence label**: 服用率
- **Slot labels**: 朝 / 昼 / 夜 / 眠前
- **Status labels**: 服用済 / 未服用 / 未記録
- **Recorder labels**: 患者 / 家族

## Testing Requirements

### Backend Integration Tests

- **Valid range**: Request with a valid from/to within 90 days → 200 with expected response structure containing patient info, range metadata, and days array.
- **Invalid range — missing params**: Request without `from` or `to` → 400 `INVALID_RANGE`.
- **Invalid range — to > todayTokyo**: Request with a future `to` date → 400 `INVALID_RANGE`.
- **Invalid range — from > to**: Request with from after to → 400 `INVALID_RANGE`.
- **Invalid range — exceeds 90 days**: Request with (to - from + 1) > 90 → 400 `INVALID_RANGE`.
- **Unauthorised — patient session**: Patient session calls report endpoint → 403 `NOT_AUTHORIZED`.
- **Unauthorised — wrong caregiver**: Caregiver accesses a patient not linked to them → 403/404 per existing policy.
- **Retention limit (if 010 applies)**: Free caregiver with `from` before cutoff → 403 `HISTORY_RETENTION_LIMIT`.
- **Premium caregiver — full range**: Premium caregiver requests 90 days → 200 with data.

### iOS Unit Tests

- **Period preset calculation (Tokyo)**: Verify "今月", "先月", "直近30日", "直近90日" presets compute correct from/to dates in Asia/Tokyo, including edge cases around month boundaries and year boundaries.
- **Range validation**: Verify the validation logic correctly rejects ranges > 90 days, to > todayTokyo, from > to, and accepts valid ranges including boundary cases (exactly 90 days, to = todayTokyo).
- **Adherence rate calculation**: Verify the formula TAKEN / (TAKEN + MISSED) with PENDING excluded. Verify edge case where TAKEN + MISSED = 0 returns the "not applicable" indicator.
- **Feature gate check**: Verify `FeatureGate.pdfExport` returns unlocked for premium and locked for free.

### iOS UI Smoke Tests

- **Caregiver Premium — full flow**: Tap "PDF出力" → period picker shown → select preset → tap generate → overlay shown → share sheet presented.
- **Caregiver Free — lock flow**: Tap "PDF出力" → lock UI shown → "アップグレード" tapped → paywall presented.
- **Patient mode — no UI**: Navigate through all history screens → no "PDF出力" button or export menu item visible.
- **Overlay interaction block**: During data fetch and PDF generation, verify the "更新中" overlay is visible and user interaction is blocked.

## Documentation Updates

- **quickstart.md**: Document how to export a PDF as a premium caregiver, explain the five period options and the 90-day limit, and provide sandbox testing steps (purchase premium in sandbox → generate PDF → verify share sheet).
- **contracts/openapi.yaml**: Add the `GET /api/patients/{patientId}/history/report` endpoint definition with query parameters, success response schema, and error responses (`INVALID_RANGE`, `NOT_AUTHORIZED`, `HISTORY_RETENTION_LIMIT`).
- **data-model.md**: Document the `HistoryReportResponse` model structure (patient, range, days array with slots and PRN) if the project maintains a response model reference.
