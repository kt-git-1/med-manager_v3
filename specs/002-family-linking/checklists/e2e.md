# E2E Checklist: Family Linking (002)

## Scenarios

- [ ] 家族がログイン→患者作成（displayName必須）→患者一覧に表示
- [ ] 家族がコード発行→患者が入力→token取得→001の閲覧API成功
- [ ] refreshが成功し旧tokenが無効（回転）
- [ ] 家族が解除→既存tokenで閲覧APIもrefreshも失敗
- [ ] コード再利用は失敗、期限切れ/使用済み/無効は404に寄せる
- [ ] patientId単位の試行回数超過でロックアウト（429）
