# Tasks: Family Linking (002)

**Input**: Design documents from `/specs/002-family-linking/`  
**Prerequisites**: plan.md, spec.md, data-model.md, contracts/  
**Tests**: Required (contract/integration/unit + iOS unit/UI smoke)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and shared scaffolding

- [x] T001 Update E2E checklist for 002 flows in `specs/002-family-linking/checklists/e2e.md` (Why: capture independent tests early | Files: `specs/002-family-linking/checklists/e2e.md` | Done: E2E checklist includes all required scenarios | Tests: N/A)
- [x] T002 [P] Confirm Prisma v7.3 config and serverless-safe client pattern in `api/prisma.config.ts`, `api/src/repositories/prisma.ts` (Why: avoid Vercel connection issues | Files: `api/prisma.config.ts`, `api/src/repositories/prisma.ts` | Done: singleton pattern and v7.3 config documented | Tests: N/A)
- [x] T003 [P] Add shared constants for linking rules in `api/src/services/linkingConstants.ts` (Why: centralize code length, expiry, lockout | Files: `api/src/services/linkingConstants.ts` | Done: constants for code length=6, expiry=15m, attempts=5, lockout=5m | Tests: N/A)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, auth boundaries, validation, and error mapping

- [x] T004 Add Patient, CaregiverPatientLink, LinkingCode, PatientSession to `api/prisma/schema.prisma` with indexes/unique (Why: data model foundation | Files: `api/prisma/schema.prisma` | Done: models include PatientSession.expiresAt (optional) and unique(patientId) + indexes match plan | Tests: N/A)
- [x] T005 Run migration `family_linking` and commit SQL in `api/prisma/migrations/*` (Why: persist schema | Files: `api/prisma/migrations/` | Done: migration created and applied locally | Tests: N/A)
- [x] T006 [P] Add validators for patient create + link code input in `api/src/validators/patient.ts` (Why: enforce 422 rules | Files: `api/src/validators/patient.ts` | Done: trims, 6-digit check, displayName non-blank + max length | Tests: `npm test -- api/tests/unit/validators.test.ts`)
- [x] T007 [P] Extend error mapping for 401/403/404/409/422/429 in `api/src/middleware/error.ts` (Why: domain policy compliance | Files: `api/src/middleware/error.ts` | Done: consistent responses for new endpoints | Tests: `npm test -- api/tests/unit/error-mapper.test.ts`)
- [x] T008 [P] Define displayName max length (50) in spec+validator constants (Why: requirement to decide max length | Files: `specs/002-family-linking/spec.md`, `api/src/validators/patient.ts` | Done: max length documented and enforced | Tests: `npm test -- api/tests/unit/validators.test.ts`)
- [x] T009 Implement real patientSessionToken verifier in `api/src/auth/patientSessionVerifier.ts` (Why: replace 001 stub with DB-backed validation | Files: `api/src/auth/patientSessionVerifier.ts`, `api/src/repositories/patientSessionRepo.ts` | Done: tokenHash lookup + revokedAt check | Tests: `npm test -- api/tests/unit/patient-session-stub.test.ts`)
- [x] T010 Update auth middleware to use real verifier in `api/src/middleware/auth.ts` (Why: enforce patient scope for read-only APIs | Files: `api/src/middleware/auth.ts` | Done: patient requests validated via PatientSession | Tests: `npm test -- api/tests/unit/auth-middleware.test.ts`)

**Checkpoint**: Schema + auth boundaries in place; user story work can begin

---

## Phase 3: User Story 1 - 家族が患者を作成し連携コードを発行 (Priority: P1) 🎯 MVP

**Goal**: Caregiver can create patient and issue a one-time linking code.

**Independent Test**: ログイン → 患者作成 → 患者一覧に表示 → 連携コード発行

### Tests for User Story 1 (write first)

