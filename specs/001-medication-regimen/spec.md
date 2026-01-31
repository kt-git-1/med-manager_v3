# Feature Specification: Medication Regimen (001)

**Feature Branch**: `001-medication-regimen`  
**Created**: 2026-01-31  
**Status**: Draft  
**Input**: User description: "SDD（Spec-Driven Development）の spec を作成してください。"

## Background / Context

- 服薬管理アプリのMVPを段階的に開発する。今回は「薬とスケジュール登録」を土台として確立する。
- 患者の負担を減らすため、薬の作成/編集は基本的に家族（caregiver）が行う。
- iOSは2アプリに分けず、1アプリ内で「家族モード/患者モード」を切り替える。

## Goals

- 家族が患者に紐づく薬（Medication）と服用スケジュール（Regimen）を作成/編集/停止/アーカイブできる。
- 指定期間の予定（scheduled doses）をDBに保存せずに生成して返せる。
- iOSで薬一覧と薬追加/編集UXが成立する（家族モード中心、患者モードは閲覧中心）。

## Non-goals

- 頓用（PRN/必要時）は001では実装しない（将来拡張の余地は残す）。
- 服用記録、履歴/カレンダーの詳細閲覧、通知配信、在庫の自動減算は別feature。
- 家族招待/オンボーディングの連携コード発行詳細は別feature。

## Roles & Access

- 家族モード: 書き込み権限を持ち、管理する患者のデータを作成/更新できる。
- 患者モード: 自分の患者データを閲覧のみできる（001では編集不可）。
- deny-by-defaultを徹底し、他患者のデータは存在が推測できない扱いとする（他患者IDは404）。

## Clarifications

### Session 2026-01-31

- Q: 他患者データへのアクセス時の応答方針は？ → A: 他患者IDは404（存在を隠す）
- Q: 予定生成のendDateの扱いは？ → A: endDateが空なら無期限、指定時は含まない
- Q: Medicationのアーカイブ時の扱いは？ → A: 非表示にしつつ参照可能（履歴保持）
- Q: Regimen停止時の扱いは？ → A: 予定生成から除外し、参照は可能
- Q: Medication/Regimenの同時更新競合時の扱いは？ → A: 競合検知時は409で再試行を促す
- Q: Scheduled Dosesのfrom/to境界は？ → A: from含む / to含まない

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 家族が薬とスケジュールを登録する (Priority: P1)

家族は患者の薬情報と服用スケジュールを登録・編集・停止・アーカイブできる。

**Why this priority**: 服薬管理の基盤となるデータ作成が最優先の価値を持つため。

**Independent Test**: 家族が患者Aに対して薬1件とスケジュールを作成し、一覧に反映されることを確認できる。

**Acceptance Scenarios**:

1. **Given** 家族が患者Aにリンク済みである, **When** 薬とスケジュールを作成する, **Then** 薬一覧に新規薬が表示される
2. **Given** 既存の薬とスケジュールがある, **When** 家族が内容を更新または停止する, **Then** 一覧と詳細に更新内容が反映される

---

### User Story 2 - 予定を期間指定で生成し、次回予定を表示する (Priority: P2)

家族と患者は指定期間の予定を取得でき、薬一覧には次回予定が表示される。

**Why this priority**: 後続の通知・服用記録の土台となる予定生成が必要なため。

**Independent Test**: 既存の薬/スケジュールに対し、7日範囲の予定を取得して曜日・時刻が一致することを確認できる。

**Acceptance Scenarios**:

1. **Given** 週3回のスケジュールが登録済み, **When** 7日範囲の予定を取得する, **Then** 指定曜日と時刻のみが返る
2. **Given** 薬一覧が表示されている, **When** 次回予定が存在する, **Then** 各薬に次回予定が表示される

---

### User Story 3 - 患者は閲覧のみできる (Priority: P3)

患者は薬一覧と予定を閲覧できるが、編集はできない。

**Why this priority**: 家族と患者の役割分担を守りつつ、患者の閲覧体験を保証するため。

**Independent Test**: 患者が薬一覧と予定を閲覧し、更新操作が拒否されることを確認できる。

