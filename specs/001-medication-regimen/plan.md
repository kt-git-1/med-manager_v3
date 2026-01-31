# Implementation Plan: Medication Regimen (001)

**Branch**: `001-medication-regimen` | **Date**: 2026-01-31 | **Spec**: `specs/001-medication-regimen/spec.md`  
**Input**: Feature specification from `specs/001-medication-regimen/spec.md`

## Summary

Build the medication and regimen foundation for the MVP: caregivers can create/manage
medications and schedules, patients can read-only view, and the API generates scheduled
doses on demand without persisting them. iOS is a single SwiftUI app with family/patient
modes. Backend is Next.js Route Handlers with Supabase Auth and Postgres via Prisma.
This plan follows `specs/000-domain-policy/spec.md`.

## Technical Context

**Language/Version**: TypeScript (Node.js 20+), Swift 5.9+  
**Primary Dependencies**: Next.js Route Handlers, Supabase Auth, Prisma ORM 7.3, SwiftUI  
**Storage**: Supabase Postgres (PostgreSQL)  
**Testing**: Vitest or Jest for API unit/contract tests, Playwright API tests (optional), XCTest for iOS, XCUITest smoke  
**Target Platform**: Vercel (serverless) + iOS 17+  
**Project Type**: Mobile + API  
**Performance Goals**: Schedule generation p95 < 2s for 7-day range  
**Constraints**: Serverless cold start, Prisma in serverless, no external calls in CI  
**Scale/Scope**: MVP scope, max 50 meds / patient, up to 4 times per day

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Spec-Driven Development: spec is the single source of truth for behavior.
- Traceability: every change maps to spec + acceptance criteria + tests (or documented exception).
- Test strategy: deterministic tests, no external calls in CI, regression tests for fixes.
- Security & privacy: least privilege, deny-by-default, PII never in logs.
- Performance guardrails: schedule generation p95 < 2s for 7-day range.
- UX/accessibility: shared patterns, accessibility, error UX, and i18n readiness.
- Documentation: ADRs for key decisions; module run/test docs updated with changes.

*Post-design re-check:* No violations introduced.

## Project Structure

### Documentation (this feature)

```text
specs/001-medication-regimen/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── openapi.yaml
└── tasks.md
```

### Source Code (repository root)

```text
api/
├── src/
│   ├── auth/              # auth + authorization helpers
│   ├── middleware/        # Route Handler guards
│   ├── routes/            # Next.js route handlers
│   ├── services/          # schedule generation, domain logic
│   ├── repositories/      # Prisma access
│   ├── validators/        # request validation
│   └── logging/
└── tests/
    ├── contract/
    ├── integration/
    └── unit/

ios/
└── MedicationApp/
    ├── App/
    ├── Features/
    │   ├── ModeSwitch/
    │   ├── MedicationList/
    │   ├── MedicationForm/
    │   └── PatientReadOnly/
    ├── Shared/
    └── Tests/
```

**Structure Decision**: Mobile + API structure to keep iOS SwiftUI and Next.js isolated.
If the repository later contains `ios-patient/ios-family`, consolidate into `ios/MedicationApp`
by migrating shared UI and introducing a mode switch at the top-level scene.

## 1) アーキテクチャ & モジュール境界

- iOS (SwiftUI)
  - `ModeSwitch`: family/patient mode toggle backed by stored session type.
  - `SessionStore`: family session (Supabase JWT) and patient session (patientSessionToken).
  - 画面構成: 薬一覧、薬追加/編集（家族のみ）、患者モード閲覧（読取専用）。
- API (Next.js Route Handlers)
  - `auth`: Supabase JWT 検証、patientSessionToken 検証（スタブ化）。
  - `middleware`: caregiver/patient のロール検証と patientId スコープ判定。
  - `routes`: Medication, Regimen, Schedule の各ハンドラ。
- Prisma data access
  - `repositories`: Prisma clientの薄いラッパー（transaction境界は service 層）。
  - `services`: schedule 生成と整合性チェックの中心。
- iOS 2アプリ統合方針
  - 既存分割があればUI/ストア/ネットワーク層を共通化し、トップのモードスイッチで分岐。

## 2) データモデル & マイグレーション（Prisma + Postgres）

### Prismaモデル（抜粋）

- Medication: patientId, name, dosageStrengthValue, dosageStrengthUnit, doseCountPerIntake,
  dosageText, notes, startDate, endDate, inventoryCount, inventoryUnit, isActive, createdAt, updatedAt
- Regimen: patientId, medicationId, timezone, startDate, endDate, times, daysOfWeek, enabled,
  createdAt, updatedAt

### 保存形式の決定

- `daysOfWeek`: enum配列（`Mon`..`Sun`）を採用。読みやすさとクエリ簡素性を優先。
- `times[]`: `HH:mm` 文字列配列。入力検証が容易で、iOSと表現が一致。

### 予定生成に効くindex提案

- Medication: `(patientId, isActive)`, `(patientId, startDate, endDate)`
- Regimen: `(patientId, medicationId, enabled)`, `(patientId, startDate, endDate)`

### start/endのcanonical