- [x] T011 [P] [US1] Contract tests for `/patients` and `/patients/{id}/linking-codes` in `api/tests/contract/patients.contract.test.ts` (Why: lock API shape | Files: `api/tests/contract/patients.contract.test.ts` | Done: request/response/422/404 cases covered | Tests: `npm test -- api/tests/contract/patients.contract.test.ts`)
- [x] T012 [P] [US1] Integration tests for patient creation + code issuance in `api/tests/integration/patient-linking.test.ts` (Why: verify DB workflow | Files: `api/tests/integration/patient-linking.test.ts` | Done: create/list/issue flow passes | Tests: `npm test -- api/tests/integration/patient-linking.test.ts`)

### Auth (Caregiver) for User Story 1

- [x] T013 [P] [US1] Implement Supabase JWT verification in `api/src/auth/supabaseJwt.ts` (Why: real caregiver auth) | Files: `api/src/auth/supabaseJwt.ts` | Done: verifies JWT, extracts caregiverUserId | Tests: `npm test -- api/tests/unit/auth-middleware.test.ts`
- [x] T014 [P] [US1] Add unit test for Supabase JWT verification in `api/tests/unit/supabase-jwt.test.ts` (Why: guard auth logic) | Files: `api/tests/unit/supabase-jwt.test.ts` | Done: valid/invalid token cases | Tests: `npm test -- api/tests/unit/supabase-jwt.test.ts`

### Implementation for User Story 1

- [x] T015 [P] [US1] Implement repositories for Patient, CaregiverPatientLink, LinkingCode in `api/src/repositories/*` (Why: data access layer | Files: `api/src/repositories/patientRepo.ts`, `api/src/repositories/caregiverPatientLinkRepo.ts`, `api/src/repositories/linkingCodeRepo.ts` | Done: CRUD helpers with scoped queries | Tests: `npm test -- api/tests/unit/prisma-client.test.ts`)
- [x] T016 [US1] Implement linkingService issue flow in `api/src/services/linkingService.ts` (Why: enforce one-time code, reissue invalidation | Files: `api/src/services/linkingService.ts` | Done: code hashed, old code invalidated, expiresAt set | Tests: `npm test -- api/tests/integration/patient-linking.test.ts`)
- [x] T017 [US1] Add caregiver endpoints in `api/app/api/patients/route.ts` (POST/GET) (Why: expose create/list | Files: `api/app/api/patients/route.ts` | Done: 201 + 200 + 422 + 404/403 mapping | Tests: `npm test -- api/tests/contract/patients.contract.test.ts`)
- [x] T018 [US1] Add linking-code endpoint in `api/app/api/patients/[patientId]/linking-codes/route.ts` (Why: issue code | Files: `api/app/api/patients/[patientId]/linking-codes/route.ts` | Done: 201 with expiresAt, 404 conceal | Tests: `npm test -- api/tests/contract/patients.contract.test.ts`)

---

## Phase 4: User Story 2 - 患者が連携コードでセッション取得・閲覧 (Priority: P2)

**Goal**: Patient exchanges code for token and reads medication/schedule.

**Independent Test**: 患者がコード入力→token取得→001の閲覧APIが成功

### Tests for User Story 2 (write first)

- [x] T019 [P] [US2] Contract tests for `/patient/link` and `/patient/session/refresh` in `api/tests/contract/patient-session.contract.test.ts` (Why: lock token exchange/refresh shape | Files: `api/tests/contract/patient-session.contract.test.ts` | Done: 200/404/422/429/401/403 covered | Tests: `npm test -- api/tests/contract/patient-session.contract.test.ts`)
- [x] T020 [P] [US2] Integration tests for code exchange + refresh rotation in `api/tests/integration/patient-session.test.ts` (Why: ensure one-time code + token rotation | Files: `api/tests/integration/patient-session.test.ts` | Done: refresh invalidates old token | Tests: `npm test -- api/tests/integration/patient-session.test.ts`)
- [x] T021 [P] [US2] Regression test: 001 read-only APIs accept new patientSessionToken in `api/tests/integration/patient-readonly.test.ts` (Why: stub replacement coverage | Files: `api/tests/integration/patient-readonly.test.ts` | Done: medications/schedule read with new token | Tests: `npm test -- api/tests/integration/patient-readonly.test.ts`)

### Implementation for User Story 2

