# Feature Specification: History Retention Limit

**Feature Branch**: `010-history-retention`  
**Created**: 2026-02-10  
**Status**: Draft  
**Input**: User description: "Introduce history retention limits: free users see 30 days of history, premium users see all. Server-enforced with stable error code. Applied to both patient and caregiver modes."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Free User 30-Day History Limit (Priority: P1)

As a free user (in either patient mode or caregiver mode), I can view my medication history for the most recent 30 days. History older than 30 days is not accessible until the linked caregiver upgrades to premium.

**Why this priority**: This is the core monetisation gate for this feature. Without the 30-day restriction there is no distinction between free and premium history access, and no incentive to upgrade.

**Independent Test**: Sign in as a free caregiver (or patient linked to a free caregiver). Open the history tab. Verify that dates within the last 30 days are viewable and that navigating to a month or day before the cutoff date results in a lock screen.

**Acceptance Scenarios**:

1. **Given** a free caregiver viewing the history tab, **When** the caregiver selects a day within the last 30 days, **Then** the day detail loads successfully with dose data.
2. **Given** a free caregiver viewing the history tab, **When** the caregiver selects a day older than 30 days, **Then** the server returns `HISTORY_RETENTION_LIMIT` and the app shows a lock overlay.
3. **Given** a free patient viewing the history tab, **When** the patient selects a day within the last 30 days, **Then** the day detail loads successfully.
4. **Given** a free patient viewing the history tab, **When** the patient selects a day older than 30 days, **Then** the server returns `HISTORY_RETENTION_LIMIT` and the app shows a lock overlay without any purchase UI.
5. **Given** a free user viewing the history tab, **When** the history tab loads, **Then** a banner reads "無料：直近30日まで（{cutoffDate}〜今日）".

---

### User Story 2 — Backend Enforcement (Priority: P1)

As the system, I enforce the 30-day history retention limit on the server so that the restriction cannot be bypassed by a modified or outdated client.

**Why this priority**: Server-side enforcement is a security requirement that must ship alongside the client gate to prevent abuse. Without it, a modified client could retrieve all history regardless of entitlement status.

**Independent Test**: Using a direct HTTP client (bypassing the iOS app), send history month and day requests as a free user for dates before cutoffDate. Verify the server returns HTTP 403 with the `HISTORY_RETENTION_LIMIT` error code. Repeat as a premium user and verify success.

**Acceptance Scenarios**:

1. **Given** a free caregiver, **When** the server receives a history month request for a month entirely before cutoffDate, **Then** the server returns HTTP 403 with `{ code: "HISTORY_RETENTION_LIMIT", cutoffDate: "YYYY-MM-DD", retentionDays: 30 }`.
2. **Given** a free caregiver, **When** the server receives a history day request for a date before cutoffDate, **Then** the server returns the same retention error.
3. **Given** a free caregiver, **When** the server receives a history month request for a month that straddles cutoffDate (cutoffDate falls within the month), **Then** the server returns the retention error (MVP: entire straddling month is locked).
4. **Given** a premium caregiver, **When** the server receives a history request for any date range, **Then** the server returns the requested data successfully.
5. **Given** a free patient session whose linked caregiver is free, **When** the server receives a history request before cutoffDate, **Then** the server returns the retention error.
6. **Given** a free patient session whose linked caregiver is premium, **When** the server receives a history request before cutoffDate, **Then** the server returns the data successfully (premium inherited via link).

---

### User Story 3 — Lock UI with Paywall in Caregiver Mode (Priority: P2)

As a free caregiver, when I try to view history older than 30 days, I see a clear lock screen that explains the restriction and offers me a direct path to upgrade to premium.

**Why this priority**: The lock UI with paywall is the conversion funnel. It must be present for the monetisation gate to drive upgrades, but the backend enforcement (P1) can function independently.

**Independent Test**: Sign in as a free caregiver. Navigate to a month or day before cutoffDate. Verify the lock overlay appears with the correct messaging and that the "アップグレード" button navigates to the paywall.

**Acceptance Scenarios**:

1. **Given** a free caregiver viewing history, **When** the caregiver navigates to a month before cutoffDate, **Then** a lock overlay appears with the message "30日より前の履歴はプレミアムで閲覧できます".
2. **Given** the lock overlay is displayed in caregiver mode, **When** the caregiver taps "アップグレード", **Then** the paywall sheet is presented.
3. **Given** the lock overlay is displayed in caregiver mode, **When** the caregiver taps "購入を復元", **Then** the restore purchase flow is triggered.
4. **Given** the lock overlay is displayed in caregiver mode, **When** the caregiver taps "閉じる", **Then** the lock overlay is dismissed and the caregiver returns to the most recent viewable history.

---

### User Story 4 — Lock UI without Paywall in Patient Mode (Priority: P2)

