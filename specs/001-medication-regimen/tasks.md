---

description: "Task list template for feature implementation"
---

# Tasks: Medication Regimen (001)

**Input**: Design documents from `/specs/001-medication-regimen/`  
**Prerequisites**: `spec.md` (required), `plan.md` (recommended), `specs/000-domain-policy/spec.md` (required / already exists)  
**Tests**: Tests are REQUIRED (per constitution and user request).  
**Organization**: Tasks are grouped by phase + user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can be done in parallel (different files / minimal dependency)
- **[Story]**: US1/US2/US3 mapping for traceability

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Initialize API app skeleton — Why: Next.js Route Handlers基盤を作る; Files: `api/package.json`, `api/tsconfig.json`, `api/app/api/health/route.ts`; Done when: dev server起動確認; Tests: `cd api && npm run dev` (smoke)
- [x] T002 [P] Initialize iOS SwiftUI app skeleton — Why: 1アプリ統合の土台; Files: `ios/MedicationApp/App/MedicationApp.swift`; Done when: ビルド成功; Tests: Xcode build
- [x] T003 [P] Add docs index for feature 001 — Why: SDD docs追跡; Files: `specs/001-medication-regimen/README.md`（または `quickstart.md` 更新）; Done when: 参照先（000/spec、001/spec、plan、tasks）がリンクされる; Tests: N/A
- [x] T004 [P] Configure API lint/format/typecheck — Why: CIで品質ゲートを確保; Files: `api/.eslintrc.cjs`, `api/.eslintignore`, `api/.prettierrc`, `api/.prettierignore`, `api/tsconfig.json`; Done when: lint/typecheckが通る; Tests: `cd api && npm run lint && npm run typecheck`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### 2.1 Test harness & DB strategy (must come early)

- [x] T005 [P] Setup API test runner (unit/contract/integration) — Why: “tests first”を成立させる; Files: `api/vitest.config.ts` (or jest config), `api/package.json` scripts, `api/tests/**` structure; Done when: `cd api && npm test` が空テストで緑になる; Tests: `cd api && npm test`
- [x] T006 [P] Setup test DB strategy + lifecycle helpers — Why: integration testsを安定化; Files: `api/tests/_db/*`, `api/docker-compose.test.yml`（または Supabase local 利用手順）, `api/.env.test.example`; Done when: テスト実行時にDBが起動/マイグレーション適用/クリーンアップできる; Tests: `cd api && npm test`（integrationの最小1本）

### 2.2 Prisma / DB foundation (Prisma v7.3)

- [x] T007 Setup Prisma v7.3 config — Why: v7.3流儀の初期設定差分に対応; Files: `api/prisma.config.ts`, `api/prisma/schema.prisma`, `api/.env.example`; Done when: v7.3の推奨構成で `npx prisma generate` / `npx prisma migrate dev --name init` が通る; Tests: `cd api && npx prisma migrate dev --name init`
- [x] T008 Define Medication/Regimen models + indexes — Why: 001の永続層を確立; Files: `api/prisma/schema.prisma`, `api/prisma/migrations/*`; Done when: migration適用 & index確認; Tests: `cd api && npx prisma migrate dev`
- [x] T009 [P] Prisma client singleton (serverless-safe) — Why: Vercel接続数を抑制; Files: `api/src/repositories/prisma.ts`; Done when: 1インスタンス再利用; Tests: `api/tests/unit/prisma-client.test.ts`

### 2.3 AuthZ / validation / error foundations

- [x] T010 [P] Auth middleware skeleton — Why: caregiver/patient認可の土台; Files: `api/src/auth/supabaseJwt.ts`, `api/src/auth/patientSessionVerifier.ts`, `api/src/middleware/auth.ts`; Done when: caregiver/patient分岐とスコープ確定が動作; Tests: `api/tests/unit/auth-middleware.test.ts`
- [x] T011 [P] patientSessionToken stub +差し替え手順記載 — Why: 001は暫定運用（連携コード発行は後続）; Files: `api/src/auth/patientSessionVerifier.ts`, `specs/001-medication-regimen/quickstart.md`; Done when: スタブで検証通過 + 置換手順がコメント/Docに明記; Tests: `api/tests/unit/patient-session-stub.test.ts`
- [x] T012 [P] Error mapping + logging — Why: 401/403/404/409/422の統一; Files: `api/src/middleware/error.ts`, `api/src/logging/logger.ts`; Done when: 代表エラーが統一応答; Tests: `api/tests/unit/error-mapper.test.ts`
- [x] T013 [P] Validation helpers — Why: times/start/end/daysOfWeek検証を共通化; Files: `api/src/validators/medication.ts`, `api/src/validators/regimen.ts`, `api/src/validators/schedule.ts`; Done when: バリデーション関数が完成; Tests: `api/tests/unit/validators.test.ts`

