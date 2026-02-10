# Data Model: Billing Foundation (Premium Unlock)

## Entities

### CaregiverEntitlement (new)

- **id**: unique identifier (UUID)
- **caregiverId**: string, references caregiver account (Supabase auth UID)
- **productId**: string, App Store product identifier (e.g., `com.yourcompany.medicationapp.premium_unlock`)
- **status**: enum `EntitlementStatus` (`ACTIVE` | `REVOKED`), default `ACTIVE`
- **originalTransactionId**: string, unique — Apple's stable transaction identifier across restores
- **transactionId**: string — most recent transaction identifier for this entitlement
- **purchasedAt**: timestamp — original purchase date from transaction payload
- **environment**: string — `Sandbox` or `Production`
- **createdAt**: timestamp (auto)
- **updatedAt**: timestamp (auto)

**Indexes**:
- Unique on `originalTransactionId` (prevents duplicate claims)
- Index on `caregiverId` (lookup by caregiver)

### EntitlementStatus (new enum)

- `ACTIVE` — entitlement is valid and premium access is granted
- `REVOKED` — entitlement has been revoked (refund, chargeback, or manual revocation)

## iOS Client State

### EntitlementState (Swift enum, client-side only)

- `unknown` — entitlement has not yet been evaluated (app launch, before first check)
- `free` — evaluation complete, no active premium entitlement found
- `premium` — evaluation complete, active premium entitlement confirmed

### EntitlementTier (Swift enum, for FeatureGate)

- `free` — no purchase required
- `premium` — requires Premium Unlock purchase
- `pro` — reserved for future Pro tier (always gated off in this feature)

## FeatureGate Definitions

| Gate Key           | Required Tier | Description                                       | Status in 008      |
|--------------------|---------------|---------------------------------------------------|---------------------|
| multiplePatients   | premium       | Register 2nd+ patients in caregiver mode          | Gate defined        |
| extendedHistory    | premium       | History beyond free 30-day limit                  | Gate defined        |
| pdfExport          | premium       | Export records as PDF                              | Gate defined        |
| enhancedAlerts     | premium       | Enhanced caregiver alerts (low inventory push)     | Gate defined        |
| escalationPush     | pro           | Escalation push (missed dose after delay)          | Excluded from Premium |

All gates are defined in this feature. The actual feature implementations behind each gate are delivered in subsequent features. In 008, gate checks return `true` (unlocked) or `false` (locked) based on the user's `EntitlementState` and the gate's `requiredTier`.

## Relationships

- **CaregiverEntitlement** belongs to a caregiver account (via `caregiverId`; no foreign key since caregiver accounts live in Supabase Auth, not in app DB).
- One caregiver can have at most one `CaregiverEntitlement` record per `originalTransactionId`.
- In the MVP with a single Non-Consumable product, a caregiver is expected to have zero or one active entitlement.

## Validation & Rules

### Claim (POST /api/iap/claim)

- `productId` MUST match the known Premium Unlock product identifier.
- `signedTransactionInfo` MUST be a non-empty string (JWS format).
- `environment` is optional; defaults to `Production` if omitted.
- Upsert by `originalTransactionId`: if a record with the same `originalTransactionId` exists, update `transactionId`, `status`, and `updatedAt`; otherwise insert.
- Only authenticated caregiver sessions may call claim.
- Non-authenticated or patient-session requests are rejected with 401.

### Entitlement Read (GET /api/me/entitlements)

- Returns aggregated `premium: boolean` (true if any entitlement with status `ACTIVE` exists for the caregiver).
- Returns list of entitlement records for audit/display.
- Only authenticated caregiver sessions may read.

### Premium Determination (server-side)

- `premium = true` if at least one `CaregiverEntitlement` record exists for the caregiver with `status = ACTIVE` and `productId` matching Premium Unlock.
- `premium = false` otherwise.

### Premium Determination (iOS client-side)

- Iterate `Transaction.currentEntitlements`; if any verified transaction matches the Premium Unlock product ID, state is `.premium`.
- If iteration completes with no match, state is `.free`.
- If evaluation has not run yet, state is `.unknown`.

### Revocation (MVP)

- On re-evaluation (lifecycle refresh), if `Transaction.currentEntitlements` no longer contains the product, local state drops to `.free`.
- Server-side: a future feature can set `status = REVOKED` via App Store Server Notifications. In MVP, the server record remains `ACTIVE` until explicitly updated.

### Patient Mode

- Patient mode users never interact with entitlement or billing logic.
- No billing UI elements are rendered in patient mode views.
- FeatureGate checks are not invoked in patient mode flows.
