# Android Play 初回公開ロードマップ

**更新日:** 2026-08-23 JST

**公開バージョン:** `1.0.0` / `versionCode=1`

**Android開発:** `android-dev@2a716ed08fcc`

**現行iOS/API参照:** `staging@e9ec0d3c6b10`

**現行Production:** `main@432b34c064d7`

**状態:** Phase 0完了。Phase 1は全QAとAnalytics確認まで完了し、一時Exploreの削除確認だけが残る。Play Console／Production操作は未開始。

この文書は、Androidを現在のStaging仕様へ再固定してからPlayストアへ初回公開するまでの実行順を示す。詳細な操作と安全条件は `play-release-runbook.md`、残存ゲートは `release-gates.json` を正とする。

## 1. 現在地

完了済み:

- 本人・家族モード、薬・在庫・履歴・通知・チュートリアルのAndroid実装
- Staging API/DBを使った本人・家族の手動確認
- Firebase Analyticsの明示同意、拒否・停止・匿名イベント送信
- 薬を削除しても定時薬・頓服の過去履歴を保持するAPI/DB/Android対応
- Stagingで「服用記録 → 薬を削除 → Today/使用中一覧から除外 → 本人・家族履歴に保持」のE2E確認
- Staging/Production flavor分離、Release APK/AAB・16 KB・SDK・権限・広告識別子除外のローカルゲート
- API 35、A302SHを含む自動QAと主要実機確認

公開前に残るもの:

- Firebase Consoleの検証済み一時Explore削除とRG-001の記録確定
- Play Organization開発者アカウント、アプリ作成、Play App Signing、アップロード鍵（RG-005）
- Production API migration/deploy、App Links、Android FCM（RG-002/RG-004）
- Play Internalからインストールした正確なAABの検証（RG-006）
- 旧対応端末・Google標準端末・TalkBackの手動確認（RG-007/RG-008）
- Data safety、Health apps、アカウント削除、ストア掲載・審査情報（RG-009）
- Closed test、Crash/ANR確認、最終rebaseline、`main`への統合（RG-010）

## 2. 固定方針

1. Androidの変更は公開判定まで`android-dev`だけで行う。
2. iOS/APIの参照元は当面`origin/staging`、公開中Productionの事実確認は`origin/main`を使う。
3. Android初回公開は`1.0.0`とし、PlayへAABをアップロードするたびに`versionCode`だけを増やす。
4. Stagingは`.staging`パッケージ、Play提出物は`productionRelease`の`com.afterlifearchive.medmanager`だけを使う。
5. Billingは初回公開範囲外で`BILLING_ENABLED=false`を維持する。
6. Play Consoleの契約、支払い、本人確認、鍵登録、宣言提出、Production deploy、ブランチ統合は、その時点で所有者の明示承認を得る。
7. 実ユーザー・実患者はQAに使わず、保持済みの専用テストアカウント／患者だけを使う。
8. ストア画像は最終UI凍結後に再生成・再確認する。現在の素材を公開確定版とは扱わない。

## 3. 実行フェーズ

### Phase 0 — 最終仕様rebaseline

**状態: 完了（2026-08-23）** — `staging@e9ec0d3`を`b75dedf`で統合し、未説明差分0件、APIテストの旧`platform=web`入力1件を現契約へ修正した。

1. `origin/staging@e9ec0d3c6b10`をiOS/APIの固定参照にする。
2. 前回同期点以降のiOS/API変更を、画面・文言・DTO・通知・履歴・チュートリアル単位で監査する。
3. Androidに不足があれば、契約 → テスト → 実装 → Staging E2Eの順で修正する。
4. Firebase AnalyticsのiOS/Androidイベント名・parameter allowlistを再比較する。
5. `source-baseline.md`、parity ledger、このロードマップのSHAを一致させる。

**完了条件:** 未説明のiOS/API差分が0件。AndroidのRelease範囲にP0/P1不具合がない。

### Phase 1 — Stagingリリース候補の凍結

**状態: 最終cleanup待ち（2026-08-23）** — API 351/351、Compose UI 299/299、全JVM/lint/build/release artifact gate、consent拒否/許可、DebugView 3イベント、Android限定Exploreまで合格。一時Exploreの削除だけがRG-001の残作業。

1. Debug/Release unit、Compose UI、lint、assemble、API regressionを全実行する。
2. 本人・家族の主要導線、部分記録、在庫不足、終了薬、頓服、薬archive履歴を再確認する。
3. Firebase consent OFF/ON/OFF、Realtime、Eventsに加え、`core_action_failed`、`patient_link_code_share_tapped`、`notification_permission_result`をsynthetic操作でDebugView確認する。
4. 所有者承認のもと一時的なExploreを作り、Platform=`android`で安全な固定enumだけを確認して削除する（RG-001）。
5. RC commitを固定し、以後の変更は不具合修正だけに制限する。