### 2.4 iOS foundations (one app, two modes)

**Requirement (fixed)**: アプリ起動直後の画面で「患者 / 家族」を選択し、  
- 患者 → 連携コード入力画面  
- 家族 → ログイン画面  
へ遷移する。

- [x] T014 [P] iOS SessionStore + app entry routing — Why: モード選択と画面遷移の土台; Files: `ios/MedicationApp/Shared/SessionStore.swift`, `ios/MedicationApp/Features/ModeSelect/ModeSelectView.swift`, `ios/MedicationApp/App/RootView.swift`; Done when: 起動→モード選択→（患者=連携、家族=ログイン）へ遷移できる; Tests: `ios/MedicationApp/Tests/SessionStoreTests.swift`
- [x] T015 [P] iOS APIClient foundation + error mapping — Why: ViewModelからHTTP詳細を分離; Files: `ios/MedicationApp/Networking/APIClient.swift`, `ios/MedicationApp/Networking/DTOs/*.swift`, `ios/MedicationApp/Networking/APIError.swift`; Done when: 認証ヘッダ付与（家族JWT/患者token）とエラー分類ができる; Tests: `ios/MedicationApp/Tests/APIClientTests.swift`
- [x] T016 [P] iOS caregiver login UI + Supabase Auth — Why: 家族モードの認証; Files: `ios/MedicationApp/Features/Auth/CaregiverLoginView.swift`, `ios/MedicationApp/Services/AuthService.swift`; Done when: email/passwordでログイン→セッション保存→APIClientがJWTを付与できる; Tests: `ios/MedicationApp/Tests/AuthServiceTests.swift`（unit中心）
- [x] T017 [P] iOS patient link code UI (stub) — Why: 患者モード入口（後続で本実装に差し替え）; Files: `ios/MedicationApp/Features/Linking/LinkCodeEntryView.swift`, `ios/MedicationApp/Services/LinkingService.swift`; Done when: code入力→（スタブで）patientSessionToken保存→閲覧APIを呼べる; Tests: `ios/MedicationApp/Tests/LinkingServiceTests.swift`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - 家族が薬とスケジュールを登録する (Priority: P1) 🎯 MVP

**Goal**: 家族がMedication/Regimenを作成・更新・停止・アーカイブできる

**Independent Test**: 家族が患者Aに薬1件+スケジュール作成し一覧に反映される

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T018 [P] [US1] Contract tests for Medication endpoints — Why: 入出力契約; Files: `api/tests/contract/medications.contract.test.ts`; Done when: 201/200/204/401/422/409を網羅; Tests: `cd api && npm test`
- [x] T019 [P] [US1] Contract tests for Regimen endpoints — Why: 入出力契約; Files: `api/tests/contract/regimens.contract.test.ts`; Done when: 201/200/401/409/422を網羅; Tests: `cd api && npm test`
- [x] T020 [P] [US1] Integration tests for caregiver CRUD — Why: 認可+DB整合; Files: `api/tests/integration/caregiver-medication-regimen.test.ts`; Done when: caregiver CRUD成功; Tests: `cd api && npm test`

### Implementation for User Story 1

