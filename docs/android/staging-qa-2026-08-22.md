# Android Staging QA Report — 2026-08-22

## 結論

現在の `android-dev` 作業スナップショットについて、Staging の本人・家族モードを対象とした自動 QA は合格した。API 35 では Compose UI 全299件を完走し、API 33／API 26 でも主要導線9件を再実行して、最終結果はいずれも失敗0・エラー0・スキップ0だった。

これはストア公開承認ではない。Play Console、署名済み配布物、実通知の長時間運用、TalkBack の読み上げ品質など、外部環境が必要な項目は別ゲートとして残す。

## 対象

- Android: `android-dev`、開始時 HEAD `ad0a6cb` と未コミットの現行変更
- API: `staging`、開始時 HEAD `dbd6b4b`
- 実施日: 2026-08-22 JST
- API 35: `MedicationApp_API_35` (`emulator-5554`)
- API 33: `MedicationApp_API_33` のコールドブート・初期化済み一時端末
- API 26: `MedicationApp_API_26` のコールドブート・初期化済み一時端末
- 既存ログイン状態: A302SH の本人モードと API 35 の家族モードは変更しない

## 自動 QA 結果

| 層 | 結果 | 主な対象 |
|---|---:|---|
| Android JVM unit tests | 435/435 合格 | Repository、API DTO／契約、時刻・在庫・一部記録、Flavor、安全性 |
| Android lint | Staging／Production とも合格 | Compose、Manifest、リソース、Android API 利用 |
| Android build | Staging Debug／Production Release とも合格 | APK生成、Releaseコンパイル |
| API tests | 342/342 合格 | 76 test files、認証、患者、服薬、在庫、通知、履歴 |
| API quality | 合格 | TypeScript typecheck、ESLint、Next production build |
| Compose UI / API 35 | 299/299 合格 | 4 shards: 67 + 66 + 84 + 82 |
| 互換性 UI / API 33 | 9/9 合格 | 認証、本人／家族記録、在庫、履歴、通知設定、200%文字 |
| 互換性 UI / API 26 | 9/9 合格 | API 33 と同じ主要導線 |

API の DB 依存テストは、実DBへ接続しないローカル専用のダミー接続先を与えて初期化条件を満たした。外部DBへの書き込みは行っていない。

API 35 の機械可読な全件証跡は `android/app/build/reports/connected-ui-shards/results.tsv` と同ディレクトリの4 XMLに保存した。

## 実施した契約・配布物検査

- Staging／Production runtime 分離と誤接続防止
- Runtime credential safety
- Main merge surface
- Android CI runtime contract
- Release gate ledger と SDK policy
- APK／AAB の Manifest、privacy、16 KB alignment、bundle content
- API 26、API 33、A302SH 向け split surface
- Play listing asset contract
- UI shard runner、Play handoff、release evidence 各スクリプト契約

Release gate ledger は `ready=3 / blocked=7` のままである。これは今回のコード不合格ではなく、Play Console や署名済み配布／実端末証跡を必要とする公開前ゲートが未実施であることを表す。

## QAで検出し修正した内容

1. Splash変更時に Manifest のアプリ名が重複文字列へ切り替わり、Play listing contract と不一致になっていた。Manifest を正規の `@string/app_name` に戻した。
2. Flavor導入後の connected UI runner が旧テスト結果パスを参照していた。実際の `connected/debug/flavors/staging` に合わせた。
3. 文字サイズ200%で、認証、本人今日、頓服、服薬詳細の画面外要素をテストが直接検索していた。LazyList を実際にスクロールして到達性を検証するよう統一した。
4. 在庫修正テストがソフトキーボード未表示時にも Back キーを送り、テスト Activity を終了する場合があった。Back キー依存を除去した。
5. API 33／26の狭い表示領域では、履歴や在庫の下部要素がまだ LazyList に生成されない場合があった。リストを対象までスクロールし、低速端末では初期一覧の生成を待つようにした。
6. 認証確認状態の非同期更新を待たずに検証する競合があった。Repository の確定状態を待つようにした。

上記のうち製品コードへ加えたのは、認証選択リストの安定したスクロール識別子と Manifest の正規ラベル修正である。その他は端末サイズ・速度・IME状態に依存しない QA 手順の強化である。

## 端末状態の保全

- API 35 の一時 Staging Flavor／test package はテスト終了時に削除した。
- 既存の `com.afterlifearchive.medmanager` は API 35 上で家族モードの「今日の服薬」画面を再表示できることを確認した。
- A302SH 上の既存 `com.afterlifearchive.medmanager` はアンインストール・データ消去・ログイン変更をしていない。
- API 33／26 の一時端末はテスト後に削除対象パッケージを除去して停止した。

## 追加機能の Staging 実動作確認

初回公開前に追加した「薬を削除しても過去の服薬履歴を残す」と Firebase Analytics について、2026-08-22 JST に次の確認を行った。

### 薬削除後の履歴保持

- ログイン済みの Staging 本人・家族モードだけを使用し、QA用の頓服で服用記録を作成した。
- 家族モードから対象薬を削除すると、使用中の薬一覧と本人モードの「必要な時のお薬」から対象薬が消えた。
- 削除後も本人履歴と家族履歴の双方に、薬名スナップショット、服用時刻、記録者種別が残った。
- Staging API の `/api/health` は HTTP 200。存在しない薬の取得は HTTP 404 となり、`archivedAt` を含む移行後スキーマで稼働していることも確認した。

### Firebase Analytics

- 正規の Staging Flavor `com.afterlifearchive.medmanager.staging` を API 35 エミュレータへ既存アプリと併設した。
- 初期状態と「今はしない」選択後は Firebase SDK が `app measurement disabled` と判定し、アプリイベントを送信しなかった。
- 「許可する」選択後は `screen_viewed(screen_name=mode_select)` と `app_mode_selected(mode=patient)` が Firebase のデバッグログで記録・アップロードされた。
- 送信パラメータに患者名、薬名、服薬内容、メールアドレス、内部IDが含まれないことを確認した。
- 検証後は Staging Flavor のデータを消去して「今はしない」の状態に戻した。既存のログイン済みアプリのデータは消去していない。

## 今回の範囲外・公開前に必要な確認

- Play Console 内部テストへ実際にアップロードした署名済み AAB のインストール
- Play Console の Data safety、審査用アクセス情報、配布ステータス
- FCM の長時間／Doze／OEM省電力下での本人・家族間通知
- TalkBack の実読み上げ順序と日本語の聞き取りやすさ
- 複数メーカー、タブレット、折りたたみ端末での視覚 QA
- Crash／ANR の本番相当期間モニタリング
- Staging API／DBを使う破壊的な全組み合わせ E2E（専用テストデータの再作成を伴うもの）

これらはストア準備フェーズで別途実施し、今回の自動 QA 合格と混同しない。