As a patient, when I try to view history older than 30 days, I see a lock screen that explains the restriction but does NOT show any purchase buttons, since purchasing is only allowed in caregiver mode.

**Why this priority**: Patient mode must never expose billing UI. The lock screen informs the patient and suggests that their caregiver can upgrade to unlock full history.

**Independent Test**: Sign in as a patient linked to a free caregiver. Navigate to a month or day before cutoffDate. Verify the lock overlay appears without any purchase or upgrade buttons.

**Acceptance Scenarios**:

1. **Given** a free patient viewing history, **When** the patient navigates to a day before cutoffDate, **Then** a lock overlay appears with the message "30日より前の履歴はプレミアムで閲覧できます。家族がプレミアムの場合は自動で表示されます。".
2. **Given** the lock overlay is displayed in patient mode, **Then** there are NO "アップグレード" or "購入を復元" buttons visible.
3. **Given** the lock overlay is displayed in patient mode, **When** the patient taps "更新" (optional refresh button), **Then** the app re-fetches entitlement state and, if the caregiver has since upgraded, the history loads.

---

### User Story 5 — Premium Unlimited History (Priority: P3)

As a premium user (caregiver who has purchased premium, or patient linked to a premium caregiver), I can browse all available medication history without any date restriction.

**Why this priority**: This is the reward side of the gate. It must work correctly but is lower priority than the restriction itself since premium users are a smaller initial population.

**Independent Test**: Purchase premium (sandbox), then navigate to history older than 30 days. Verify the data loads without any lock UI. Repeat in patient mode linked to the premium caregiver.

**Acceptance Scenarios**:

1. **Given** a premium caregiver, **When** the caregiver navigates to any month or day in history, **Then** the data loads successfully without a lock overlay.
2. **Given** a patient linked to a premium caregiver, **When** the patient navigates to any month or day in history, **Then** the data loads successfully without a lock overlay.
3. **Given** a premium user viewing the history tab, **Then** the banner reads "全期間表示中".

---

### Edge Cases

- **Cutoff boundary month (straddling)**: When cutoffDate falls within a requested month (e.g., cutoffDate is Feb 15 and the user views February), the MVP locks the entire month for free users. The lock UI explains this.
- **Caregiver upgrades while patient is active**: If a caregiver upgrades to premium while a patient session is active, the patient can tap "更新" or re-navigate to trigger a fresh API call. The server will now allow the request since the linked caregiver is premium.
- **Caregiver premium expires or is refunded**: The next history request from the patient (or the caregiver) will be subject to the 30-day restriction again.
- **Timezone boundary**: cutoffDate is computed in Asia/Tokyo. A request at 23:59 JST on day X and 00:01 JST on day X+1 must resolve to the correct cutoff.
- **Entitlement state unknown at history load time**: If the entitlement state is unknown when the user navigates to the history tab, the app must show the "更新中" overlay, refresh entitlements, then proceed with the correct gate decision.
- **Network failure during history fetch**: If the API call fails for reasons other than `HISTORY_RETENTION_LIMIT`, the existing error handling (retry prompt) applies. The lock UI is shown only for the specific retention error code.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST restrict free users to viewing history within `[cutoffDate, todayTokyo]` where `cutoffDate = todayTokyo - 29 days` (inclusive on both ends), computed in the Asia/Tokyo timezone.
- **FR-002**: System MUST apply the same 30-day retention rule to both patient-mode and caregiver-mode history endpoints (4 endpoints total: patient month, patient day, caregiver month, caregiver day).
- **FR-003**: System MUST return HTTP 403 with a stable, machine-readable body `{ code: "HISTORY_RETENTION_LIMIT", message: "<text>", cutoffDate: "YYYY-MM-DD", retentionDays: 30 }` when a free user requests history before cutoffDate.
- **FR-004**: For month endpoints, if the requested month is entirely before cutoffDate OR if cutoffDate falls within the requested month, the system MUST return the retention error for free users (MVP simplification: straddling months are locked).
- **FR-005**: For day endpoints, if the requested date is before cutoffDate (`date < cutoffDate`), the system MUST return the retention error for free users.
- **FR-006**: Premium determination for caregiver sessions MUST use `caregiver_entitlements` (at least one record with status ACTIVE).
- **FR-007**: Premium determination for patient sessions MUST follow the patient's caregiver link (1:1 constraint from feature 002) to resolve the linked caregiver's entitlements. If the linked caregiver has an ACTIVE entitlement, the patient session is treated as premium.
- **FR-008**: The iOS app MUST display a retention status banner at the top of the history tab: "無料：直近30日まで（{cutoffDate}〜今日）" for free users, or "全期間表示中" for premium users.
- **FR-009**: In caregiver mode, the lock UI MUST offer navigation to the paywall with buttons: "アップグレード", "購入を復元", and "閉じる".
- **FR-010**: In patient mode, the lock UI MUST NOT display any purchase, upgrade, or paywall-related buttons. The message MUST inform the patient that their caregiver's premium status controls access.
- **FR-011**: The system MUST NOT delete or archive history data from the database. This feature is a view restriction only.
- **FR-012**: During history loading, synchronisation, or entitlement refresh, the full-screen "更新中" overlay MUST be displayed to block user interaction.

