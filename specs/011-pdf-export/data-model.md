# Data Model: PDF Export of Medication History (011)

## Overview

Feature 011 introduces no new database entities or tables. It adds a report aggregation endpoint that queries existing entities from features 002 (Family Linking), 003 (Dose Recording), 004 (History Schedule View), 007 (PRN Medications), and 008 (Billing Foundation). PDF generation happens on-device (iOS) from the API response — no server-side PDF storage or rendering.

## Entities Used

### DoseRecord (from 003)

Used to populate the scheduled dose sections of the PDF. Each record represents a single medication dose at a scheduled time.

- **id**: UUID (primary key)
- **patientId**: string (references Patient)
- **medicationId**: string (references Medication)
- **scheduledAt**: timestamp (the scheduled time for the dose)
- **takenAt**: timestamp (when the dose was actually taken; defaults to creation time)
- **recordedByType**: `RecordedByType` enum (`PATIENT` | `CAREGIVER`)
- **recordedById**: string (nullable)
- **createdAt**: timestamp (auto)
- **updatedAt**: timestamp (auto)

**Indexes**: `@@unique([patientId, medicationId, scheduledAt])`, `@@index([patientId, scheduledAt])`

**Query for report**: `getScheduleWithStatus(patientId, from, to, "Asia/Tokyo")` generates the full schedule for the date range and merges with dose records to produce `effectiveStatus` per dose (TAKEN, MISSED, or PENDING).

### PrnDoseRecord (from 007)

Used to populate the PRN (as-needed) sections of the PDF.

- **id**: UUID (primary key)
- **patientId**: string (references Patient)
- **medicationId**: string (references Medication, includes `name` via relation)
- **takenAt**: timestamp (when the dose was taken; defaults to creation time)
- **quantityTaken**: float (number of doses taken)
- **actorType**: `RecordedByType` enum (`PATIENT` | `CAREGIVER`)
- **createdAt**: timestamp (auto)

**Indexes**: `@@index([patientId, takenAt(sort: Desc)])`, `@@index([patientId, medicationId, takenAt(sort: Desc)])`

**Query for report**: `listPrnHistoryItemsByRange({ patientId, from, to, timeZone: "Asia/Tokyo" })` returns PRN items with medication name and count-by-day grouping.

### Medication (from 001)

Referenced for medication name, dosage text, and dose count per intake. Included via schedule generation (`medicationSnapshot` in `ScheduleResponseDose`).

- **name**: string
- **dosageText**: string
- **doseCountPerIntake**: number

### Patient (from 002)

Referenced for the patient's display name, included in the PDF header and API response.

- **id**: UUID (primary key)
- **displayName**: string

**Query for report**: Retrieved via `getPatientRecordForCaregiver(patientId, caregiverUserId)` during scope assertion — no additional query needed.

### CaregiverEntitlement (from 008)

Used for premium status verification (feature gate and optional retention check).

- **caregiverId**: string
- **status**: `EntitlementStatus` enum (`ACTIVE` | `REVOKED`)

**Query for premium check**: `prisma.caregiverEntitlement.findFirst({ where: { caregiverId, status: "ACTIVE" } })`

## New Domain Artifacts (Not Database Entities)

### InvalidRangeError (backend, new)

A domain error class thrown when the report endpoint receives invalid date range parameters.

- **Properties**:
  - `code`: string (`"INVALID_RANGE"`)
  - `statusCode`: 400

- **Serialised response**:
  ```json
  {
    "code": "INVALID_RANGE",
    "message": "指定された期間が不正です。"
  }
  ```

### MAX_REPORT_RANGE_DAYS Constant (backend, new)

- **Value**: `90`
- **Location**: `api/src/validators/reportValidator.ts` (or dedicated constants file)
- **Purpose**: Maximum number of days (inclusive) allowed for a single report request. Adjustable for future changes without code modifications.

### HistoryReportResponse (API transport model, new)

The response shape returned by the report endpoint. Not a database entity — assembled at request time from existing data.

#### Top-Level Structure

| Field | Type | Description |
|-------|------|-------------|
| `patient` | `ReportPatient` | Patient identity |
| `range` | `ReportRange` | Date range metadata |
| `days` | `ReportDay[]` | Per-day history data |

#### ReportPatient

| Field | Type | Description |
|-------|------|-------------|
| `id` | string (UUID) | Patient ID |
| `displayName` | string | Patient display name |

#### ReportRange

| Field | Type | Description |
|-------|------|-------------|
| `from` | string (date) | Start date YYYY-MM-DD |
| `to` | string (date) | End date YYYY-MM-DD |
| `timezone` | string | Always `"Asia/Tokyo"` |
| `days` | number | Number of days in range (inclusive) |

#### ReportDay

| Field | Type | Description |
|-------|------|-------------|
| `date` | string (date) | Date YYYY-MM-DD |
| `slots` | `ReportSlots` | Scheduled doses grouped by slot |
| `prn` | `ReportPrnItem[]` | PRN records for this day |

#### ReportSlots