- [x] T021 [P] [US1] Medication repository — Why: DB操作の境界; Files: `api/src/repositories/medicationRepo.ts`; Done when: CRUD実装; Tests: `api/tests/unit/medication-repo.test.ts`
- [x] T022 [P] [US1] Regimen repository — Why: DB操作の境界; Files: `api/src/repositories/regimenRepo.ts`; Done when: CRUD実装; Tests: `api/tests/unit/regimen-repo.test.ts`
- [x] T023 [US1] Medication service (archive含む) — Why: ドメイン制約; Files: `api/src/services/medicationService.ts`; Done when: archive/isActive反映; Tests: `api/tests/unit/medication-service.test.ts`
- [x] T024 [US1] Regimen service (enabled制御) — Why: enabled制御; Files: `api/src/services/regimenService.ts`; Done when: enabled制御; Tests: `api/tests/unit/regimen-service.test.ts`
- [x] T025 [US1] Medication route handlers — Why: API提供; Files: `api/app/api/medications/route.ts`, `api/app/api/medications/[id]/route.ts`; Done when: create/list/get/update/archive対応; Tests: `api/tests/contract/medications.contract.test.ts`
- [x] T026 [US1] Regimen route handlers (nested under medication) — Why: APIをspecに合わせる; Files: `api/app/api/medications/[id]/regimens/route.ts`, `api/app/api/regimens/[id]/route.ts`; Done when: create/update（必要ならstop=PATCH enabled=false）; Tests: `api/tests/contract/regimens.contract.test.ts`
- [x] T027 [US1] Caregiver authorization rules — Why: 403/404方針; Files: `api/src/middleware/auth.ts`; Done when: 他患者IDは404（情報漏洩防止）; Tests: `api/tests/integration/caregiver-medication-regimen.test.ts`

### iOS (Caregiver mode) for US1

- [x] T028 [US1] iOS Medication list (caregiver) — Why: 家族UX; Files: `ios/MedicationApp/Features/MedicationList/MedicationListView.swift`; Done when: 薬名/開始日表示（APIClient経由）; Tests: `ios/MedicationApp/Tests/MedicationListViewModelTests.swift`
- [x] T029 [US1] iOS Medication form (caregiver) — Why: 登録/編集UX; Files: `ios/MedicationApp/Features/MedicationForm/MedicationFormView.swift`; Done when: 入力/保存/二重送信防止（APIClient経由）; Tests: `ios/MedicationApp/Tests/MedicationFormViewModelTests.swift`
- [x] T030 [US1] iOS form validation & errors — Why: UX要件; Files: `ios/MedicationApp/Features/MedicationForm/MedicationFormViewModel.swift`; Done when: validation/エラー表示; Tests: `ios/MedicationApp/Tests/MedicationFormValidationTests.swift`

**Checkpoint**: User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - 予定を期間指定で生成し、次回予定を表示する (Priority: P2)

**Goal**: 期間指定の予定生成と次回予定表示を提供する

**Independent Test**: 7日範囲の予定で曜日/時刻/タイムゾーンが一致する

### Tests for User Story 2 ⚠️

- [x] T031 [P] [US2] Unit tests for schedule generator — Why: 境界ケース保証; Files: `api/tests/unit/schedule-generator.test.ts`; Done when: timezone/曜日/start/end/enabled/isActiveを網羅; Tests: `cd api && npm test`
- [x] T032 [P] [US2] Integration tests for /schedule — Why: API整合; Files: `api/tests/integration/schedule-range.test.ts`; Done when: from/to境界/422/401を検証; Tests: `cd api && npm test`
- [x] T033 [P] [US2] Contract tests for /schedule response — Why: 安定キー/スナップショット保証; Files: `api/tests/contract/schedule.contract.test.ts`; Done when: (patientId, medicationId, scheduledAt) とスナップショットが含まれる; Tests: `cd api && npm test`

### Implementation for User Story 2

- [x] T034 [US2] Schedule generation service — Why: 予定生成ロジック; Files: `api/src/services/scheduleService.ts`; Done when: 000-domain-policyで定義された期間境界・timezone・正規化ルールに一致; Tests: `api/tests/unit/schedule-generator.test.ts`
- [x] T035 [US2] Schedule route handler — Why: 期間クエリ提供; Files: `api/app/api/schedule/route.ts`; Done when: 200で予定返却; Tests: `api/tests/integration/schedule-range.test.ts`
- [x] T036 [US2] Schedule response mapper — Why: 安定キー+スナップショット整形; Files: `api/src/services/scheduleResponse.ts`; Done when: keyとsnapshotが返る; Tests: `api/tests/contract/schedule.contract.test.ts`
- [x] T037 [US2] Add next scheduled dose to medications list response — Why: 薬一覧UX; Files: `api/app/api/medications/route.ts`, `api/src/services/medicationService.ts`; Done when: nextScheduledAt算出; Tests: `api/tests/integration/caregiver-medication-regimen.test.ts`
- [x] T038 [US2] iOS next-dose display — Why: 家族/患者の可視化; Files: `ios/MedicationApp/Features/MedicationList/MedicationListView.swift`; Done when: 次回予定表示; Tests: `ios/MedicationApp/Tests/MedicationListViewModelTests.swift`

