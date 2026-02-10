# Feature Specification: Billing Foundation (Premium Unlock)

**Feature Branch**: `008-billing-foundation`  
**Created**: 2026-02-10  
**Status**: Draft  
**Input**: User description: "Feature ID: 008 billing foundation for premium unlock, entitlement gates, restore flow, and server-side claim/entitlements support."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Purchase and Unlock Premium in Caregiver Mode (Priority: P1)

As a caregiver user, I can purchase a one-time Premium Unlock and immediately see premium-only family-mode capabilities become available.

**Why this priority**: This is the core monetization path and enables all premium gating decisions.

**Independent Test**: In caregiver mode, open settings, purchase Premium Unlock, and confirm premium status changes to active and premium gates evaluate as unlocked.

**Acceptance Scenarios**:

1. **Given** a caregiver user without premium, **When** the user opens settings, views paywall details, and completes purchase, **Then** the account state updates to premium and premium-gated items are marked available.
2. **Given** a caregiver user with premium already active, **When** the user revisits settings, **Then** the status is shown as active and upgrade CTA is de-emphasized or hidden.
3. **Given** product details cannot be loaded, **When** the user opens paywall, **Then** the user sees a clear error message and a retry action.

---

### User Story 2 - Restore and Auto-Refresh Purchase State (Priority: P2)

As a caregiver user who already paid, I can restore purchases from settings and have purchase status refresh automatically at key app lifecycle points.

**Why this priority**: Required for compliance and prevents paid users from losing access after reinstall, account change, or device change.

**Independent Test**: Use a previously purchased account, run restore from settings, and verify premium returns; also verify refresh occurs on app launch, foreground return, and post-login.

**Acceptance Scenarios**:

1. **Given** a caregiver user with a prior purchase, **When** the user selects restore purchases, **Then** purchase state refreshes and premium becomes active without buying again.
2. **Given** the app launches, returns to foreground, or finishes caregiver login, **When** entitlement refresh runs, **Then** the displayed premium status is updated automatically.
3. **Given** refresh is in progress, **When** the user attempts interaction, **Then** a full-screen updating overlay blocks actions until refresh completes.

---

### User Story 3 - Keep Patient Mode Free and Hide Billing Entry (Priority: P3)

As a patient-mode user, I can continue using the app for free and do not encounter billing prompts or paywall entry points.

**Why this priority**: Preserves core app accessibility and enforces product policy that patient mode remains permanently free.

**Independent Test**: Enter patient mode and navigate visible tabs; verify no upgrade, purchase, restore, or paywall entry is presented.

**Acceptance Scenarios**:

1. **Given** the app is in patient mode, **When** the user opens settings and other main screens, **Then** no billing-related CTA or section is shown.
2. **Given** premium status changes while patient mode is active, **When** the UI refreshes, **Then** patient mode remains free and unchanged.

---

### User Story 4 - Enforce Entitlements on Server-Backed Premium Capabilities (Priority: P3)

As a product owner, I can rely on server-recognized premium entitlements so future server-backed premium features can reject unauthorized use.

**Why this priority**: Prevents abuse where local UI state alone could otherwise unlock resource-consuming capabilities.

**Independent Test**: Submit valid and invalid purchase claims as an authenticated caregiver, then read entitlement profile and verify premium status and records are accurate.

**Acceptance Scenarios**:

1. **Given** an authenticated caregiver and a valid purchase transaction proof, **When** the app submits a claim, **Then** server entitlement is created or updated and premium returns true.
2. **Given** unauthenticated or malformed claim input, **When** claim is submitted, **Then** the request is rejected and no entitlement is granted.
3. **Given** duplicate claim submissions for the same original transaction, **When** server processes claims, **Then** only one entitlement identity is retained and duplicates are prevented.

### Edge Cases

- User initiates purchase or restore while offline; system shows failure reason and retry path without changing entitlement state.
- Product catalog temporarily unavailable; paywall remains closable and cannot start purchase until data is available.
- Lifecycle refresh triggers while another refresh is already running; system resolves to one effective in-progress state and avoids conflicting status outputs.
- Caregiver account switches after a previous user had premium; refreshed entitlement reflects the newly authenticated caregiver only.
- Entitlement source mismatch (device says premium, server not yet updated); UI can reflect immediate local state while server-backed gates remain tied to server entitlement until claim completes.
- Refunded or revoked purchase is encountered on later re-evaluation; premium state drops from active to inactive on the next evaluation cycle.

### Out of Scope

