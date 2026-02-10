# Data Model: Free Limit Gates (009)

## Overview

Feature 009 introduces no new database entities or tables. It adds gate logic that uses three existing entities from features 002 (Family Linking) and 008 (Billing Foundation).

## Entities Used

### CaregiverEntitlement (from 008)

Used to determine whether a caregiver has active premium status.

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

**Query for gate**: `tx.caregiverEntitlement.findFirst({ where: { caregiverId, status: "ACTIVE" } })` — returns non-null if premium.

### CaregiverPatientLink (from 002)

Used to count how many patients are actively linked to a caregiver.

- **id**: UUID (primary key)
- **caregiverId**: string
- **patientId**: string (unique — 1 patient = 1 caregiver)
- **status**: `LinkStatus` enum (`ACTIVE` | `REVOKED`)
- **revokedAt**: timestamp (nullable)
- **createdAt**: timestamp (auto)
- **updatedAt**: timestamp (auto)

**Query for gate**: `tx.caregiverPatientLink.count({ where: { caregiverId, status: "ACTIVE" } })` — returns the number of active linked patients. Only ACTIVE links count toward the limit (FR-012).

### Patient (from 002)

The resource being gated. Created by `createPatientForCaregiver()` alongside its `CaregiverPatientLink`.

- **id**: UUID (primary key)
- **caregiverId**: string
- **displayName**: string
- **createdAt**: timestamp (auto)
- **updatedAt**: timestamp (auto)

## New Domain Artifacts (Not Database Entities)

### PatientLimitError (backend, new)

A domain error class thrown when a free caregiver exceeds the patient limit.

- **Properties**:
  - `limit`: number (the maximum allowed for the plan, currently 1)
  - `current`: number (the caregiver's current active linked patient count)
  - `statusCode`: 403

- **Serialised response**:
  ```json
  {
    "code": "PATIENT_LIMIT_EXCEEDED",
    "message": "Patient limit reached. Upgrade to premium for unlimited patients.",
    "limit": 1,
    "current": 1
  }
  ```

### FREE_PATIENT_LIMIT Constant (backend, new)

- **Value**: `1`
- **Location**: `api/src/services/patientLimitConstants.ts`
- **Purpose**: Single source of truth for the free-plan patient limit. Easily adjustable for future plan tiers without code changes.

## Gate Logic (Pseudocode)

```
FUNCTION createPatientForCaregiver(caregiverId, displayName):
  BEGIN TRANSACTION(tx):
    activeCount = tx.caregiverPatientLink.count(caregiverId, status=ACTIVE)
    
    IF activeCount >= FREE_PATIENT_LIMIT:
      hasEntitlement = tx.caregiverEntitlement.findFirst(caregiverId, status=ACTIVE)
      IF NOT hasEntitlement:
        THROW PatientLimitError(limit=FREE_PATIENT_LIMIT, current=activeCount)
    
    patient = tx.patient.create(caregiverId, displayName)
    tx.caregiverPatientLink.create(caregiverId, patient.id, status=ACTIVE)
    RETURN patient
  COMMIT
```

## Relationships Diagram

```
CaregiverAccount (Supabase Auth)
  ├── CaregiverEntitlement (0..N) ─── premium status check
  ├── CaregiverPatientLink (0..N) ─── active count for gate
  │     └── Patient (1:1 via unique patientId)
  └── [Gate: if NOT premium AND active links >= 1 → BLOCK new creation]
```

## Validation & Rules

### Patient Creation (POST /api/patients) — Updated

1. Authenticate caregiver (existing: `requireCaregiver()`)
2. Validate input (existing: `validatePatientCreate()`)
3. **NEW**: Inside transaction, count active links and check entitlement
4. If not premium and count >= `FREE_PATIENT_LIMIT` → throw `PatientLimitError`
5. Create `Patient` + `CaregiverPatientLink` (existing)

### Unchanged Operations

- **Patient list** (GET /api/patients): No limit gate. Free caregivers with >1 patients (grandfather) see all.
- **Linking code issuance** (POST /api/patients/{id}/linking-codes): No limit gate. Only issues codes for already-linked patients.
- **Code exchange** (POST /api/patient/link): No limit gate. Patient-mode endpoint; creates session, not link.
- **Patient revoke**: No limit gate. Reduces active count (may re-enable future creation for free caregivers).
- **Patient delete**: No limit gate. Removes patient and all related data.

### iOS Client State

Existing from 008, no changes:

- `EntitlementState`: `.unknown` | `.free` | `.premium`
- `FeatureGate.multiplePatients`: requires `.premium` tier
- `FeatureGate.isUnlocked(.multiplePatients, for: state)`: returns `true` only for `.premium`

New iOS error case:

- `APIError.patientLimitExceeded(limit: Int, current: Int)`: thrown when server returns `PATIENT_LIMIT_EXCEEDED`
