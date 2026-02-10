# Tasks: Billing Foundation (Premium Unlock)

**Input**: Design documents from `/specs/008-billing-foundation/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/openapi.yaml

**Tests**: Tests are REQUIRED (spec Testing Requirements mandate unit, UI smoke, and integration tests).

**Organization**: Tasks are grouped into 4 phases (Tests-first, Backend, iOS, Docs) with user story labels for traceability.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 = Purchase/Restore/Entitlement update, US2 = FeatureGate + Paywall UX + Settings, US3 = Backend claim + entitlements API + security, US4 = Docs/App Review readiness

## Path Conventions

- **Backend**: `api/` (Next.js App Router + Prisma)
- **iOS**: `ios/MedicationApp/` (SwiftUI + StoreKit2)

## Test Commands

- **iOS**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- **Backend**: `cd api && npm test`

---

## Phase 1: Tests (Required First)

**Purpose**: Write all tests before implementation. Tests MUST fail initially and pass after their corresponding implementation phase completes.

### Backend Tests

- [x] T001 [P] [US3] Create integration tests for POST /api/iap/claim in `api/tests/integration/entitlement-claim.test.ts`
  - **Why**: Validates claim upserts entitlement, duplicate claims are idempotent, invalid productId is rejected, unauthenticated requests return 401 (FR-012, FR-013, FR-014, NFR-002)
  - **Files**: `api/tests/integration/entitlement-claim.test.ts`
  - **Done**: Test file exists with cases for: (1) valid claim creates ACTIVE entitlement, (2) duplicate originalTransactionId upserts without creating second record, (3) missing/invalid productId returns 422, (4) missing auth header returns 401, (5) patient token returns 401
  - **Test**: `cd api && npm test`

- [x] T002 [P] [US3] Create integration tests for GET /api/me/entitlements in `api/tests/integration/entitlement-claim.test.ts`
  - **Why**: Validates entitlement read returns premium boolean and record list (FR-015, FR-016)
  - **Files**: `api/tests/integration/entitlement-claim.test.ts` (append to same file)
  - **Done**: Test cases for: (1) caregiver with ACTIVE entitlement gets premium: true, (2) caregiver with no entitlements gets premium: false, (3) unauthenticated returns 401
  - **Test**: `cd api && npm test`

- [x] T003 [P] [US3] Create unit tests for IAP claim validator in `api/tests/unit/iapValidator.test.ts`
  - **Why**: Validates input sanitization before claim processing (FR-013)
  - **Files**: `api/tests/unit/iapValidator.test.ts`
  - **Done**: Test cases for: (1) valid payload passes, (2) missing productId fails, (3) wrong productId fails, (4) missing signedTransactionInfo fails, (5) empty strings fail
  - **Test**: `cd api && npm test`

- [x] T004 [P] [US3] Add entitlement fixture helper to test DB utilities in `api/tests/_db/testDb.ts`
  - **Why**: Integration tests need to seed and query CaregiverEntitlement records
  - **Files**: `api/tests/_db/testDb.ts`
  - **Done**: `createEntitlementFixture({ caregiverId, productId, originalTransactionId, ... })` function exported; uses Prisma client to upsert
  - **Test**: `cd api && npm test`

### iOS Unit Tests

- [x] T005 [P] [US1] Create unit tests for EntitlementStore state transitions in `ios/MedicationApp/Tests/Billing/EntitlementStoreTests.swift`
  - **Why**: Validates unknown -> free, unknown -> premium, premium -> free state transitions (FR-006, FR-017)
  - **Files**: `ios/MedicationApp/Tests/Billing/EntitlementStoreTests.swift`
  - **Done**: Test cases for: (1) initial state is unknown, (2) evaluation with no entitlement sets free, (3) evaluation with matching product sets premium, (4) re-evaluation after revocation sets free, (5) isPremium computed property returns correct boolean
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T006 [P] [US2] Create unit tests for FeatureGate mapping in `ios/MedicationApp/Tests/Billing/FeatureGateTests.swift`
  - **Why**: Validates gate -> tier mapping and isUnlocked logic for free/premium/pro (FR-009, FR-010, FR-011)
  - **Files**: `ios/MedicationApp/Tests/Billing/FeatureGateTests.swift`
  - **Done**: Test cases for: (1) multiplePatients requires premium and is unlocked when premium, (2) multiplePatients is locked when free, (3) escalationPush requires pro and is locked for both free and premium, (4) all 5 gates have correct requiredTier, (5) unknown state locks all premium gates
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### iOS UI Smoke Tests

- [x] T007 [P] [US2] Create UI smoke tests for caregiver Settings billing section and Paywall in `ios/MedicationApp/Tests/Billing/PaywallUITests.swift`
  - **Why**: Validates caregiver mode shows upgrade CTA, restore button, and Paywall can display (FR-002, FR-003, FR-004, FR-007, SC-001)
  - **Files**: `ios/MedicationApp/Tests/Billing/PaywallUITests.swift`
  - **Done**: Test cases for: (1) caregiver Settings contains "billing.premium.upgrade" accessible element, (2) caregiver Settings contains "billing.premium.restore" accessible element, (3) tapping upgrade opens Paywall sheet, (4) Paywall contains purchase and restore buttons, (5) overlay with accessibilityIdentifier "SchedulingRefreshOverlay" appears during refresh/purchase
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T008 [P] [US2] Create UI smoke tests verifying patient mode has zero billing UI in `ios/MedicationApp/Tests/Billing/PatientNoBillingUITests.swift`
  - **Why**: Validates patient mode never exposes billing entry points (FR-008, SC-003)
  - **Files**: `ios/MedicationApp/Tests/Billing/PatientNoBillingUITests.swift`
  - **Done**: Test cases for: (1) patient Settings does not contain "billing.premium.upgrade" element, (2) patient Settings does not contain "billing.premium.restore" element, (3) no billing-related accessibility identifiers found across patient tab bar screens
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

**Checkpoint**: All test files exist and define expected behavior. Tests fail because implementations do not exist yet.

---

## Phase 2: Backend — Schema + Endpoints (US3)

**Purpose**: Add CaregiverEntitlement table, claim/entitlements endpoints, and supporting layers. After this phase, backend integration tests (T001-T004) must pass.

### Schema & Migration

- [x] T009 [US3] Add EntitlementStatus enum and CaregiverEntitlement model to Prisma schema in `api/prisma/schema.prisma`
  - **Why**: Persistent storage for caregiver purchase entitlements with unique originalTransactionId (FR-014, FR-016, data-model.md)
  - **Files**: `api/prisma/schema.prisma`
  - **Done**: (1) `EntitlementStatus` enum with ACTIVE, REVOKED values added, (2) `CaregiverEntitlement` model added with fields: id, caregiverId, productId, status, originalTransactionId (@unique), transactionId, purchasedAt, environment, createdAt, updatedAt, (3) @@index([caregiverId]) present
  - **Test**: `cd api && npx prisma validate`

- [x] T010 [US3] Generate and apply Prisma migration for caregiver_entitlements in `api/prisma/migrations/`
  - **Why**: Creates the database table; Prisma v7.3 uses updated migration workflow (plan.md constraint note)
  - **Files**: `api/prisma/migrations/` (new migration directory)
  - **Done**: Migration SQL file exists creating `CaregiverEntitlement` table with unique constraint on `originalTransactionId` and index on `caregiverId`; `npx prisma migrate dev` succeeds
  - **Test**: `cd api && npx prisma migrate dev --name caregiver_entitlements`

### Repository

- [x] T011 [P] [US3] Implement entitlement repository in `api/src/repositories/entitlementRepo.ts`
  - **Why**: Data access layer for CaregiverEntitlement CRUD following existing repository pattern (patientRepo.ts, etc.)
  - **Files**: `api/src/repositories/entitlementRepo.ts`
  - **Done**: Exports: (1) `upsertEntitlement({ caregiverId, productId, originalTransactionId, transactionId, purchasedAt, environment })` — upserts by originalTransactionId, (2) `findEntitlementsByCaregiverId(caregiverId)` — returns all records for caregiver, (3) uses singleton Prisma client from `./prisma.ts`
  - **Test**: `cd api && npm test`

### Validator

- [x] T012 [P] [US3] Implement IAP claim validator in `api/src/validators/iapValidator.ts`
  - **Why**: Input validation before claim processing; rejects invalid/missing fields (FR-013)
  - **Files**: `api/src/validators/iapValidator.ts`
  - **Done**: Exports `validateClaimInput({ productId, signedTransactionInfo, environment? })` returning `{ errors: string[], productId, signedTransactionInfo, environment }`. Validates: (1) productId matches known premium product ID, (2) signedTransactionInfo is non-empty string, (3) environment defaults to "Production" if omitted
  - **Test**: `cd api && npm test`

### Service

- [x] T013 [US3] Implement entitlement service in `api/src/services/entitlementService.ts`
  - **Why**: Business logic for claim (decode JWS payload, extract fields, upsert) and read (aggregate premium boolean) (FR-012, FR-015, research decision 2)
  - **Files**: `api/src/services/entitlementService.ts`
  - **Done**: Exports: (1) `claimEntitlement(caregiverId, { productId, signedTransactionInfo, environment })` — decodes JWS payload to extract originalTransactionId/transactionId/purchasedAt, calls repo upsert, returns `{ premium, productId, status, updatedAt }`, (2) `getEntitlements(caregiverId)` — calls repo find, computes `premium = records.some(r => r.status === "ACTIVE")`, returns `{ premium, entitlements: [...] }`
  - **Test**: `cd api && npm test`

### API Routes

- [x] T014 [P] [US3] Implement POST /api/iap/claim route in `api/app/api/iap/claim/route.ts`
  - **Why**: Endpoint for iOS client to submit purchase claim (FR-012, contracts/openapi.yaml)
  - **Files**: `api/app/api/iap/claim/route.ts`
  - **Done**: (1) Exports `POST` handler, (2) calls `requireCaregiver(authHeader)`, (3) parses JSON body, (4) calls `validateClaimInput`, returns 422 on validation errors, (5) calls `entitlementService.claimEntitlement`, (6) returns 200 with `{ data: { premium, productId, status, updatedAt } }`, (7) errors handled via `errorResponse()`
  - **Test**: `cd api && npm test`

- [x] T015 [P] [US3] Implement GET /api/me/entitlements route in `api/app/api/me/entitlements/route.ts`
  - **Why**: Endpoint for iOS client to read premium status (FR-015, contracts/openapi.yaml)
  - **Files**: `api/app/api/me/entitlements/route.ts`
  - **Done**: (1) Exports `GET` handler, (2) calls `requireCaregiver(authHeader)`, (3) calls `entitlementService.getEntitlements`, (4) returns 200 with `{ data: { premium, entitlements: [...] } }`, (5) errors handled via `errorResponse()`
  - **Test**: `cd api && npm test`

### Verification

- [x] T016 [US3] Verify all backend tests pass (T001-T004) against implemented code
  - **Why**: Confirms backend implementation satisfies test expectations (SC-004, SC-005)
  - **Files**: `api/tests/integration/entitlement-claim.test.ts`, `api/tests/unit/iapValidator.test.ts`
  - **Done**: `cd api && npm test` exits 0 with all entitlement-related tests passing
  - **Test**: `cd api && npm test`

**Checkpoint**: Backend complete. POST /api/iap/claim and GET /api/me/entitlements work with caregiver auth. Integration and unit tests pass.

---

## Phase 3: iOS — StoreKit2 + Paywall + Settings + FeatureGate (US1 + US2)

**Purpose**: Implement StoreKit2 purchase/restore, EntitlementStore, FeatureGate, Paywall UI, Settings integration, lifecycle auto-refresh, and patient-mode guarantee. After this phase, iOS unit and UI smoke tests (T005-T008) must pass.

### Project Setup

- [x] T017 [US1] Add Features/Billing source group to Xcode project in `ios/MedicationApp/project.yml`
  - **Why**: XcodeGen must include new Billing directory in compilation; Tests/Billing must be visible to test target
  - **Files**: `ios/MedicationApp/project.yml`
  - **Done**: (1) `Features/Billing` path included in MedicationApp target sources (already covered by `Features` glob or added explicitly), (2) `Tests/Billing` path included in MedicationAppTests target sources (already covered by `Tests` glob or added explicitly), (3) `xcodegen generate` succeeds if project.yml changed
  - **Test**: `cd ios/MedicationApp && xcodegen generate` (if project.yml modified; otherwise verify existing globs cover new directories)

### Networking DTOs

- [x] T018 [P] [US1] Create entitlement DTOs in `ios/MedicationApp/Networking/DTOs/EntitlementDTO.swift`
  - **Why**: Typed request/response models for /api/iap/claim and /api/me/entitlements (contracts/openapi.yaml)
  - **Files**: `ios/MedicationApp/Networking/DTOs/EntitlementDTO.swift`
  - **Done**: (1) `ClaimRequest` struct with productId, signedTransactionInfo, environment (Codable), (2) `ClaimResponse` struct matching API shape `{ data: { premium, productId, status, updatedAt } }`, (3) `EntitlementsResponse` struct matching `{ data: { premium, entitlements: [{ productId, status, purchasedAt, originalTransactionId }] } }`, (4) all structs are Sendable
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Localization

- [x] T019 [P] [US2] Add billing localization keys to `ios/MedicationApp/Resources/Localizable.strings`
  - **Why**: All user-facing billing strings must be localized and not hardcoded (NFR-003, constitution III)
  - **Files**: `ios/MedicationApp/Resources/Localizable.strings`
  - **Done**: Keys added: `billing.premium.upgrade`, `billing.premium.restore`, `billing.premium.status.active`, `billing.premium.status.inactive`, `billing.paywall.title`, `billing.paywall.purchase`, `billing.paywall.restore`, `billing.paywall.close`, `billing.paywall.error`, `billing.paywall.retry`, `billing.paywall.description`, `billing.paywall.offline`; all with Japanese values
  - **Test**: Build succeeds; strings are referenced by implementation tasks

### Core Logic

- [x] T020 [US1] Implement EntitlementStore in `ios/MedicationApp/Features/Billing/EntitlementStore.swift`
  - **Why**: Central observable state for premium/free/unknown; drives all UI gating and lifecycle refresh (FR-005, FR-006, FR-007, research decision 1)
  - **Files**: `ios/MedicationApp/Features/Billing/EntitlementStore.swift`
  - **Done**: (1) `@Observable @MainActor` class, (2) `EntitlementState` enum (unknown/free/premium), (3) `state` published property defaulting to .unknown, (4) `isPremium: Bool` computed, (5) `refresh()` async method iterates `Transaction.currentEntitlements` for product ID match, (6) `isRefreshing: Bool` flag for overlay binding, (7) `purchase(product:)` async calls `product.purchase()` then refresh + server claim, (8) `restore()` async calls `AppStore.sync()` then refresh, (9) server claim via `APIClient.claimEntitlement()` (fire-and-forget with error tolerance), (10) concurrency guard prevents overlapping refreshes
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T021 [P] [US2] Implement FeatureGate in `ios/MedicationApp/Features/Billing/FeatureGate.swift`
  - **Why**: Centralized enum map of premium-gated capabilities (FR-009, FR-010, FR-011, data-model.md FeatureGate table)
  - **Files**: `ios/MedicationApp/Features/Billing/FeatureGate.swift`
  - **Done**: (1) `EntitlementTier` enum (free/premium/pro), (2) `FeatureGate` enum with cases: multiplePatients, extendedHistory, pdfExport, enhancedAlerts, escalationPush, (3) `requiredTier` computed property returning correct tier per gate, (4) `static func isUnlocked(_ gate: FeatureGate, for state: EntitlementState) -> Bool` — returns true only when state meets or exceeds required tier (premium unlocks premium gates; pro is always locked in this feature), (5) escalationPush always returns false
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### API Client Extension

- [x] T022 [US1] Add entitlement API methods to APIClient in `ios/MedicationApp/Networking/APIClient.swift`
  - **Why**: iOS client needs to call POST /api/iap/claim and GET /api/me/entitlements
  - **Files**: `ios/MedicationApp/Networking/APIClient.swift`
  - **Done**: (1) `claimEntitlement(_ request: ClaimRequest) async throws` — POST to /api/iap/claim with JSON body, returns ClaimResponse, (2) `getEntitlements() async throws -> EntitlementsResponse` — GET /api/me/entitlements, (3) both use existing `tokenForCurrentMode()` for auth header, (4) error handling via existing `mapErrorIfNeeded`
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Paywall UI

- [x] T023 [US2] Implement PaywallViewModel in `ios/MedicationApp/Features/Billing/PaywallViewModel.swift`
  - **Why**: Drives Paywall view state: product loading, purchase, restore, error/retry (FR-002, FR-003, FR-007)
  - **Files**: `ios/MedicationApp/Features/Billing/PaywallViewModel.swift`
  - **Done**: (1) `@Observable @MainActor` class, (2) `product: Product?` loaded via `Product.products(for:)`, (3) `loadError: String?` for product fetch failure, (4) `purchase()` async delegates to EntitlementStore.purchase, (5) `restore()` async delegates to EntitlementStore.restore, (6) `retryLoad()` re-fetches product, (7) references EntitlementStore.isRefreshing for overlay state
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T024 [US2] Implement PaywallView in `ios/MedicationApp/Features/Billing/PaywallView.swift`
  - **Why**: User-facing paywall with price, purchase CTA, restore, error/retry, and blocking overlay (FR-002, FR-003, FR-007, FR-018, NFR-003)
  - **Files**: `ios/MedicationApp/Features/Billing/PaywallView.swift`
  - **Done**: (1) Displays product.displayPrice when loaded, (2) premium benefits description using localized string, (3) "購入する" button calls viewModel.purchase(), (4) "購入を復元" button calls viewModel.restore(), (5) "閉じる" dismiss button, (6) error state with retry button when product load fails, (7) `SchedulingRefreshOverlay` shown when entitlementStore.isRefreshing, (8) all strings from Localizable.strings, (9) VoiceOver-friendly accessibility labels
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Settings Integration

- [x] T025 [US2] Add Premium section to caregiver Settings in `ios/MedicationApp/Features/PatientManagement/PatientManagementView.swift`
  - **Why**: Caregiver users need persistent access to upgrade and restore from Settings (FR-002, FR-003, FR-004, SC-001)
  - **Files**: `ios/MedicationApp/Features/PatientManagement/PatientManagementView.swift`
  - **Done**: (1) New Section with header "プレミアム", (2) when not premium: "プレミアムにアップグレード" button opens PaywallView sheet, (3) "購入を復元" button triggers EntitlementStore.restore(), (4) premium status label showing "Premium: 有効" or "Premium: 無効", (5) when premium: upgrade CTA hidden or de-emphasized, restore still visible, (6) section only rendered when `sessionStore.mode == .caregiver`, (7) accessibility identifiers for UI tests: "billing.premium.upgrade", "billing.premium.restore"
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Patient Mode Guarantee

- [x] T026 [US2] Audit patient mode views to confirm zero billing entry points in `ios/MedicationApp/Features/PatientReadOnly/PatientReadOnlyView.swift`
  - **Why**: Patient mode must never show billing UI (FR-008, SC-003)
  - **Files**: `ios/MedicationApp/Features/PatientReadOnly/PatientReadOnlyView.swift`
  - **Done**: (1) PatientReadOnlyView and PatientSettingsView contain no references to EntitlementStore, PaywallView, or billing localization keys, (2) no billing accessibility identifiers present, (3) UI test T008 passes
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Lifecycle Auto-Refresh

- [x] T027 [US1] Hook EntitlementStore.refresh() on app launch and foreground in `ios/MedicationApp/App/RootView.swift`
  - **Why**: Entitlement state must auto-refresh on app launch and foreground return (FR-005, SC-006)
  - **Files**: `ios/MedicationApp/App/RootView.swift`
  - **Done**: (1) EntitlementStore injected via @Environment or @State, (2) `.task { }` calls entitlementStore.refresh() on appear (covers launch), (3) `.onChange(of: scenePhase)` calls entitlementStore.refresh() when transitioning to .active (covers foreground), (4) refresh only runs when mode is .caregiver
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

- [x] T028 [US1] Hook EntitlementStore.refresh() after caregiver login in `ios/MedicationApp/Shared/SessionStore.swift` or login completion site
  - **Why**: Post-login refresh ensures newly authenticated caregiver gets correct premium state (FR-005, SC-006)
  - **Files**: `ios/MedicationApp/Shared/SessionStore.swift` (or `ios/MedicationApp/Features/Auth/` login callback)
  - **Done**: (1) After `saveCaregiverToken()` completes, entitlement refresh is triggered, (2) mechanism: either direct call, NotificationCenter post, or callback closure, (3) only triggers when mode == .caregiver
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Environment Wiring

- [x] T029 [US1] Wire EntitlementStore into SwiftUI environment in `ios/MedicationApp/App/MedicationApp.swift`
  - **Why**: EntitlementStore must be accessible throughout the view hierarchy via @Environment
  - **Files**: `ios/MedicationApp/App/MedicationApp.swift`
  - **Done**: (1) EntitlementStore created as @State or @StateObject at app root, (2) injected into view hierarchy via .environment(), (3) accessible from RootView, PatientManagementView, PaywallView, and any future view needing gate checks
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

### Verification

- [x] T030 [US1] Verify all iOS tests pass (T005-T008) against implemented code
  - **Why**: Confirms iOS implementation satisfies test expectations (SC-001, SC-002, SC-003, SC-006)
  - **Files**: `ios/MedicationApp/Tests/Billing/`
  - **Done**: `xcodebuild test` exits 0 with all Billing test files passing
  - **Test**: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`