### Non-Functional Requirements *(mandatory)*

- **NFR-001 (Security)**: Server-side enforcement is mandatory. The client-side UI gate is a UX convenience only; the server is the source of truth. A modified client sending direct API requests MUST be blocked by the server.
- **NFR-002 (UX/Responsiveness)**: Any history fetch, entitlement refresh, or gate-check operation that involves waiting MUST display the full-screen "更新中" overlay to block user interaction and provide visual feedback.
- **NFR-003 (Privacy/Authorisation)**: Patient sessions MUST remain scoped to the patient's own patientId (existing authorisation). Caregiver sessions MUST remain scoped to linked patients only (existing RLS/concealment policy). The retention gate MUST NOT weaken or alter these policies.
- **NFR-004 (Documentation)**: quickstart.md MUST document free vs premium history differences, the patient-mode behaviour, and sandbox testing procedures. The OpenAPI contract MUST include the `HISTORY_RETENTION_LIMIT` error definition. data-model.md MUST document the entitlement reference path (patient -> caregiver link -> caregiver_entitlements).

### Key Entities *(no new entities required)*

- **CaregiverEntitlement** (existing from 008): Stores purchase/entitlement records per caregiver. Used to determine premium status. An ACTIVE record indicates premium.
- **CaregiverPatientLink** (existing from 002): The 1:1 link between a patient and a caregiver. Used in patient sessions to resolve the linked caregiver's premium status.

No new tables or entities are introduced in this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Free users in both patient and caregiver modes cannot view history older than 30 days. Attempting to do so results in a lock screen within 1 tap of the blocked action.
- **SC-002**: Premium users can browse all available history without encountering any lock screen or retention restriction.
- **SC-003**: Direct API requests that bypass the iOS app are rejected by the server for free users requesting history before cutoffDate, with a 100% enforcement rate.
- **SC-004**: Patient-mode lock screens contain zero billing, paywall, or upgrade UI elements.
- **SC-005**: All required backend integration tests (free/premium x caregiver/patient x month/day endpoints) and iOS unit/UI smoke tests pass.
- **SC-006**: Premium status inherited via patient-caregiver link works correctly: a patient linked to a premium caregiver can view all history.

## Definitions

- **todayTokyo**: The current date in the Asia/Tokyo timezone.
- **retentionDaysFree**: 30 (the number of days of history accessible to free users).
- **cutoffDate**: The earliest date a free user can view, inclusive. Calculated as `todayTokyo - 29 days`. Free users can view dates in the range `[cutoffDate, todayTokyo]`.
- **Premium**: A user (caregiver or patient linked to a premium caregiver) who has an ACTIVE entitlement record in `caregiver_entitlements`. Premium users ignore cutoffDate and can view all history.

## Assumptions

- The billing foundation (feature 008) is fully implemented and deployed: StoreKit2 integration, EntitlementStore, FeatureGate (including `.extendedHistory`), PaywallView, the "更新中" overlay, and the backend `caregiver_entitlements` table and endpoints are all operational.
- The history/schedule view (feature 004) is fully implemented: the 4 history endpoints (patient month/day, caregiver month/day) and the iOS calendar + day detail views are operational.
- The 1:1 patient-to-caregiver link constraint (feature 002) is in effect. Each patient has exactly one linked caregiver.
- The existing `FeatureGate.extendedHistory` case (defined in 008) will be used as the iOS-side gate identifier for this feature.
- The `retentionDaysFree` value of 30 is a product decision for MVP. The implementation should use a named constant so it can be adjusted for future plan tiers without code changes.
- Performance of the retention check is negligible (date comparison + entitlement lookup) and does not require caching.

## Non-Goals (Explicit)

- Physical deletion or archiving of history data from the database.
- Escalation push notifications (Pro tier, future feature).
- Changes to screens or features outside of the 004 history views (other than minimal UX integration such as the retention banner).
- Partial month data return for straddling months (deferred; MVP locks the entire straddling month).

## API Error Contract

When a free user requests history data before cutoffDate, the server responds:

- **HTTP Status**: 403 Forbidden
- **Body**:
  ```json
  {
    "code": "HISTORY_RETENTION_LIMIT",
    "message": "履歴の閲覧は直近30日間に制限されています。",
    "cutoffDate": "2026-01-12",
    "retentionDays": 30
  }
  ```
