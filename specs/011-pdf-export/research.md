# Research: PDF Export of Medication History (011)

## Research Summary

Feature 011 builds on the billing foundation (008), the history views (004), PRN medications (007), and optionally the history retention limit (010). All decisions below were resolved through codebase inspection — no external research or prototyping was required.

## Decision 1: PDF Rendering Approach

**Decision**: Use `UIGraphicsPDFRenderer` (built-in iOS framework) with standard A4 page size (595 × 842 points). Render text with `NSAttributedString.draw(in:)` for styled labels and values. No third-party dependency.

**Rationale**: The PDF content is structured text and simple tables (medication names, dosages, statuses, timestamps). `UIGraphicsPDFRenderer` provides direct control over page layout and text placement, which is sufficient for this use case. It is available on all supported iOS versions and requires no additional dependencies, keeping the app binary small and avoiding supply-chain risk.

**Alternatives considered**:
- PDFKit (Apple framework): Primarily designed for viewing and annotating existing PDFs, not generating new ones. Requires creating `PDFPage` objects from images or existing documents — more complex for text-heavy content.
- Third-party libraries (e.g., TPPDF, SwiftyPDF): Add external dependency for functionality achievable with built-in APIs. Rejected per constitution principle (minimize external dependencies for functionality available natively).
- WebKit-based rendering (load HTML → print to PDF): Requires a `WKWebView` instance, involves async loading, and is harder to control page breaks. Over-engineered for structured tabular data.

## Decision 2: Report API Design

**Decision**: Single new endpoint `GET /api/patients/{patientId}/history/report?from=YYYY-MM-DD&to=YYYY-MM-DD` under the existing caregiver history path. The endpoint reuses existing services:
- `scheduleService.getScheduleWithStatus(patientId, from, to, "Asia/Tokyo")` for scheduled dose data
- `scheduleResponse.groupDosesByLocalDate()` + `resolveSlot()` for slot grouping
- `prnDoseRecordService.listPrnHistoryItemsByRange()` for PRN data

A new `reportService.ts` orchestrates these into the spec response shape.

**Rationale**: The existing schedule and PRN services already handle the heavy lifting (medication snapshots, dose status resolution, timezone-aware grouping). The report endpoint essentially performs the same queries as the month endpoint but for a wider date range (up to 90 days) and returns per-medication detail rather than per-slot summary status. Reusing these services avoids duplicating query logic and ensures data consistency with the history views.

**Alternatives considered**:
- Separate data endpoint per day (client makes N requests): Rejected — the spec requires a single request for the full range to minimize latency and simplify the client flow.
- Return raw dose records and let the client group: Rejected — increases client complexity and data transfer; the server already has grouping logic.
- Use the existing month endpoint in a loop (client calls month endpoint for each month in range): Rejected — the month endpoint returns summary status per slot (not per-medication detail), which is insufficient for PDF content.

## Decision 3: Date Range Iteration

**Decision**: The server iterates from `from` to `to` (inclusive) using Asia/Tokyo date keys. For each date, it collects the scheduled doses (already grouped by `groupDosesByLocalDate`) and assigns each dose to a slot via `resolveSlot()`. PRN items are already grouped by date key from `listPrnHistoryItemsByRange()`.

**Rationale**: This is the same approach used in the month endpoint's day-by-day loop (`api/app/api/patients/[patientId]/history/month/route.ts`), proven to handle timezone-aware date iteration correctly. The max range of 90 days keeps the iteration bounded.

**Alternatives considered**:
- SQL-level date grouping: Possible but complicates the query and diverges from the established pattern. The existing services handle grouping in application code.
- Parallel per-day queries: Over-engineered — the existing `getScheduleWithStatus` fetches the entire range in one query.

## Decision 4: Validation Error Pattern

**Decision**: Create `InvalidRangeError` extending `Error` with `statusCode: 400`, `code: "INVALID_RANGE"`. The route handler catches this error before `errorResponse()` and returns:

```json
{
  "code": "INVALID_RANGE",
  "message": "指定された期間が不正です。"
}
```

**Rationale**: Follows the exact same pattern as `HistoryRetentionError` in `api/src/errors/historyRetentionError.ts`. Custom error classes with `statusCode` are caught in the route's try/catch before the generic `errorResponse()` mapper, allowing structured error responses with stable machine-readable codes.

**Alternatives considered**:
- Return 422 Unprocessable Entity: The existing codebase uses 422 for schema validation (`validateYearMonth`, `validateDateString`) but those return `{ error: "validation", messages: [...] }`. The report range validation is a domain-level check (business rule) rather than schema validation, so 400 with a stable code is more appropriate and consistent with the spec.
- Use the generic error mapper: Rejected — the generic mapper produces `{ error: "bad_request", message: "..." }` which lacks a stable code field. The client needs `"INVALID_RANGE"` to distinguish range errors from other 400 responses.