**Checkpoint**: Full iOS and Backend implementations complete. All tests (T001-T008) pass. Purchase, restore, lifecycle refresh, FeatureGate, patient-mode guarantee all functional.

---

## Phase 4: Docs / App Review Readiness (US4)

**Purpose**: Update documentation artifacts and add App Review compliance notes. All docs in specs/008-billing-foundation/ were pre-generated during planning; this phase verifies and finalizes them for post-implementation accuracy.

- [x] T031 [P] [US4] Finalize quickstart.md with verified sandbox test steps in `specs/008-billing-foundation/quickstart.md`
  - **Why**: Sandbox purchase/restore procedure must be tested against actual implementation; App Review notes must reference real UI locations (NFR-006)
  - **Files**: `specs/008-billing-foundation/quickstart.md`
  - **Done**: (1) Sandbox purchase steps verified against implemented Paywall, (2) restore steps verified, (3) App Review notes reference actual Settings section and Paywall locations, (4) test commands confirmed working
  - **Test**: Manual walkthrough of quickstart steps

- [x] T032 [P] [US4] Verify contracts/openapi.yaml matches implemented API in `specs/008-billing-foundation/contracts/openapi.yaml`
  - **Why**: OpenAPI spec must match actual request/response shapes after implementation (NFR-006)
  - **Files**: `specs/008-billing-foundation/contracts/openapi.yaml`
  - **Done**: (1) POST /iap/claim request/response schemas match route handler, (2) GET /me/entitlements response schema matches route handler, (3) error response codes match implementation
  - **Test**: `cd api && npm test` (contract-level validation covered by integration tests)

