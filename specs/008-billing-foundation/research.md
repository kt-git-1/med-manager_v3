# Research: Billing Foundation (Premium Unlock)

## Decision 1: StoreKit2 Transaction.currentEntitlements for local premium state

- **Decision**: Use `Transaction.currentEntitlements` to determine premium status on the device without a server round-trip. Re-evaluate on app launch, foreground return, and post-login.
- **Rationale**: StoreKit2 `currentEntitlements` is the Apple-recommended way to check Non-Consumable ownership locally. It provides immediate UI responsiveness without network dependency and works offline. Lifecycle-triggered re-evaluation ensures revocations and new purchases are picked up promptly.
- **Alternatives considered**:
  - Always query the server for entitlement status (adds latency, fails offline, degrades UX).
  - Use `Transaction.updates` stream only (misses purchases made on other devices until stream fires; `currentEntitlements` is more reliable for initial state).

## Decision 2: Server-side JWS verification scope in MVP

- **Decision**: On claim, validate the JWS structure, decode the payload to extract `originalTransactionId`, `productId`, and `environment`, and verify that `productId` matches the known Premium Unlock product ID. Full Apple root certificate chain verification is deferred to a later iteration.
- **Rationale**: Full JWKS/root-cert validation requires fetching and caching Apple's certificate chain, which adds complexity and an external dependency in CI. For MVP, payload-level validation with `originalTransactionId` uniqueness and `productId` matching provides sufficient protection against casual abuse. The `signedTransactionInfo` is still stored for future verification upgrades. A note is added to the spec for future App Store Server Notifications integration.
- **Alternatives considered**:
  - Full Apple root certificate chain verification from day one (significant implementation cost, external call in claim path, blocked by CI no-external-calls rule without mocking infrastructure).
  - No server-side verification at all (unacceptable; allows trivial fake claims).

## Decision 3: Single CaregiverEntitlement table keyed on originalTransactionId

- **Decision**: Store entitlements in a single `CaregiverEntitlement` table with `originalTransactionId` as a unique key. Use upsert on claim to prevent duplicates.
- **Rationale**: Non-Consumable purchases have a stable `originalTransactionId` across restore and re-purchase. A unique constraint on this field guarantees idempotent claims. One table is sufficient for the single-product MVP and straightforward to extend for future products.
- **Alternatives considered**:
  - Separate tables per product type (over-engineering for one product; adds join complexity).
  - Store entitlement as a boolean flag on a caregiver profile table (loses audit trail, originalTransactionId tracking, and multi-product extensibility).

## Decision 4: EntitlementStatus enum (ACTIVE / REVOKED)

- **Decision**: Use a Prisma enum `EntitlementStatus` with values `ACTIVE` and `REVOKED` on the `CaregiverEntitlement` record.
- **Rationale**: Matches spec FR-016 requirement for distinguishing active vs revoked access. Re-evaluation based revocation (spec FR-017) sets status to `REVOKED` when a refund or revocation is detected. The enum is extensible for future states (e.g., `EXPIRED` for subscriptions) without schema migration.
- **Alternatives considered**:
  - Boolean `isActive` flag (less expressive, harder to extend for subscription states).
  - Soft-delete with `revokedAt` timestamp only (loses explicit state for queries and reporting).

## Decision 5: FeatureGate as a pure Swift enum map

- **Decision**: Implement `FeatureGate` as a Swift enum with a `requiredTier` property. Gate checks are pure functions that compare the user's `EntitlementState` against the gate's required tier. No server round-trip for gate evaluation.
- **Rationale**: All gate definitions are known at compile time for this feature. Pure enum ensures type safety, exhaustive switch coverage, and zero latency for UI gating. Server-side gates are additive for future server-backed features (e.g., extended history limits enforced by the API), but the iOS gate map does not need server consultation.
- **Alternatives considered**:
  - Remote config / server-fetched gate map (adds latency, network dependency, and offline failure mode for UI gating).
  - Dictionary-based gate map (loses compile-time exhaustiveness and type safety).

## Decision 6: Reuse SchedulingRefreshOverlay for billing blocking overlay

- **Decision**: Reuse the existing `SchedulingRefreshOverlay` component for all billing-related blocking operations (product fetch, purchase, restore, entitlement refresh).
- **Rationale**: The existing overlay matches the UX requirement for a full-screen "更新中" state with app icon, progress indicator, and semi-transparent background. It already has an accessibility identifier and consistent styling. Reusing it avoids visual inconsistency and reduces code duplication.
- **Alternatives considered**:
  - New billing-specific overlay (unnecessary divergence from established UX pattern).
  - Inline progress indicators without blocking (violates spec FR-007 requirement for operation-blocking overlay).
