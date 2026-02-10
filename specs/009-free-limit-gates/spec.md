# Feature Specification: Free Limit Gates

**Feature Branch**: `009-free-limit-gates`  
**Created**: 2026-02-10  
**Status**: Draft  
**Input**: User description: "Introduce the first real free vs premium gate: free caregivers can register/link at most 1 patient; premium caregivers can register/link unlimited patients."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Free Caregiver Patient Limit (Priority: P1)

As a free caregiver, I can register only 1 patient. When I attempt to add a second patient, I am shown a paywall and the add is blocked on both the client and the server.

**Why this priority**: This is the core monetisation gate. Without it, there is no distinction between free and premium.

**Independent Test**: Create a free caregiver account, add 1 patient successfully, then attempt to add a second. Verify the paywall appears and the backend rejects the request.

**Acceptance Scenarios**:

1. **Given** a free caregiver with 0 linked patients, **When** the caregiver creates a patient, **Then** the patient is created successfully and linked to the caregiver.
2. **Given** a free caregiver with 1 linked patient, **When** the caregiver taps the "add patient" action, **Then** the app blocks the action and presents the paywall — no network request to create a patient is made.
3. **Given** a free caregiver with 1 linked patient, **When** a modified client sends a create-patient request directly to the server, **Then** the server rejects the request with a stable `PATIENT_LIMIT_EXCEEDED` error.
4. **Given** a caregiver whose entitlement state is unknown, **When** the caregiver taps the "add patient" action, **Then** the app shows the "更新中" overlay, refreshes entitlement, and applies the correct gate decision afterward.

---

### User Story 2 — Backend Enforcement (Priority: P1)

As the system, I enforce the free patient limit on the server so that the gate cannot be bypassed by a modified or outdated client.

**Why this priority**: Server-side enforcement is a security requirement that must ship alongside the client gate to prevent abuse.

**Independent Test**: Using a direct HTTP client (bypassing the iOS app), send a create-patient request as a free caregiver who already has 1 patient. Verify the server returns an error with the `PATIENT_LIMIT_EXCEEDED` code.

**Acceptance Scenarios**:

1. **Given** a free caregiver with 0 linked patients, **When** the server receives a create-patient request, **Then** the server allows the operation.
2. **Given** a free caregiver with 1 linked patient, **When** the server receives a create-patient request, **Then** the server rejects with HTTP 403 and the response body `{ "code": "PATIENT_LIMIT_EXCEEDED", "limit": 1, "current": <count> }`.
3. **Given** a premium caregiver with any number of linked patients, **When** the server receives a create-patient request, **Then** the server allows the operation.
4. **Given** a caregiver who does not own a particular patient, **When** that caregiver attempts to create, list, or read patients belonging to another caregiver, **Then** the server denies or conceals the data (existing RLS policy unchanged).

---

### User Story 3 — Premium Caregiver Unlimited (Priority: P2)

As a premium caregiver, I can add multiple patients without any restriction or paywall interruption.

**Why this priority**: Premium must deliver tangible value. This is the simplest and most visible premium benefit and must work seamlessly once purchased.

**Independent Test**: Purchase premium (sandbox), then add 2+ patients. Verify no paywall is shown and the server allows each creation.

**Acceptance Scenarios**:

1. **Given** a premium caregiver with any number of linked patients, **When** the caregiver taps the "add patient" action, **Then** the app proceeds to patient creation without showing a paywall.
2. **Given** a premium caregiver, **When** the caregiver creates a second, third, or Nth patient, **Then** each creation succeeds on both client and server.

---

### User Story 4 — Grandfather Rule (Priority: P3)

As a caregiver who already has multiple linked patients from a previous app version (before the gate existed), I can continue to view and manage all of them on the free plan, but I cannot add new patients beyond the limit.

**Why this priority**: Existing users must not lose access to data they already had. However, this is an edge case that only applies to early adopters.

**Independent Test**: Seed a free caregiver account with 3 linked patients (simulating pre-gate data). Open the app. Verify all 3 patients appear in the list and are selectable. Then attempt to add a 4th patient and verify the paywall appears.

**Acceptance Scenarios**:

