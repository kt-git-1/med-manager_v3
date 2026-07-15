# med-manager API

## 飲み忘れPush通知の定期実行

家族への飲み忘れ通知は、Supabase Cronから次のAPIを5分ごとに呼び出す。

```text
GET /api/cron/missed-dose-notifications
Authorization: Bearer <CRON_SECRET>
```

ステージングと本番で別々の`CRON_SECRET`をVercelのProduction環境変数に設定する。対応するSupabaseへCronを登録するときは、APIディレクトリで次を実行する。

```bash
DIRECT_URL="..." \
CRON_SECRET="..." \
MISSED_DOSE_CRON_ENDPOINT="https://example.com/api/cron/missed-dose-notifications" \
npm run cron:configure:missed-dose
```

スクリプトは`pg_cron`と`pg_net`を有効化し、URLと認証値をSupabase Vaultへ保存する。同名のCronを安全に置き換えるため、再実行してよい。