**Acceptance Scenarios**:

1. **Given** 患者が自分の患者IDにリンク済み, **When** 薬一覧と予定を閲覧する, **Then** 読み取りが成功する
2. **Given** 患者が更新系操作を試みる, **When** 変更を送信する, **Then** 変更が拒否される

---

### Edge Cases

- タイムゾーン境界や日付またぎで予定が欠落/重複しないか
- Regimenが無効化またはMedicationがアーカイブされた場合は予定に出ない
- timesが重複または空の場合は登録できない
- startDate/endDateの矛盾がある場合は登録できない
- 同時更新で競合が発生した場合は更新が拒否される

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 家族は自分が管理する患者のMedicationを作成/更新/停止/アーカイブできる
- **FR-002**: 家族はRegimenを作成/更新/停止できる（曜日指定または毎日、時刻は1件以上）
- **FR-003**: 患者は自分のMedicationと予定を閲覧できるが編集はできない
- **FR-004**: 予定（scheduled doses）はDBに保存せず、期間クエリで生成して返す
- **FR-005**: 予定生成はtimezone・曜日・start/end・isActive/enabledを反映する
- **FR-006**: 予定の安定キーは (patientId, medicationId, scheduledAt) とする
- **FR-007**: scheduledAtは分単位に正規化し、fromは含みtoは含まない境界で生成する
- **FR-008**: 予定の返却にはMedicationの表示用スナップショットを含める
- **FR-009**: 薬一覧には次回予定が表示される（予定生成結果から算出）
- **FR-010**: Regimenの日付範囲を正とし、Medicationの日付は矛盾時に検証エラーとする
- **FR-011**: deny-by-defaultを徹底し、他患者IDのアクセスは404で応答する
- **FR-012**: timesは重複不可のHH:mm形式、startDate <= endDateを必須とする
- **FR-013**: endDateが空なら無期限、指定時は終了日を含めずに予定を生成する
- **FR-014**: Medicationをアーカイブした場合は一覧や予定生成から除外し、参照は可能とする
- **FR-015**: Regimenを停止した場合は予定生成から除外し、参照は可能とする
- **FR-016**: 更新競合を検知した場合は409を返し、再取得・再試行を促す

### Non-Functional Requirements *(mandatory)*

- **NFR-001 (Performance)**: 7日範囲の予定取得は、標準的な患者負荷（最大50薬・1日4回）で95%の試行が2秒以内に完了する
- **NFR-002 (Security/Privacy)**: 最小権限を適用し、PIIやトークンはログに残さない
- **NFR-003 (UX/Accessibility)**: 薬一覧と登録画面はloading/empty/errorを表示し、二重送信を防止し、Dynamic Type/VoiceOverに対応する
- **NFR-004 (Documentation/Operations)**: 仕様・ADR・運用手順に影響がある場合は同一PRで更新する

### Key Entities *(include if feature involves data)*

- **Medication**: 患者に紐づく薬情報。名称、用量、服用数/回、メモ、在庫、開始/終了、状態を持つ
- **Regimen**: Medicationに紐づく服用スケジュール。timezone、曜日指定、時刻、開始/終了、状態を持つ
- **Scheduled Dose**: 期間指定で生成される予定。scheduledAtとMedicationの表示用スナップショットを持つ
- **Patient**: 服薬管理の対象となる患者
- **Caregiver**: 患者を管理し書き込み権限を持つ家族
- **Patient Link**: 家族と患者の紐付け関係

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 介護者の90%が薬とスケジュールの登録を3分以内に完了できる
- **SC-002**: 7日範囲の予定取得は95%の試行が2秒以内に完了する
- **SC-003**: 権限外アクセスの試行は100%拒否され、他患者データの露出がない
- **SC-004**: 患者の閲覧タスク完了率が95%以上である

## Assumptions & Dependencies

- `specs/000-domain-policy/spec.md` に依存する。
- 連携コード発行/交換の詳細は後続featureで扱う前提とする。
- 1アプリ内で家族モード/患者モードを切り替える。