- [x] T033 [P] [US4] Verify data-model.md matches implemented schema in `specs/008-billing-foundation/data-model.md`
  - **Why**: Data model doc must reflect actual Prisma schema fields and FeatureGate enum (NFR-006)
  - **Files**: `specs/008-billing-foundation/data-model.md`
  - **Done**: (1) CaregiverEntitlement fields match schema.prisma, (2) EntitlementStatus enum values match, (3) FeatureGate table matches Swift enum cases, (4) validation rules match validator implementation
  - **Test**: Diff data-model.md against `api/prisma/schema.prisma` and `ios/MedicationApp/Features/Billing/FeatureGate.swift`

- [x] T034 [P] [US4] Add Prisma v7.3 setup note to quickstart or plan in `specs/008-billing-foundation/quickstart.md`
  - **Why**: Prisma v7.3 has different initialization than prior versions; developers must know (plan.md constraint)
  - **Files**: `specs/008-billing-foundation/quickstart.md`
  - **Done**: Note added explaining Prisma v7.3 uses `@prisma/adapter-pg` with `pg.Pool` (matching existing `api/src/repositories/prisma.ts` pattern), and that `prisma migrate dev` is the migration command
  - **Test**: N/A (doc update)

**Checkpoint**: All documentation finalized and verified against implementation.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Tests)**: No dependencies — start immediately
- **Phase 2 (Backend)**: Depends on T004 (fixture helper) being available; implementation makes T001-T003 pass
- **Phase 3 (iOS)**: Depends on Phase 2 completion (API endpoints must exist for client calls); implementation makes T005-T008 pass
- **Phase 4 (Docs)**: Depends on Phase 2 + Phase 3 completion (docs verified against implementation)