## Decision 5: PDF Sharing Mechanism

**Decision**: Use `UIActivityViewController` via a SwiftUI representable wrapper. The PDF is generated to a temporary file URL, then the URL is passed to the activity view controller for sharing.

**Rationale**: The existing `ShareLink` usage in `PatientLinkCodeView.swift` handles static strings — it accepts `item:` at view construction time. For PDF export, the file does not exist until the async generation completes, so the share sheet must be presented programmatically after generation. `UIActivityViewController` with a file URL is the standard iOS pattern for sharing dynamically generated documents. The representable wrapper is presented via `.sheet(isPresented:)` which is the same presentation pattern used throughout the app.

**Alternatives considered**:
- `ShareLink(item: url)` with a pre-generated URL: Rejected — `ShareLink` expects the item to be available at view construction time. The PDF is generated asynchronously, so the URL is not available until the generate step completes.
- `fileExporter` modifier: Designed for saving to Files app, not for general sharing (AirDrop, Mail, Messages, etc.).
- Direct `UIDocumentInteractionController`: More limited than `UIActivityViewController` — only supports single-app handoff, not the full share sheet.

## Decision 6: Period Picker Presets

**Decision**: Implement presets as pure functions computing `(from: Date, to: Date)` tuples using `Calendar` with `TimeZone(identifier: "Asia/Tokyo")`, matching `AppConstants.defaultTimeZone`. Presets are recomputed at submission time (when the user taps "PDFを作成して共有"), not at picker open time.

**Rationale**: Computing presets at submission time handles the midnight boundary edge case: if the user opens the picker at 23:59 JST and taps generate at 00:01 JST the next day, the presets reflect the new "today". Using pure functions makes the logic unit-testable without mocking system time (inject `today` as a parameter). The `Calendar` + `TimeZone` approach is the standard Swift API for timezone-aware date arithmetic.

**Alternatives considered**:
- Compute presets at picker open time and cache: Rejected — introduces the midnight boundary bug where presets become stale.
- Use `DateComponents` offset from UTC: Rejected — `Calendar` with a `TimeZone` is the idiomatic Swift approach and is already used in other date calculations in the app (e.g., notification scheduling).

## Decision 7: Retention Alignment (Defense-in-Depth)

**Decision**: The report endpoint calls `checkRetentionForDay(from, "caregiver", caregiverId)` on the earliest requested date. Since PDF export requires premium (gated by `FeatureGate.pdfExport`) and the retention limit (010) only blocks free users, this check is effectively a no-op for the expected flow. However, it provides defense-in-depth if gate logic changes in the future.

**Rationale**: The spec (FR-017) states the retention check SHOULD (not MUST) be applied. Including it costs one additional database query (entitlement lookup) which is negligible. If a future product change allows free users to export PDFs for a limited range, the retention check would automatically enforce the 30-day limit server-side.

**Alternatives considered**:
- Skip the retention check entirely: Valid for MVP since premium users bypass retention. Rejected because it creates a latent security gap if the premium requirement is relaxed.
- Check retention for every day in the range: Over-engineered — checking only the `from` date is sufficient because if `from >= cutoffDate`, then all dates in the range are within the allowed window.

## Decision 8: Patient DisplayName Resolution

**Decision**: Fetch the patient's `displayName` from the existing `Patient` record. The `assertCaregiverPatientScope` function already queries the patient record — extend this to return the patient data so the display name is available without an extra query.

**Rationale**: The scope assertion in `api/src/middleware/auth.ts` calls `getPatientRecordForCaregiver(patientId, caregiverUserId)` which returns the full patient record including `displayName`. Instead of discarding this and querying again, the report service receives the patient data from the scope assertion step.

**Alternatives considered**:
- Separate query for patient displayName: Works but wasteful — the data is already available from the scope check.
- Include displayName in the session token: Not available — the session token contains only `caregiverUserId`, not patient details.

## Decision 9: Lock UI Pattern

**Decision**: Create `PDFExportLockView` following the exact same pattern as `HistoryRetentionLockView` — a full-screen overlay with title, body, and buttons ("アップグレード" / "購入を復元" / "閉じる"). The "アップグレード" button presents `PaywallView` via `.sheet(isPresented:)`.

**Rationale**: The spec requires the same three-button pattern for the PDF export lock as for the history retention lock (FR-016). Reusing the pattern ensures visual consistency and leverages the proven PaywallView integration. Accessibility identifiers follow the same naming convention for UI test discovery.

**Alternatives considered**:
- Reuse `HistoryRetentionLockView` with a mode parameter: Rejected — the two views have different titles, body text, and contexts. A dedicated view is cleaner and avoids overloading the retention lock with unrelated feature gating.
- Show an alert instead of a full-screen overlay: Rejected — the spec requires a dedicated lock UI with upgrade/restore actions, not a simple alert dialog.