- Regimen日付範囲を正とする。Medicationのstart/endは存在すれば整合性検証のみ。
- 矛盾時は422。更新競合は409。

### Prisma v7.3 初期設定（必須）

1. `prisma init` により `prisma.config.ts` を生成（リポジトリrootに配置）
2. `prisma.config.ts` に datasource URL を移す（`schema.prisma`の`url`は使わない）
3. `.env` に `DATABASE_URL` を定義し、`prisma.config.ts` で `env("DATABASE_URL")` を参照
4. `schema.prisma` は `datasource` / `generator` の定義のみ（URLは書かない）
5. マイグレーション:
   - `prisma migrate dev --name <name>` を使用（`prisma.config.ts` を前提）
6. 既存DBの内省:
   - `prisma db pull` を使用（`prisma.config.ts` を前提）
7. CI/本番の環境変数ロードはプラットフォーム側（Vercel）で管理し、
   ローカルは `.env` を使用

## 3) API設計 & 認可の実装方針

### エンドポイント一覧（001）

- Medication: create, list, get, update, archive
- Regimen: create, update, stop
- Schedule: get by range (from/to)

### バリデーション/エラー

- 401: 未認証
- 403: 権限不足（同一patientId以外の操作）
- 404: 他患者IDへのアクセス（存在隠蔽）
- 409: 更新競合
- 422: バリデーションエラー（times, start/end, daysOfWeek）

### 認証/認可ミドルウェア

- caregiver: Supabase JWT検証 → caregiverUserId → 管理patientIdのスコープチェック
- patient: patientSessionToken検証（スタブ）→ patientId確定 → read-only 強制
- スタブ方針: 001では `patientSessionToken` の署名検証を固定キー or mockにし、
  後続featureで交換可能な `PatientSessionVerifier` をインターフェース化。

### ログ方針

- トークン/PIIはログに残さない。相関ID（requestId）を付与。

## 4) Schedule生成アルゴリズム

### 条件

- from含む / to含まない
- endDateが空なら無期限、指定時は終了日を含めない
- enabled/isActive が false の場合は除外
- daysOfWeek 未指定は毎日

### 擬似コード

```
normalize(from, to, timezone):
  fromZ = toZonedDateTime(from, timezone).truncateToMinutes()
  toZ = toZonedDateTime(to, timezone).truncateToMinutes()

generate(patientId, from, to):
  meds = fetch medications + regimens by patientId
  for each regimen:
    if regimen.enabled == false: continue
    if medication.isActive == false or medication.archived: continue
    window = intersect([fromZ, toZ), [regimen.start, regimen.end))
    for each day in window:
      if daysOfWeek matches:
        for time in regimen.times:
          scheduledAt = combine(day, time, timezone)
          if scheduledAt in window and scheduledAt >= fromZ and scheduledAt < toZ:
            emit scheduled dose with medication snapshot
```

### N+1回避

- patientIdでMedicationとRegimenを一括取得し、アプリ側で生成。
- MedicationとRegimenのjoinは1回で済ませる。

## 5) iOS実装計画（SwiftUI）

- 画面
  - 薬一覧（次回予定表示）
  - 薬追加/編集（家族のみ）
  - 患者モード閲覧（read-only）
- モード切替
  - `SessionStore` が family/patient を保持し、Rootで画面切替。
  - patientSessionToken は Keychain に保存（暫定でも差し替え可能に）。
- 状態設計
  - loading/empty/error、二重送信防止、フォーム検証。
  - Dynamic Type / VoiceOverラベル必須。

## 6) テスト計画（constitution準拠）

- Backend
  - Contract: Medication/Regimen/Scheduleの入出力
  - Integration: Prisma + Postgres でスケジュール生成境界（timezone/曜日/期間）
  - Unit: schedule生成ロジックの純粋関数
- iOS
  - Unit: ViewModelのバリデーション
  - UI smoke: 薬一覧/追加/患者閲覧の主要フロー
- Independent Test をE2Eチェックリスト化

## 7) マイルストーン / 作業分解（PR単位）

- (a) Prisma schema + migration + index（v7.3セットアップ含む）
  - Done when: `prisma.config.ts` が生成済み、schema/migration が通る
- (b) API（Medication/Regimen）
  - Done when: CRUDが動作し、403/404/422/409が検証済み
- (c) Schedule生成 + テスト
  - Done when: from/to境界、曜日、timezoneのテストが通る
- (d) iOS 家族画面（一覧/追加編集）
  - Done when: 作成/編集/停止/アーカイブがUIから可能
- (e) iOS 患者モード閲覧（リンクはスタブ）
  - Done when: 読取専用UIが動作し、編集は不可
- (f) 仕上げ（エラー状態、アクセシビリティ、ドキュメント）
  - Done when: a11y/エラーUX/ドキュメント更新が完了

## 8) リスクと対策

- Vercel無料枠 + Prisma接続制限: 接続数を抑える設計、接続プールを利用。
- timezoneバグ: 境界ケースのテストを強化（DST/日付またぎ）。
- patientSessionTokenスタブの差し替え: `PatientSessionVerifier` を抽象化し、
  後続featureで実装を差し替える。
