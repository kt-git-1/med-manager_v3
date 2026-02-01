# Implementation Plan: Family Linking (002)

**Branch**: `002-family-linking` | **Date**: 2026-02-01 | **Spec**: `specs/002-family-linking/spec.md`  
**Input**: Feature specification from `specs/002-family-linking/spec.md`

## Summary

Implement caregiver-to-patient linking with short-lived one-time codes, patient session
tokens with refresh/rotation, and unlink flows. Replace the 001 patientSessionToken stub
with real verification backed by PatientSession records. iOS remains a single SwiftUI app
with family/patient modes, and backend is Next.js Route Handlers with Supabase Auth
(email/password for caregiver login/signup) and Postgres via Prisma. This plan follows
`specs/000-domain-policy/spec.md`.

## Technical Context

**Language/Version**: TypeScript (Node.js 20+), Swift 5.9+  
**Primary Dependencies**: Next.js Route Handlers, Supabase Auth email/password, Prisma ORM 7.3, SwiftUI  
**Storage**: Supabase Postgres (PostgreSQL)  
**Testing**: Vitest or Jest (API unit/contract/integration), XCTest + XCUITest smoke  
**Target Platform**: Vercel (serverless) + iOS 17+  
**Project Type**: Mobile + API  
**Performance Goals**: Linking code exchange p95 < 2s; iOS transitions smooth (no main-thread blocking)  
**Constraints**: Serverless cold start, Prisma connection limits, token rotation (old token invalidated)  
**Scale/Scope**: MVP scope, single caregiver per patient, limited patient data for read-only views

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Spec-Driven Development: spec is the single source of truth for behavior.
- Traceability: every change maps to spec + acceptance criteria + tests (or documented exception).
- Test strategy: deterministic tests, no external calls in CI, regression tests for fixes.
- Security & privacy: least privilege, deny-by-default, PII never in logs.
- Performance guardrails: linking code exchange p95 < 2s; refresh should be lightweight.
- UX/accessibility: shared patterns, accessibility, error UX, and i18n readiness.
- Documentation: ADRs for key decisions; module run/test docs updated with changes.

*Post-design re-check:* No violations introduced.

## Project Structure

### Documentation (this feature)

```text
specs/002-family-linking/
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
├── app/
│   └── api/
│       ├── patients/                  # caregiver endpoints
│       ├── patient/                   # patient endpoints
│       └── schedule/                  # existing read-only
├── src/
│   ├── auth/                          # JWT + patientSessionToken verification
│   ├── middleware/                    # caregiver/patient guards
│   ├── repositories/                  # Prisma access
│   ├── services/                      # domain workflows
│   ├── validators/                    # request validation
│   └── logging/
└── tests/
    ├── contract/
    ├── integration/
    └── unit/

ios/MedicationApp/
├── App/
├── Features/
│   ├── ModeSelect/
│   ├── Auth/                          # caregiver login
│   ├── Linking/                       # link code entry
│   ├── PatientReadOnly/               # today view, placeholders
│   └── PatientManagement/             # new for 002 (list/create/link/revoke)
├── Services/
├── Shared/
└── Tests/
```

**Structure Decision**: Mobile + API structure to keep SwiftUI and Next.js isolated while
sharing the same patient scope model across iOS and API.

## 1) アーキテクチャ & モジュール境界

### API (Next.js Route Handlers)

- **Routes** (app router):
  - Caregiver: `api/app/api/patients/route.ts`, `api/app/api/patients/[patientId]/linking-codes/route.ts`,
    `api/app/api/patients/[patientId]/revoke/route.ts`
  - Patient: `api/app/api/patient/link/route.ts`, `api/app/api/patient/session/refresh/route.ts`
  - Read-only APIs from 001 (medications, schedule) use patientSessionToken verified by new verifier.
- **Auth boundary**:
  - `api/src/middleware/auth.ts` enforces caregiver vs patient scopes (deny-by-default).
  - `api/src/auth/supabaseJwt.ts` verifies Supabase JWT for caregiver sessions.
  - `api/src/auth/patientSessionVerifier.ts` replaced with DB-backed validation (no stub).
  - `api/src/middleware/error.ts` for consistent error mapping to 401/403/404/409/422/429.
- **Token verification replacement**:
  - 001 stub verifier -> 002 real verifier (lookup active PatientSession by token hash, check revoked).
  - Update tests in `api/tests/unit/patient-session-stub.test.ts` to assert real verification.

### Data Access (Prisma)

- Repository layer in `api/src/repositories/`:
  - `patientRepo`, `caregiverPatientLinkRepo`, `linkingCodeRepo`, `patientSessionRepo`
- Service layer in `api/src/services/`:
  - `linkingService` (issue code, exchange, rate-limit/lockout, revoke)
  - `patientSessionService` (rotate tokens, validate active session)
- Services own transactions, repositories are thin Prisma wrappers.

### iOS (SwiftUI)

- **ModeSelect** → **Caregiver auth (login/signup)** or **Link code entry** (fixed launch flow).
- **State management**:
  - `SessionStore` holds caregiver JWT and patientSessionToken (Keychain-backed).
  - Mode selection plus session state determines root navigation.
- **Navigation**:
  - Caregiver: Auth choice → Login/Signup → Tab (薬 / 連携・患者)
  - Patient: Link code → Tab (今日 / 履歴)
- **Session refresh**:
  - Background refresh before expiry; avoid concurrent refresh; on failure, return to link code entry.