- [x] T022 [P] [US2] Implement PatientSession repository in `api/src/repositories/patientSessionRepo.ts` (Why: token storage/lookup | Files: `api/src/repositories/patientSessionRepo.ts` | Done: tokenHash create/find/revoke helpers | Tests: `npm test -- api/tests/unit/prisma-client.test.ts`)
- [x] T023 [US2] Implement patientSessionService (exchange/refresh/rotate) in `api/src/services/patientSessionService.ts` (Why: token rotation contract | Files: `api/src/services/patientSessionService.ts` | Done: refresh rotates and invalidates old | Tests: `npm test -- api/tests/integration/patient-session.test.ts`)
- [x] T024 [US2] Implement patient link endpoint in `api/app/api/patient/link/route.ts` (Why: code exchange) | Files: `api/app/api/patient/link/route.ts` | Done: 200 + 404/422/429 behaviors | Tests: `npm test -- api/tests/contract/patient-session.contract.test.ts`
- [x] T025 [US2] Implement refresh endpoint in `api/app/api/patient/session/refresh/route.ts` (Why: token rotation) | Files: `api/app/api/patient/session/refresh/route.ts` | Done: rotates token, 401/403 on invalid | Tests: `npm test -- api/tests/contract/patient-session.contract.test.ts`
- [x] T026 [US2] Add lockout tracking per patientId in `api/src/services/linkingService.ts` (Why: 5 attempts → 5m lockout) | Files: `api/src/services/linkingService.ts` | Done: lockout enforced and returns 429 | Tests: `npm test -- api/tests/integration/patient-session.test.ts`

---

## Phase 5: User Story 3 - 家族がリンク解除して患者セッションを失効 (Priority: P3)

**Goal**: Caregiver can revoke link and invalidate all patient sessions.

**Independent Test**: 解除後に閲覧/refreshが失敗

### Tests for User Story 3 (write first)

- [x] T027 [P] [US3] Contract test for `/patients/{id}/revoke` in `api/tests/contract/patient-revoke.contract.test.ts` (Why: lock revoke behavior | Files: `api/tests/contract/patient-revoke.contract.test.ts` | Done: 200/404 cases | Tests: `npm test -- api/tests/contract/patient-revoke.contract.test.ts`)
- [x] T028 [P] [US3] Integration test for revoke invalidating sessions in `api/tests/integration/patient-revoke.test.ts` (Why: ensure access blocked) | Files: `api/tests/integration/patient-revoke.test.ts` | Done: read/refresh fail after revoke | Tests: `npm test -- api/tests/integration/patient-revoke.test.ts`

### Implementation for User Story 3

- [x] T029 [US3] Implement revoke flow in `api/src/services/linkingService.ts` (Why: revoke link + sessions) | Files: `api/src/services/linkingService.ts` | Done: sets revokedAt for link + sessions | Tests: `npm test -- api/tests/integration/patient-revoke.test.ts`
- [x] T030 [US3] Add revoke endpoint in `api/app/api/patients/[patientId]/revoke/route.ts` (Why: expose revoke) | Files: `api/app/api/patients/[patientId]/revoke/route.ts` | Done: 200/404, deny-by-default | Tests: `npm test -- api/tests/contract/patient-revoke.contract.test.ts`

---

## Phase 6: iOS Family Mode (Caregiver)

**Goal**: Patient management UI for caregivers.

- [x] T031 [P] Implement caregiver auth choice + login/signup screens in `ios/MedicationApp/Features/Auth/*` (Why: Supabase Auth email/password) | Files: `ios/MedicationApp/Features/Auth/*` | Done: user can choose login/signup, perform auth, store session | Tests: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T032 [P] Implement patient list/add screens in `ios/MedicationApp/Features/PatientManagement/PatientManagementView.swift`, `PatientCreateView.swift` (Why: create/list patients) | Files: `ios/MedicationApp/Features/PatientManagement/*` | Done: list displays, create validates displayName | Tests: `xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test`
- [x] T033 [P] Implement code issuance UI in `ios/MedicationApp/Features/PatientManagement/PatientLinkCodeView.swift` (Why: issue/reissue with expiry) | Files: `ios/MedicationApp/Features/PatientManagement/PatientLinkCodeView.swift` | Done: shows code + expiry, reissue | Tests: XCUITest smoke
- [x] T034 Implement revoke flow UI in `ios/MedicationApp/Features/PatientManagement/PatientRevokeView.swift` (Why: unlink) | Files: `ios/MedicationApp/Features/PatientManagement/PatientRevokeView.swift` | Done: revoke triggers API, updates UI | Tests: XCUITest smoke