| Field | Type | Description |
|-------|------|-------------|
| `morning` | `ReportSlotItem[]` | Morning slot medications |
| `noon` | `ReportSlotItem[]` | Noon slot medications |
| `evening` | `ReportSlotItem[]` | Evening slot medications |
| `bedtime` | `ReportSlotItem[]` | Bedtime slot medications |

#### ReportSlotItem

| Field | Type | Description |
|-------|------|-------------|
| `medicationId` | string (UUID) | Medication ID |
| `name` | string | Medication name |
| `dosageText` | string | Dosage description (e.g., "5mg") |
| `doseCount` | number | Number of doses per intake |
| `status` | string | `"TAKEN"` \| `"MISSED"` \| `"PENDING"` |
| `recordedAt` | string (ISO 8601, nullable) | When the dose was recorded (Asia/Tokyo offset), null if not taken |

#### ReportPrnItem

| Field | Type | Description |
|-------|------|-------------|
| `medicationId` | string (UUID) | Medication ID |
| `name` | string | Medication name |
| `dosageText` | string | Dosage description |
| `quantity` | number | Number of doses taken |
| `recordedAt` | string (ISO 8601) | When the dose was recorded (Asia/Tokyo offset) |
| `recordedBy` | string | `"PATIENT"` \| `"CAREGIVER"` |

## Report Assembly Flow (Pseudocode)

```
FUNCTION generateReport(patientId, fromStr, toStr):
  patient = getPatientRecord(patientId)  // already fetched in scope assertion
  from = parseToTokyoStart(fromStr)      // YYYY-MM-DD → Date at 00:00 Asia/Tokyo
  to = parseToTokyoEnd(toStr)            // YYYY-MM-DD → Date at 00:00 next day Asia/Tokyo

  // Scheduled doses
  allDoses = getScheduleWithStatus(patientId, from, to, "Asia/Tokyo")
  dosesByDate = groupDosesByLocalDate(allDoses, "Asia/Tokyo")

  // PRN records
  prnResult = listPrnHistoryItemsByRange({ patientId, from, to, timeZone: "Asia/Tokyo" })

  // Build per-day structure
  days = []
  FOR each date from fromStr to toStr (inclusive, Asia/Tokyo):
    dayDoses = dosesByDate.get(date) ?? []
    slots = { morning: [], noon: [], evening: [], bedtime: [] }
    FOR each dose in dayDoses:
      slot = resolveSlot(dose.scheduledAt, "Asia/Tokyo")
      slots[slot].push({
        medicationId, name, dosageText, doseCount,
        status: dose.effectiveStatus.toUpperCase(),
        recordedAt: (status == "TAKEN") ? dose.takenAt : null
      })
    dayPrn = prnResult.items.filter(item => dateKeyOf(item.takenAt) == date)
    days.push({ date, slots, prn: dayPrn.map(...) })

  rangeDays = daysBetweenInclusive(fromStr, toStr)
  RETURN { patient: { id, displayName }, range: { from, to, timezone, days: rangeDays }, days }
```

## Affected Endpoints

| Endpoint | Action | Description |
|----------|--------|-------------|
| `GET /api/patients/{patientId}/history/report?from=&to=` | NEW | Returns aggregated history for PDF generation |

## Unchanged Operations

- **History month/day endpoints** (004): Unchanged — continue to serve the history tab views.
- **Dose recording** (003): Unchanged — dose records are read-only for this feature.
- **PRN dose recording** (007): Unchanged — PRN records are read-only for this feature.
- **Entitlement claim/read** (008): Unchanged.
- **Patient creation/linking** (002): Unchanged.
- **History retention gate** (010): Unchanged — optionally applied to the report endpoint for defense-in-depth.
- **Today tab / medication list**: Not affected.

## Relationships Diagram

```
CaregiverAccount (Supabase Auth)
  ├── CaregiverEntitlement (0..N) ─── premium status check (FeatureGate.pdfExport)
  ├── CaregiverPatientLink (0..N)
  │     └── Patient (1:1 via unique patientId)
  │           ├── DoseRecord (0..N) ─── scheduled dose history
  │           ├── PrnDoseRecord (0..N) ─── PRN dose history
  │           └── [Report endpoint: aggregates DoseRecord + PrnDoseRecord for date range]
  └── [Gate: caregiver must be premium to access report endpoint]
```

## iOS Client Models

### HistoryReportResponseDTO (new)

Decodable struct matching the API response. Contains nested DTOs:
- `HistoryReportPatientDTO` (id, displayName)
- `HistoryReportRangeDTO` (from, to, timezone, days)
- `HistoryReportDayDTO` (date, slots, prn)
- `HistoryReportSlotItemDTO` (medicationId, name, dosageText, doseCount, status, recordedAt?)
- `HistoryReportPrnItemDTO` (medicationId, name, dosageText, quantity, recordedAt, recordedBy)

### APIError Extension (modify)

- New case or generic handling for `INVALID_RANGE` error code (HTTP 400) from the report endpoint.
- Existing `historyRetentionLimit` case (from 010) handles `HISTORY_RETENTION_LIMIT` if retention check is applied.

### FeatureGate (existing)

- `FeatureGate.pdfExport` (defined in 008): requires `.premium` tier
- `FeatureGate.isUnlocked(.pdfExport, for: entitlementStore.state)`: used to gate the export button