1. **Given** a free caregiver with 3 pre-existing linked patients, **When** the caregiver opens the patient list, **Then** all 3 patients are visible and selectable.
2. **Given** a free caregiver with 3 pre-existing linked patients, **When** the caregiver taps the "add patient" action, **Then** the app blocks the action and presents the paywall.
3. **Given** a free caregiver with 3 pre-existing linked patients, **When** a modified client sends a create-patient request to the server, **Then** the server rejects with `PATIENT_LIMIT_EXCEEDED` (current=3, limit=1).

---

### Edge Cases

- **Caregiver revokes a patient then tries to add a new one**: If the revocation reduces the active linked count below the free limit, the caregiver should be able to add a new patient. The count MUST reflect only ACTIVE links, not revoked ones.
- **Race condition — two simultaneous create requests**: The server must ensure atomicity so that a free caregiver cannot create two patients by sending concurrent requests. Only one should succeed.
- **Network failure during entitlement refresh**: If the entitlement refresh fails, the app must not silently allow or block the action. It should display an error and let the user retry.
- **Premium expires or is refunded after multiple patients are added**: Existing linked patients remain accessible (grandfather rule applies retroactively). The caregiver simply cannot add new patients.
- **Patient mode user**: Patient mode must never encounter paywall or upgrade UI, regardless of the caregiver's entitlement status.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST check the caregiver's premium status and current linked-patient count before allowing creation of a new patient.
- **FR-002**: If the caregiver is NOT premium and the count of ACTIVE linked patients is >= 1, the system MUST reject patient creation.
- **FR-003**: The rejection response MUST use HTTP 403 with a stable, machine-readable body: `{ "code": "PATIENT_LIMIT_EXCEEDED", "message": "<human-readable text>", "limit": 1, "current": <N> }`.
- **FR-004**: The iOS app MUST intercept a `PATIENT_LIMIT_EXCEEDED` response and present the paywall.
- **FR-005**: The iOS app MUST perform a local entitlement check BEFORE making the create-patient network request. If the caregiver is free and count >= 1, the paywall MUST be shown without issuing the network call.
- **FR-006**: If the caregiver's entitlement state is unknown at the time of the gate check, the app MUST refresh entitlements first, displaying the full-screen "更新中" overlay while blocking user interaction.
- **FR-007**: Free caregivers who already have more than 1 linked patient (grandfather case) MUST be able to view, select, and manage all existing linked patients without restriction.
- **FR-008**: Only the "add patient" action is gated. The patient list, patient detail, linking code issuance for existing patients, dose recording, and all other operations on existing patients MUST remain unrestricted.
- **FR-009**: Patient mode MUST NOT display any billing, paywall, upgrade, or premium-related UI.
- **FR-010**: Premium status MUST be determined server-side from the entitlements data store; the server MUST NOT trust the client's self-reported premium status.
- **FR-011**: The free-plan patient limit MUST be 1 (single constant, easily adjustable for future plan tiers).
- **FR-012**: The count of linked patients MUST consider only ACTIVE links (revoked links are excluded).

### Non-Functional Requirements *(mandatory)*

- **NFR-001 (Security)**: Server-side enforcement is mandatory. The client-side gate is a UX convenience; the server is the source of truth. A modified client sending direct API requests MUST be blocked.
- **NFR-002 (UX/Responsiveness)**: Any entitlement refresh, gating check, or patient-add API call that involves waiting MUST display the full-screen "更新中" overlay to block user interaction and provide visual feedback.
- **NFR-003 (Privacy/RLS)**: The existing row-level security and concealment policies MUST remain unchanged. A caregiver MUST NOT be able to observe or affect patients belonging to another caregiver. Grandfather viewing MUST NOT leak patients outside of legitimate linkage.
- **NFR-004 (Documentation)**: Quickstart documentation MUST be updated to explain the free limit, how to test it in sandbox, and the grandfather rule.

### Key Entities *(no new entities required)*

- **CaregiverEntitlement** (existing from 008): Stores purchase/entitlement records per caregiver. Used to determine premium status.
- **Patient** (existing from 002): Represents a patient record created by a caregiver.
- **CaregiverPatientLink** (existing from 002): Represents the active link between a caregiver and a patient. The count of ACTIVE links determines whether the gate is triggered.