---

## Phase 7: iOS Patient Mode

**Goal**: Link code entry and read-only tabs for patient.

- [x] T035 [P] Implement link code entry flow in `ios/MedicationApp/Features/Linking/LinkCodeEntryView.swift` (Why: patient token acquisition) | Files: `ios/MedicationApp/Features/Linking/LinkCodeEntryView.swift` | Done: trims input, 6-digit validation, error UX | Tests: `xcodebuild ... test`
- [x] T036 [P] Implement patient tabs (Today/History placeholder) in `ios/MedicationApp/Features/PatientReadOnly/PatientReadOnlyView.swift` (Why: 002 UX) | Files: `ios/MedicationApp/Features/PatientReadOnly/PatientReadOnlyView.swift` | Done: today view + history placeholder | Tests: UI smoke
- [x] T037 Implement auto-refresh in `ios/MedicationApp/Services/AuthService.swift` or `SessionStore.swift` (Why: token rotation) | Files: `ios/MedicationApp/Services/AuthService.swift`, `ios/MedicationApp/Shared/SessionStore.swift` | Done: refresh before expiry, avoids concurrent refresh, handles invalidation | Tests: unit test + UI smoke
- [x] T038 Update root navigation for mode flow in `ios/MedicationApp/App/RootView.swift` (Why: invalid token returns to link entry) | Files: `ios/MedicationApp/App/RootView.swift` | Done: 401/403 triggers reset to link entry | Tests: XCUITest smoke

---

## Phase 8: Polish & Cross-Cutting Concerns

- [x] T039 [P] Update API docs and quickstart notes in `specs/002-family-linking/quickstart.md` (Why: runbook parity) | Files: `specs/002-family-linking/quickstart.md` | Done: commands and verification steps updated | Tests: N/A
- [x] T040 [P] Add log redaction for tokens in `api/src/logging/logger.ts` (Why: privacy compliance) | Files: `api/src/logging/logger.ts` | Done: token/PII masked | Tests: unit test if available
- [x] T041 Run full test suite and record results in `api/test-results/.last-run.json` (Why: ensure CI alignment) | Files: `api/test-results/.last-run.json` | Done: all tests passing | Tests: `npm test`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Setup; blocks all user stories
- **User Stories (Phase 3–5)**: Depend on Foundational; can proceed in parallel if staffed
- **iOS Phases (6–7)**: Can start after API contract stability (post Phase 3)
- **Polish (Phase 8)**: After core stories are done

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2
- **US2 (P2)**: Can start after Phase 2; depends on link code issuance
- **US3 (P3)**: Can start after Phase 2; independent of US2

### Parallel Opportunities

- Contract tests per story can run in parallel
- Repository and service tasks can run in parallel across stories once schema is done
- iOS family and patient flows can be split across devs

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1–2
2. Complete Phase 3 (US1)
3. Validate independent test

### Incremental Delivery

US1 → US2 → US3 → iOS flows → Polish

---

## Phase 9: Integration (001 × 002) - 家族モードの患者選択と薬登録導線の統合 🎯 MVP

**Goal**: 家族モードで「対象患者」を選択し、その患者に対して薬/スケジュール（001）を登録・編集できる導線を完成させる。  
**Key idea**: `currentPatientId`（選択中患者）を単一の真実として保持し、薬タブの操作対象を必ず `currentPatientId` に束縛する。  
**Policy**: caregiver の薬CRUDは `patientId` 必須（未指定は 422）。

### Independent Test (E2E)
- 家族ログイン → 患者作成 → 患者を選択 → 薬追加/編集 → 薬一覧に反映（対象患者が一致）  
- 患者未選択時は薬タブが「患者選択」へ誘導する  
- 選択中患者を解除（revoke）すると currentPatient がクリアされ導線がリセットされる