### User Story Dependencies

- **US1 (Purchase/Restore)**: Requires US3 backend endpoints for server claim; core EntitlementStore + lifecycle
- **US2 (FeatureGate + Paywall + Settings)**: Requires US1 EntitlementStore; FeatureGate is independent
- **US3 (Backend)**: Independent — can be built and tested without iOS
- **US4 (Docs)**: Depends on all other stories being complete

### Within Each Phase

- Tasks marked [P] can run in parallel (different files, no shared state)
- Unmarked tasks depend on prior tasks in same phase completing

### Parallel Opportunities

```text
# Phase 1: All test files can be written in parallel
T001 | T002 | T003 | T004 | T005 | T006 | T007 | T008

# Phase 2: Repository and Validator in parallel after schema
T009 -> T010 -> (T011 | T012) -> T013 -> (T014 | T015) -> T016

# Phase 3: DTOs, Localizable, and FeatureGate in parallel
T017 -> (T018 | T019 | T021) -> T020 -> T022 -> T023 -> T024 -> T025 -> T026
T027 and T028 after T020; T029 after T020
T030 after all Phase 3 tasks

# Phase 4: All docs in parallel
T031 | T032 | T033 | T034
```

---

## Implementation Strategy

### MVP First (Phase 1 + Phase 2)