No new tables or entities are introduced in this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A free caregiver attempting to add a second patient is reliably blocked and sees the paywall within 1 tap of the blocked action.
- **SC-002**: A premium caregiver can add 2 or more patients without encountering any paywall or gate friction.
- **SC-003**: Direct API requests that bypass the iOS app are rejected by the server for free caregivers who have reached the limit, with a 100% enforcement rate.
- **SC-004**: Free caregivers who had multiple linked patients before the gate was introduced retain full read and management access to all existing patients.
- **SC-005**: Patient-mode screens contain zero billing, paywall, or upgrade UI elements.
- **SC-006**: All required iOS unit tests (gate decision logic) and backend integration tests (limit enforcement, grandfather, RLS) pass.

## Assumptions

- The billing foundation (feature 008) is fully implemented and deployed: StoreKit2 integration, EntitlementStore, FeatureGate, PaywallView, the "更新中" overlay, and the backend entitlements table and endpoints are all operational.
- The existing `FeatureGate.multiplePatients` case (defined in 008) will be used as the iOS-side gate identifier for this feature.
- The only operation that increases the caregiver-patient link count is patient creation (`POST /api/patients`). Linking code issuance and code exchange do not create new links.
- The free limit of 1 patient is a product decision for MVP. The implementation should use a named constant so it can be adjusted for future plan tiers without code changes.
- There is no concept of "shared" patients across caregivers; the existing 1-patient-to-1-caregiver constraint (unique `patientId` on `CaregiverPatientLink`) remains in effect.
- Performance of the gate check is negligible (single count query + entitlement lookup) and does not require caching.

## Non-Goals (Explicit)

- Other premium features (extended history retention, PDF export, enhanced push notifications) are NOT part of this feature.
- Escalation push (Pro plan) is NOT part of this feature.
- Patient-mode flows are NOT changed in any way.
- Changing the linking code or code-exchange flow is NOT in scope.

## API Error Contract

When a free caregiver who has reached the patient limit attempts to create a new patient, the server responds:

- **HTTP Status**: 403 Forbidden
- **Body**:
  ```
  {
    "code": "PATIENT_LIMIT_EXCEEDED",
    "message": "Patient limit reached. Upgrade to premium for unlimited patients.",
    "limit": 1,
    "current": <number of currently active linked patients>
  }
  ```
- The `code` field is a stable, machine-readable identifier. The iOS app uses this value to decide whether to show the paywall.
- The `message` field is informational and may be localised in future; clients should not parse it programmatically.
- The `limit` and `current` fields allow the client to display contextual information to the user.

## UX Copy (Japanese)

When the gate is triggered on iOS, the paywall presents:

- **Title**: プレミアムで複数患者を登録
- **Body**: 無料プランでは登録できる患者は1人までです。プレミアムで無制限に登録できます。
- **Primary button**: アップグレード
- **Secondary button**: 購入を復元
- **Dismiss button**: 閉じる

## Testing Requirements

### iOS Tests

- **Unit — gate decision logic** (`canAddPatient`):
  - free + count=0 → allowed
  - free + count=1 → blocked (paywall trigger)
  - free + count>1 (grandfather) → viewing allowed, add blocked
  - premium + any count → allowed
- **UI smoke tests**:
  - Free caregiver with 1 patient: tapping "add patient" → paywall shown, no API call proceeds
  - Premium caregiver: add patient proceeds without paywall
  - Patient mode: no upgrade/paywall UI present anywhere
  - Overlay blocks interaction during entitlement refresh and gating operations

### Backend Tests

- **Integration — limit enforcement**:
  - Free caregiver with 0 patients: create patient → 201 success
  - Free caregiver with 1 patient: create patient → 403 `PATIENT_LIMIT_EXCEEDED`
  - Premium caregiver: create patient beyond 1 → 201 success
- **Integration — grandfather**:
  - Free caregiver with >1 pre-existing linked patients: list/read patients → 200 (all visible); create patient → 403 `PATIENT_LIMIT_EXCEEDED`
- **Integration — RLS/concealment**:
  - Non-owning caregiver cannot observe or affect another caregiver's patients (existing behaviour preserved)
- **Integration — race condition**:
  - Two concurrent create-patient requests from a free caregiver with 0 patients: exactly 1 succeeds, 1 fails

## Documentation Updates

- Update quickstart documentation for 009:
  - How to test the free limit in sandbox (free vs premium entitlement states)
  - Where the paywall triggers (add patient action)
  - Grandfather rule explained (existing multi-patient users can still view all patients)
- If OpenAPI documentation is maintained, document the `PATIENT_LIMIT_EXCEEDED` error response on the `POST /api/patients` endpoint.