## 2) データモデル & マイグレーション（Prisma + Postgres）

### Prismaモデル（抜粋）

- Patient: displayName (max 50), caregiverId (owner), createdAt, updatedAt
- CaregiverPatientLink: caregiverId, patientId (unique), status, revokedAt, createdAt
- LinkingCode: patientId, codeHash, expiresAt, usedAt, issuedBy, createdAt
- PatientSession: patientId, tokenHash, issuedAt, expiresAt (optional), lastRotatedAt, revokedAt

### Index / Unique制約

- CaregiverPatientLink: unique(patientId)
- LinkingCode: index(codeHash), index(patientId, expiresAt)
- PatientSession: unique(tokenHash), index(patientId), index(revokedAt)

### 解除の扱い

- Link revoke は `CaregiverPatientLink.revokedAt` と `PatientSession.revokedAt` 更新で
  アクセス遮断（データ削除しない）。

### Prisma v7.3 初期設定（必須）

1. `prisma.config.ts` を使用（既存の v7.3 ルールを踏襲）
2. `DATABASE_URL` は `.env` と Vercel 環境変数で管理
3. マイグレーションは `npx prisma migrate dev --name family_linking`
4. `prisma db pull` は `prisma.config.ts` を前提

## 3) API設計 & 認可（000-domain-policy準拠）

### caregiver API

- `POST /patients`
  - displayName 必須、空白のみは 422
- `GET /patients`
  - caregiver 自身が作成した患者のみ
- `POST /patients/{patientId}/linking-codes`
  - 他家族の patientId は 404
  - 再発行時は旧コード即無効化
- `POST /patients/{patientId}/revoke`
  - Link解除 + PatientSession 全失効（以後 refresh 不可）

### patient API

- `POST /patient/link`
  - 6桁数値コード（前後トリム）を交換 → patientSessionToken 発行
  - 期限切れ/未登録/使用済みは 404 or 422 (policyに従い情報隠蔽)
  - 試行回数超過は 429、ロックアウト 5分
- `POST /patient/session/refresh`
  - 旧トークンを無効化し、新トークンを返す（回転）

### スコープとエラー

- 401: トークン欠如/無効
- 403: 役割不一致
- 404: 他patientIdへのアクセス（情報漏洩防止）
- 409: 1患者=1家族制約違反
- 422: バリデーション（displayName 空/コード形式不正）
- 429: 試行回数超過・ロックアウト

### 001への影響

- `api/src/auth/patientSessionVerifier.ts` を DB 参照の本実装に置換。
- 001の read-only API (medications/schedule) は patientSessionToken の検証ロジック差替えのみ。
- 追加テスト: 001の閲覧APIが新トークンで通る回帰テスト。

## 4) セキュリティ & レート制限/ロックアウト

- 試行回数/ロックアウトは patientId 単位でカウント（5回で5分ロック）。
- 再発行時は旧コード即無効化し、試行カウンタはリセットしない。
- 連携コードは 6桁数字、前後トリム、ハッシュ保存（平文保存禁止）。
- ログに PII/トークンを出さない（マスク + requestId）。

## 5) iOS実装計画（SwiftUI）

### 家族モード

- Login → Tab (薬 / 連携・患者)
- 連携・患者タブ:
  - 患者一覧
  - 患者追加（displayName 必須）
  - 患者選択 → 連携コード発行（期限表示/再発行）
  - 解除（リンク解除＋患者セッション失効）

### 患者モード

- 連携コード入力 → Tab (今日 / 履歴)
- 002時点:
  - 今日タブ: 今日の予定 or 空状態
  - 履歴タブ: 準備中プレースホルダー

### セッション自動更新

- 期限の手前で refresh を呼ぶ（バックオフ + 同時呼び出し防止）
- refresh 失敗時は患者モードをリセットし、連携コード入力へ戻す

## 6) テスト計画（tests first）

### Contract tests

- `POST /patients` / `GET /patients`
- `POST /patients/{id}/linking-codes`
- `POST /patients/{id}/revoke`
- `POST /patient/link`
- `POST /patient/session/refresh`

### Integration tests

- 1患者=1家族制約（別家族操作の404/409）
- コード期限/ワンタイム/再発行無効化
- ロックアウト（patientId単位）
- refresh 回転（旧token無効化）
- revoke で既存 token が無効化される
- 001の閲覧APIが新トークンで通る回帰

### iOS unit/UI smoke

- ModeSelect遷移、患者作成フォーム必須チェック、失効時の戻り先

## 7) マイルストーン / PR分割（依存順）

- (a) Prisma schema + migrations + indexes
- (b) Patient/CaregiverLink/LinkingCode/PatientSession repositories/services
- (c) Auth/Token verifier を本実装にし、001のスタブを置換（回帰テスト含む）
- (d) API endpoints + contract/integration tests
- (e) iOS 家族タブ（患者作成/一覧/コード発行/解除）
- (f) iOS 患者フロー（コード入力/今日・履歴タブ/自動refresh/失効時遷移）
- (g) ドキュメント（quickstart、運用注意、差し替え点、エラーマトリクス）

## 8) リスクと対策

- Vercel + Prisma 接続/コールドスタート: 接続数抑制、プール活用、N+1回避。
- ロックアウト/レート制限の保存場所: DBで patientId 単位に保持し整合性を担保。
- token回転の同時refresh: iOS側で同時呼び出しを抑制し失敗時は再リンク導線。
