# Data Model: History Retention Limit (010)

## Overview

Feature 010 introduces no new database entities or tables. It adds a view restriction (retention gate) that uses two existing entities from features 002 (Family Linking) and 008 (Billing Foundation). History data remains in the database regardless of the user's plan — this is a read-time restriction only.

## Entities Used

### CaregiverEntitlement (from 008)

Used to determine whether a caregiver (or a patient's linked caregiver) has active premium status.

- **id**: UUID (primary key)
- **caregiverId**: string (references Supabase Auth UID)
- **productId**: string (App Store product identifier)
- **status**: `EntitlementStatus` enum (`ACTIVE` | `REVOKED`)
- **originalTransactionId**: string (unique — prevents duplicate claims)
- **transactionId**: string
- **purchasedAt**: timestamp
- **environment**: string (`Sandbox` | `Production`)
- **createdAt**: timestamp (auto)
- **updatedAt**: timestamp (auto)

**Query for caregiver premium**: `prisma.caregiverEntitlement.findFirst({ where: { caregiverId, status: "ACTIVE" } })` — returns non-null if premium.

### CaregiverPatientLink (from 002)

Used to resolve the linked caregiver for a patient session (1:1 relationship).

- **id**: UUID (primary key)
- **caregiverId**: string
- **patientId**: string (unique — 1 patient = 1 caregiver)
- **status**: `LinkStatus` enum (`ACTIVE` | `REVOKED`)
- **revokedAt**: timestamp (nullable)
- **createdAt**: timestamp (auto)
- **updatedAt**: timestamp (auto)

**Query for patient premium**: `prisma.caregiverPatientLink.findFirst({ where: { patientId, status: "ACTIVE" } })` — returns the linked `caregiverId`, then check `CaregiverEntitlement` for that caregiver.

## New Domain Artifacts (Not Database Entities)

### HistoryRetentionError (backend, new)

A domain error class thrown when a free user requests history data before the cutoff date.

- **Properties**:
  - `cutoffDate`: string (YYYY-MM-DD, Asia/Tokyo)
  - `retentionDays`: number (currently 30)
  - `statusCode`: 403

- **Serialised response**:
  ```json
  {
    "code": "HISTORY_RETENTION_LIMIT",
    "message": "履歴の閲覧は直近30日間に制限されています。",
    "cutoffDate": "2026-01-12",
    "retentionDays": 30
  }
  ```

### RETENTION_DAYS_FREE Constant (backend, new)

- **Value**: `30`
- **Location**: `api/src/services/historyRetentionConstants.ts`
- **Purpose**: Single source of truth for the free-plan history retention window. Adjustable for future plan tiers without code changes.

### Cutoff Date Definition

- **todayTokyo**: The current date in Asia/Tokyo timezone
- **cutoffDate**: `todayTokyo - 29 days` (inclusive)
- **Viewable range (free)**: `[cutoffDate, todayTokyo]` — exactly 30 days
- **Viewable range (premium)**: All available history (no restriction)

## Premium Resolution Flow

### Caregiver Session

```
requireCaregiver(authHeader)
  → session = { role: "caregiver", caregiverUserId }
  → prisma.caregiverEntitlement.findFirst({ caregiverId: session.caregiverUserId, status: "ACTIVE" })
  → if found: premium (allow all history)
  → if not found: free (enforce cutoff)
```

### Patient Session

```
requirePatient(authHeader)
  → session = { role: "patient", patientId }
  → prisma.caregiverPatientLink.findFirst({ patientId: session.patientId, status: "ACTIVE" })
  → if not found: free (no active link)
  → caregiverId = link.caregiverId
  → prisma.caregiverEntitlement.findFirst({ caregiverId, status: "ACTIVE" })
  → if found: premium (caregiver is premium, patient inherits)
  → if not found: free (enforce cutoff)
```

## Retention Check Logic (Pseudocode)

### Day Endpoint

```
FUNCTION checkRetentionForDay(dateStr, sessionType, sessionId):
  cutoffDate = getTodayTokyo() - 29 days
  IF dateStr < cutoffDate:
    IF sessionType == "caregiver":
      premium = isPremiumForCaregiver(sessionId)
    ELSE:
      premium = isPremiumForPatient(sessionId)
    IF NOT premium:
      THROW HistoryRetentionError(cutoffDate, retentionDays=30)
```

### Month Endpoint

```
FUNCTION checkRetentionForMonth(year, month, sessionType, sessionId):
  cutoffDate = getTodayTokyo() - 29 days
  firstDayOfMonth = YYYY-MM-01
  IF firstDayOfMonth < cutoffDate:
    IF sessionType == "caregiver":
      premium = isPremiumForCaregiver(sessionId)
    ELSE:
      premium = isPremiumForPatient(sessionId)
    IF NOT premium:
      THROW HistoryRetentionError(cutoffDate, retentionDays=30)
```

Note: The month check uses `firstDayOfMonth < cutoffDate`, which locks any month that contains dates before the cutoff (MVP straddling rule).

## Affected Endpoints (from 004)

| Endpoint | Session Type | Retention Check |
|----------|-------------|-----------------|
| `GET /api/patient/history/month?year=Y&month=M` | Patient | `checkRetentionForMonth(Y, M, "patient", session.patientId)` |
| `GET /api/patient/history/day?date=D` | Patient | `checkRetentionForDay(D, "patient", session.patientId)` |
| `GET /api/patients/{patientId}/history/month?year=Y&month=M` | Caregiver | `checkRetentionForMonth(Y, M, "caregiver", session.caregiverUserId)` |
| `GET /api/patients/{patientId}/history/day?date=D` | Caregiver | `checkRetentionForDay(D, "caregiver", session.caregiverUserId)` |

## Unchanged Operations

- **History data storage**: No data is deleted or archived. Existing dose records, PRN records, and schedule data remain intact.
- **Patient creation / linking** (009 gate): Unchanged.
- **Entitlement claim / read** (008): Unchanged.
- **Today tab / medication list**: Not affected by the retention gate.
- **Dose recording**: Not affected — users can record doses regardless of retention.

## Relationships Diagram

```
CaregiverAccount (Supabase Auth)
  ├── CaregiverEntitlement (0..N) ─── premium status check
  ├── CaregiverPatientLink (0..N)
  │     └── Patient (1:1 via unique patientId)
  │           └── History Data (Dose Records, PRN Records, Schedules)
  │                 └── [Gate: if NOT premium AND requested date < cutoffDate → BLOCK]
  └── [Premium check: caregiver → entitlement; patient → link → caregiver → entitlement]
```

## iOS Client State

Existing from 008, no schema changes:

- `EntitlementState`: `.unknown` | `.free` | `.premium`
- `FeatureGate.extendedHistory`: requires `.premium` tier (already defined in 008)
- `FeatureGate.isUnlocked(.extendedHistory, for: state)`: returns `true` only for `.premium`

New iOS artifacts:

- `APIError.historyRetentionLimit(cutoffDate: String, retentionDays: Int)`: thrown when server returns `HISTORY_RETENTION_LIMIT`
- `FeatureGate.retentionDaysFree = 30`: client-side constant for banner display
- `FeatureGate.historyCutoffDate() -> String`: client-side cutoff calculation for banner (Asia/Tokyo)