**完了条件:** P0-PreとRG-001が完了し、Staging RCのcommit・テスト結果・残存既知問題が記録されている。

### Phase 2 — Play所有者基盤と署名

1. Healthアプリ要件を再確認し、Organization開発者アカウントを所有者が作成・確認する。
2. `com.afterlifearchive.medmanager`を一度だけ作成し、Play App Signingを有効化する。
3. アップロード鍵を所有者管理で作成・バックアップし、秘密値をGit・チャット・ログへ出さない。
4. upload certificateとPlay app-signing certificateを別物として記録・検証する。
5. Production用Firebase Androidアプリを登録し、値はCI secretまたはGit-ignore済みローカル設定だけへ入れる。

**完了条件:** RG-005のアカウント・パッケージ・署名・Firebase前提が揃い、署名済みAABを安全に生成できる。

### Phase 3 — Productionバックエンド準備

1. Production DBの読み取り専用preflightとmigration影響を確認する。
2. `archivedAt`を含むmigrationとAndroid API対応を、保護された手動workflowだけでdeployする。
3. health、認証、薬archive、履歴、通知登録を専用テストデータでsmoke testする。
4. Play app-signing証明書で`assetlinks.json`を公開し、App Linksを検証する。
5. Android FCMのtoken登録、本人記録通知、generic通知内容、tap routing、解除をProduction専用QAで確認する。

**完了条件:** RG-002/RG-003/RG-004が完了。Productionへの誤接続、重複記録、履歴消失がない。

### Phase 4 — 署名済みAABとInternal testing

1. clean commitから`productionRelease`を生成し、Release runtime、SDK、権限、16 KB、AAB内容、署名を検証する。
2. AAB・evidence JSON・SHA256SUMSの不変handoffを作成する。
3. Play Internalへアップロードし、Play Developer APIのbundle/track/generated APK情報を同じAABへ結び付ける。
4. adb版ではなくPlay Internalから実機へインストールする。
5. fresh install、update、session復元、App Links、FCM、ローカル通知、バックグラウンド・Dozeを確認する。

**完了条件:** RG-006が完了し、Playが署名・配布した正確なartifactを実機で確認できる。

### Phase 5 — 端末・アクセシビリティ・安定性QA

1. A302SHに加え、API 26–28の旧対応実機と現行Google標準実機を確認する。
2. 文字サイズ100%/125%/200%、ダークモード、回転・狭幅、IME、オフライン復帰を確認する。
3. TalkBackで読み上げ順、focus、double tap、2本指scroll、機密情報の読み上げを確認する。
4. Crash/ANR、起動時間、履歴の長時間読み込み、連続refresh、process deathを監視する。

**完了条件:** RG-007/RG-008が完了し、未解決のP0/P1が0件。

### Phase 6 — Play Console掲載・宣言・Closed test

1. 最終UIからアイコン、feature graphic、8枚の日本語phone screenshotを再生成・目視確認する。
2. Data safety、Health apps、広告なし、Analytics同意、アカウント削除URLを実artifactと照合して入力する。
3. 審査用の再利用可能な家族アカウントと専用患者を用意し、資格情報はGitへ保存しない。
4. Closed testで本人・家族の実運用シナリオを行い、Crash/ANRとfeedbackを確認する。

**完了条件:** RG-009が完了し、Closed testで公開阻害不具合がない。

### Phase 7 — 最終統合と初回公開

1. 最新`main`と`staging`を再取得し、iOS/API/Androidの最終差分監査を行う。
2. Androidの変更だけをレビューし、iOSの古いファイルを逆流させない。
3. 明示承認後に`android-dev`を`main`へ統合する。
4. 統合でcommit/tree/artifactが変わった場合はAABを再生成し、Internal smokeを再実行する。
5. Production提出を開始し、審査通過後は段階的rolloutとCrash/ANR/Analyticsを監視する。

**完了条件:** RG-010を含む全RGが完了し、PlayのProduction trackで`1.0.0`が公開され、監視とrollback判断手順が有効になっている。

## 4. 直近の実行順

次に行う作業は以下の順番に固定する。

1. 検証済み一時Analytics Exploreの削除を所有者が即時確認し、RG-001を閉じる
2. Staging RC commitを確定して`android-dev`へpushする
3. Play Organizationアカウント準備（所有者作業）
4. Production署名・Firebase・API/App Links/FCM
5. Play Internal → 実機QA → Console宣言 → Closed test
6. 最終`main`統合 → Production公開

掲載素材の最終化はPhase 6まで保留し、Phase 0–5のUI変更で作り直しが発生しないようにする。