1. Write all tests (Phase 1) — establish behavioral contract
2. Implement backend (Phase 2) — backend tests pass
3. **STOP and VALIDATE**: Run `cd api && npm test` — all green

### Full Feature (Phase 3 + Phase 4)

4. Implement iOS (Phase 3) — iOS tests pass
5. **STOP and VALIDATE**: Run full iOS test suite — all green
6. Finalize docs (Phase 4)
7. **FINAL VALIDATION**: Both test suites green, quickstart walkthrough passes

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 34 |
| Phase 1 (Tests) | 8 |
| Phase 2 (Backend) | 8 |
| Phase 3 (iOS) | 14 |
| Phase 4 (Docs) | 4 |
| Parallel opportunities | 8 (Phase 1) + 2 (Phase 2) + 3 (Phase 3) + 4 (Phase 4) |
| US1 tasks | 8 |
| US2 tasks | 9 |
| US3 tasks | 10 |
| US4 tasks | 4 |
| Non-goals excluded | Premium feature implementations, escalation push, Pro plan |

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Tests MUST fail before implementation and pass after
- Commit after each task or logical group
- Stop at any checkpoint to validate independently
- Escalation push (escalationPush gate) is defined but always locked — no implementation in 008
- Premium feature bodies (PDF, history, alerts) are NOT implemented in 008 — only gates are defined