- Implementation of premium feature bodies (for example: PDF export behavior, long-history rendering behavior, enhanced alert behavior).
- Escalation push notifications based on missed records after scheduled time plus delay.
- Introduction of a separate Pro commercial plan; only premium/pro boundary definition is included here.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST offer exactly one one-time Premium Unlock product for caregiver mode monetization in this feature.
- **FR-002**: System MUST allow caregiver users to purchase Premium Unlock from a paywall entry in caregiver settings.
- **FR-003**: System MUST provide a restore-purchases action in caregiver settings and in paywall.
- **FR-004**: System MUST display the current premium status in caregiver settings as active or inactive.
- **FR-005**: System MUST automatically refresh entitlement state on app launch, app foreground, and completion of caregiver login.
- **FR-006**: System MUST maintain entitlement states of unknown, free, and premium, and use these states for gating decisions.
- **FR-007**: System MUST apply a full-screen updating overlay that blocks interaction during product retrieval, purchase, restore, and entitlement refresh flows.
- **FR-008**: System MUST keep patient mode free and MUST NOT show any purchase or restore entry points in patient mode.
- **FR-009**: System MUST define and use a centralized feature gate map for premium-controlled capabilities.
- **FR-010**: System MUST classify these capabilities as Premium-included in gate definitions: additional patients beyond the first, long-term history access beyond free limits, PDF export, and enhanced caregiver alerts.
- **FR-011**: System MUST classify escalation push notifications (missed-record escalation after scheduled time plus delay) as excluded from Premium and reserved for a future Pro offering.
- **FR-012**: System MUST allow authenticated caregiver clients to submit purchase claim data so server-side entitlement can be created or updated.
- **FR-013**: System MUST reject purchase claims that are unauthenticated, invalid, or inconsistent with the Premium Unlock product identity.
- **FR-014**: System MUST prevent duplicate entitlement registration for the same original purchase identity.
- **FR-015**: System MUST provide an authenticated endpoint for the caregiver account to read server-recognized entitlement status and records.
- **FR-016**: System MUST support entitlement status values that distinguish active access from revoked access in server records.
- **FR-017**: System MUST support re-evaluation behavior where revoked or refunded purchases can remove premium access on a subsequent refresh cycle.
- **FR-018**: System MUST describe premium value clearly in paywall messaging and make clear that patient mode remains usable without payment.

### Non-Functional Requirements *(mandatory)*

- **NFR-001 (Performance)**: 95% of entitlement refresh operations complete in under 3 seconds under normal network conditions, and status presentation updates in the same user session.
- **NFR-002 (Security/Privacy)**: Premium claim and entitlement read operations require authenticated caregiver identity, and unauthorized requests are consistently denied.
- **NFR-003 (UX/Accessibility)**: Updating overlay, errors, and action labels are understandable in plain language, localizable, and prevent ambiguous user state during blocking operations.
- **NFR-004 (Reliability)**: Claim and entitlement persistence behavior is idempotent for repeated submissions of the same purchase identity.
- **NFR-005 (Compliance)**: Restore purchases is always accessible from caregiver settings, and premium benefit messaging remains accurate and non-misleading.
- **NFR-006 (Operations/Documentation)**: Product, entitlement-state, and gate definitions are documented so later features can reuse consistent gate decisions without redefining billing rules.

### Key Entities *(include if feature involves data)*

- **Premium Product**: The single one-time purchasable offer identified as Premium Unlock and associated display content.
- **Entitlement State**: User access state used by clients (`unknown`, `free`, `premium`) and server records (`active`, `revoked`) for gating.
- **Feature Gate**: Central definition entry mapping capability keys to required access tier and mode restrictions.
- **Caregiver Entitlement Record**: Server-side record linking a caregiver account to a validated purchase identity, status, purchase time, environment, and update timestamps.
- **Purchase Claim Payload**: Authenticated caregiver submission containing product identity and signed transaction proof used for entitlement upsert.

### Assumptions

- Only caregiver accounts can hold and use Premium entitlement in this phase.
- There is one Premium Unlock product in scope; multi-product bundles and subscription plans are out of scope.
- Local entitlement can be used for immediate UI responsiveness, while server entitlement governs future server-backed premium operations.
- Revoke and refund handling is based on periodic re-evaluation in MVP; event-driven revocation updates are deferred to a later feature.
- Premium-gated feature implementations are delivered in later features; this feature defines gate contracts and entitlement flow only.

### Testing Requirements

- Unit-level validation is required for entitlement state transitions (`unknown`, `free`, `premium`) and feature-gate decisions for free vs premium.
- UI smoke validation is required to confirm caregiver purchase/restore entry points, paywall rendering with retry on product-load failure, and interaction-blocking update overlay behavior.
- UI smoke validation is required to confirm patient mode never exposes billing entry points.
- Integration validation is required to confirm entitlement claim upsert behavior, entitlement read behavior, duplicate-claim handling, and rejection of unauthorized or malformed claims.
- Where feasible, isolated validation of transaction-proof verification outcomes is required using controlled test inputs.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of caregiver users can access both purchase and restore actions from settings in acceptance testing.
- **SC-002**: At least 95% of successful purchase or restore attempts show updated premium status within 5 seconds.
- **SC-003**: In patient mode validation runs, 0 billing entry points are visible across primary navigation surfaces.
- **SC-004**: In backend integration tests, 100% of valid claim submissions result in premium-active entitlement state and 100% of invalid/unauthenticated claims are rejected.
- **SC-005**: In duplicate-claim tests, repeated submissions for the same original purchase identity create no additional entitlement identities.
- **SC-006**: In lifecycle tests (launch, foreground, post-login), entitlement auto-refresh runs on all three events and status remains consistent with latest evaluated state.