- The `code` field is a stable, machine-readable identifier. The iOS app uses this value to decide whether to show the retention lock UI (as opposed to a generic 403/auth failure).
- The `message` field is informational and may be localised in future; clients should not parse it programmatically.
- The `cutoffDate` field is the earliest viewable date in `YYYY-MM-DD` format (Asia/Tokyo).
- The `retentionDays` field allows the client to display contextual information without hardcoding the limit.

### Affected Endpoints

| Endpoint | Mode | Gate Trigger |
| --- | --- | --- |
| `GET /api/patient/history/month?year=Y&month=M` | Patient | Requested month entirely before cutoffDate, or straddles cutoffDate |
| `GET /api/patient/history/day?date=D` | Patient | Requested date < cutoffDate |
| `GET /api/patients/{patientId}/history/month?year=Y&month=M` | Caregiver | Same as patient month |
| `GET /api/patients/{patientId}/history/day?date=D` | Caregiver | Same as patient day |

### Premium Resolution by Session Type

| Session Type | Premium Lookup Path |
| --- | --- |
| Caregiver | `caregiver_entitlements` where `caregiverId = session.caregiverUserId` and `status = ACTIVE` |
| Patient | `caregiver_patient_link` where `patientId = session.patientId` → `caregiverId` → `caregiver_entitlements` where `status = ACTIVE` |

## UX Copy (Japanese)

### History Tab Banner

- **Free**: 無料：直近30日まで（{cutoffDate}〜今日）
- **Premium**: 全期間表示中

### Lock UI — Caregiver Mode

- **Title**: プレミアムで全期間の履歴を閲覧
- **Body**: 30日より前の履歴はプレミアムで閲覧できます
- **Primary button**: アップグレード
- **Secondary button**: 購入を復元
- **Dismiss button**: 閉じる

### Lock UI — Patient Mode

- **Title**: 履歴の閲覧制限
- **Body**: 30日より前の履歴はプレミアムで閲覧できます。家族がプレミアムの場合は自動で表示されます。
- **Optional button**: 更新（re-fetches entitlement and retries the history request）

## Testing Requirements

### Backend Integration Tests

- **Free caregiver — month before cutoff**: Request a month entirely before cutoffDate → 403 `HISTORY_RETENTION_LIMIT` with correct `cutoffDate` and `retentionDays`.
- **Free caregiver — day before cutoff**: Request a day before cutoffDate → 403 `HISTORY_RETENTION_LIMIT`.
- **Free caregiver — straddling month**: Request a month where cutoffDate falls within the month → 403 `HISTORY_RETENTION_LIMIT` (MVP lock).
- **Free caregiver — day within range**: Request a day within `[cutoffDate, todayTokyo]` → 200 with data.
- **Premium caregiver — any date**: Request a month/day before cutoffDate → 200 with data.
- **Free patient session — linked to free caregiver**: Request day before cutoff → 403 `HISTORY_RETENTION_LIMIT`.
- **Free patient session — linked to premium caregiver**: Request day before cutoff → 200 with data (premium inherited via link).
- **Non-authenticated / other patient**: Existing deny/conceal behaviour preserved (401/404).

### iOS Unit Tests

- **cutoffDate calculation**: Verify `cutoffDate = todayTokyo - 29 days` with Asia/Tokyo timezone, including edge cases around midnight JST.
- **Lock decision from error code**: Verify that receiving `HISTORY_RETENTION_LIMIT` error code triggers the lock UI state (not a generic error or auth failure).
- **Banner text selection**: Verify free state shows retention banner with cutoffDate, premium state shows "全期間表示中".
- **Patient mode gate**: Verify that patient-mode lock UI does not include paywall/purchase buttons.

### iOS UI Smoke Tests

- **Caregiver mode — past navigation**: Navigate to a month/day before cutoff → lock overlay shown → "アップグレード" taps open paywall.
- **Patient mode — past navigation**: Navigate to a month/day before cutoff → lock overlay shown → no purchase buttons visible.
- **Premium state**: Navigate freely through all history without encountering lock UI.
- **"更新中" overlay**: Verify the overlay blocks interaction during history fetch and entitlement refresh.

## Documentation Updates

- **quickstart.md**: Explain free vs premium history access differences, patient-mode behaviour (no purchase UI, caregiver premium enables access), and sandbox testing steps (purchase premium → verify full history access).
- **contracts/openapi.yaml**: Add the `HISTORY_RETENTION_LIMIT` error response definition to all 4 history endpoints, including the `cutoffDate` and `retentionDays` fields.
- **data-model.md**: Document the entitlement reference path for patient sessions: `patient → CaregiverPatientLink → caregiverId → CaregiverEntitlement (status=ACTIVE)`.