**Checkpoint**: User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - 患者は閲覧のみできる (Priority: P3)

**Goal**: 患者は閲覧のみ可能、更新は403

**Independent Test**: 患者が閲覧でき、更新系が403になる

### Tests for User Story 3 ⚠️

- [ ] T039 [P] [US3] Contract tests for patient read-only — Why: 403/404/200方針; Files: `api/tests/contract/patient-readonly.contract.test.ts`; Done when: read可/更新不可; Tests: `cd api && npm test`
- [ ] T040 [P] [US3] Integration tests for patient access — Why: patientSessionTokenスタブ検証; Files: `api/tests/integration/patient-readonly.test.ts`; Done when: read-only動作; Tests: `cd api && npm test`

### Implementation for User Story 3

- [ ] T041 [US3] Patient auth flow with stub verifier — Why: 001の暫定運用; Files: `api/src/auth/patientSessionVerifier.ts`, `api/src/middleware/auth.ts`; Done when: read-only enforcement（更新は403、他患者は404）; Tests: `api/tests/integration/patient-readonly.test.ts`
- [ ] T042 [US3] iOS patient read-only views — Why: 患者閲覧UX; Files: `ios/MedicationApp/Features/PatientReadOnly/PatientReadOnlyView.swift`; Done when: 編集UI非表示; Tests: `ios/MedicationApp/Tests/PatientReadOnlyViewTests.swift`
- [ ] T043 [US3] iOS mode gating — Why: 患者は編集不可; Files: `ios/MedicationApp/Features/MedicationForm/MedicationFormView.swift`, `ios/MedicationApp/Shared/SessionStore.swift`; Done when: patientで編集不可（UI/操作/ナビ）; Tests: `ios/MedicationApp/Tests/ModeGatingTests.swift`

**Checkpoint**: All user stories should now be independently functional

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T044 [P] Add Independent Test E2E checklist — Why: 受け入れ確認; Files: `specs/001-medication-regimen/checklists/e2e.md`; Done when: 4シナリオが記載; Tests: N/A
- [ ] T045 [P] Accessibility + loading/empty/error states — Why: UX要件; Files: `ios/MedicationApp/Features/*`, `ios/MedicationApp/Shared/Views/StateViews.swift`; Done when: a11y対応; Tests: XCUITest smoke
- [ ] T046 [P] API error matrix docs — Why: 401/403/404/409/422を明文化; Files: `specs/001-medication-regimen/README.md`; Done when: エラー方針記載; Tests: N/A
- [ ] T047 [P] Schedule boundary regression tests — Why: timezoneバグ防止; Files: `api/tests/unit/schedule-generator.test.ts`; Done when: DST/日付跨ぎ追加（該当する場合）; Tests: `cd api && npm test`
- [ ] T048 [P] Schedule performance smoke check — Why: p95目標の観測; Files: `api/tests/integration/schedule-perf.test.ts`; Done when: 7日範囲の計測が記録; Tests: `cd api && npm test`
- [ ] T049 [P] Domain-policy linkage check — Why: 仕様依存の明確化; Files: `specs/001-medication-regimen/README.md`; Done when: `specs/000-domain-policy/spec.md` 参照が明確; Tests: N/A
- [ ] T050 Run quickstart validation — Why: 実行手順の整合; Files: `specs/001-medication-regimen/quickstart.md`; Done when: ローカル手順が通る; Tests: `npm test`, Xcode tests

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Repositories before services
- Services before endpoints
- Core implementation before UI integration
- Story complete before moving to next priority

### Parallel Opportunities

- Setup tasks marked [P] can run in parallel
- Foundational tasks marked [P] can run in parallel
- Contract/integration/unit tests can run in parallel by story

---

## Parallel Example: User Story 1

```bash
# Launch contract tests for US1 in parallel:
Task: "Contract tests for Medication endpoints in api/tests/contract/medications.contract.test.ts"
Task: "Contract tests for Regimen endpoints in api/tests/contract/regimens.contract.test.ts"

# Launch repositories for US1 in parallel:
Task: "Medication repository in api/src/repositories/medicationRepo.ts"
Task: "Regimen repository in api/src/repositories/regimenRepo.ts"
```

---

## Notes

- [P] tasks = different files, no dependencies
- Each user story should be independently functional and testable