### iOS Integration Tasks

- [x] T042 [P] Add `currentPatientId` + persistence to `SessionStore` (Why: single source of truth for caregiver target patient | Files: `ios/MedicationApp/Shared/SessionStore.swift` (+ UserDefaults), `ios/MedicationApp/Services/*` if needed | Done: currentPatient set/clear/restore works; cleared on logout and revoke; patient mode unaffected | Tests: unit tests for persistence + basic UI smoke)
- [x] T043 [P] Wire patient selection in caregiver patient list and show selected state (Why: make selection explicit and reversible | Files: `ios/MedicationApp/Features/PatientManagement/PatientManagementView.swift`, related ViewModels | Done: tapping a patient sets `currentPatientId`; selected row is visibly highlighted; last selection is restored on next launch | Tests: UI smoke / ViewModel unit)
- [x] T044 [P] Add caregiver Medication tab guard + empty states for no patient selected / no patients / no meds (Why: prevent ambiguity and dead-ends | Files: `ios/MedicationApp/Features/Medication/*`, `ios/MedicationApp/App/RootView.swift` (tab switch helper) | Done:
  - 0 patients → prompt to create patient (navigate to 連携/患者タブ)
  - no selection → prompt to select patient (navigate to 連携/患者タブ)
  - selection + 0 meds → prompt to add med
  | Tests: UI smoke)
- [x] T045 [P] Display current patient header + switch affordance in Medication tab (Why: reduce user confusion when multiple patients exist | Files: `ios/MedicationApp/Features/Medication/MedicationListView.swift` (or equivalent) | Done: shows “対象患者: {displayName}” and a “切替” action that opens patient tab or a chooser | Tests: UI smoke)
- [x] T046 [P] Ensure Medication create/edit requests always include `currentPatientId` (Why: bind CRUD to chosen patient | Files: `ios/MedicationApp/Networking/APIClient.swift`, medication DTOs/ViewModels | Done: list/create/update/delete all require currentPatient; if missing, UI blocks and no request is sent | Tests: APIClient request-building unit tests)
- [x] T047 [P] Handle revoke impact on selection (Why: avoid stale selection) | Files: `ios/MedicationApp/Features/PatientManagement/PatientRevokeView.swift`, `ios/MedicationApp/Shared/SessionStore.swift` | Done: if revoked patient == currentPatient, clear selection and redirect medication tab to selection prompt | Tests: UI smoke

### API Integration Tasks

- [x] T048 [P] Enforce caregiver medication endpoints require `patientId` (Why: remove ambiguity in multi-patient caregiver UX | Files: medication endpoints in `api/app/api/*` and validators (001) | Done: create/list/update for caregiver require `patientId` and return 422 when missing; non-owned patientId is 404 conceal | Tests: contract + integration)
- [x] T049 [P] Add integration test: caregiver creates two patients, adds meds to each, list scoped by patientId (Why: prove scoping works end-to-end | Files: `api/tests/integration/caregiver-medication-scope.test.ts` (new) | Done: med A not returned when querying patient B; 422 on missing patientId | Tests: `npm test -- api/tests/integration/caregiver-medication-scope.test.ts`)
- [x] T050 [P] Add integration test: revoke clears access but does not delete data; re-link same family can continue (Why: validate “解除はアクセス遮断のみ” contract | Files: `api/tests/integration/revoke-access-only.test.ts` (new) | Done: revoke makes patient tokens invalid; caregiver can still see patient and meds; new patient session after re-link can read existing meds | Tests: `npm test -- api/tests/integration/revoke-access-only.test.ts`)

### Cross-Cutting / Documentation

- [x] T051 [P] Update E2E checklists for unified caregiver flow (Why: prevent regression where UI is disconnected | Files: `specs/001-medication-regimen/checklists/e2e.md`, `specs/002-family-linking/checklists/e2e.md` | Done: unified scenario added (login→patient create→select→med CRUD) | Tests: N/A)
- [x] T052 Run full test suite and record results (Why: confirm no regressions across 001/002) | Files: `api/test-results/.last-run.json` (optional) | Done: all tests passing | Tests: `npm test` + iOS `xcodebuild ... test`
