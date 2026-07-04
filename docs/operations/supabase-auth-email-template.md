# Supabase Auth Email Template

本番 Supabase で家族アカウントを作成するときに送られる、メールアドレス確認メールの設定内容です。

設定場所:

- Supabase Dashboard > Authentication > Emails > Confirm signup

件名:

```text
【お薬見守り】メールアドレス確認のお願い
```

HTML body:

```html
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">
  お薬見守りの家族アカウント作成を完了するため、メールアドレスの確認をお願いします。
</div>

<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;margin:0;padding:0;background:#f3f7fb;">
  <tr>
    <td align="center" style="padding:32px 16px;font-family:-apple-system,BlinkMacSystemFont,'Helvetica Neue',Arial,sans-serif;color:#111827;">
      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;max-width:560px;background:#ffffff;border-radius:18px;overflow:hidden;box-shadow:0 12px 32px rgba(15,23,42,0.08);">
        <tr>
          <td bgcolor="#2563eb" style="padding:28px 28px 20px;background:#2563eb;background:linear-gradient(135deg,#2563eb 0%,#38bdf8 100%);color:#ffffff;">
            <div style="font-size:14px;line-height:1.4;font-weight:700;letter-spacing:0.04em;opacity:0.95;">
              お薬見守り
            </div>
            <h1 style="margin:10px 0 0;font-size:24px;line-height:1.4;font-weight:800;">
              メールアドレス確認のお願い
            </h1>
          </td>
        </tr>

        <tr>
          <td style="padding:30px 28px 28px;line-height:1.8;">
            <p style="margin:0 0 18px;font-size:16px;line-height:1.8;color:#111827;">
              お薬見守りアプリの家族アカウント作成をお申し込みいただき、ありがとうございます。
            </p>

            <p style="margin:0 0 22px;font-size:16px;line-height:1.8;color:#111827;">
              アカウント作成を完了するために、下のボタンからメールアドレスの確認をお願いします。
            </p>

            <table role="presentation" cellspacing="0" cellpadding="0" border="0" align="center" style="margin:30px auto;">
              <tr>
                <td align="center" bgcolor="#2563eb" style="border-radius:999px;">
                  <a href="{{ .ConfirmationURL }}" style="display:inline-block;padding:15px 28px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:999px;font-size:16px;line-height:1.3;font-weight:700;">
                    メールアドレスを確認する
                  </a>
                </td>
              </tr>
            </table>

            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;margin:0 0 24px;background:#eff6ff;border:1px solid #bfdbfe;border-radius:14px;">
              <tr>
                <td style="padding:16px 18px;">
                  <p style="margin:0;font-size:14px;line-height:1.7;color:#1e3a8a;">
                    確認が完了したら、お薬見守りアプリに戻り、登録したメールアドレスとパスワードでログインしてください。
                  </p>
                </td>
              </tr>
            </table>

            <p style="margin:0;font-size:13px;line-height:1.7;color:#6b7280;">
              ボタンが開けない場合は、メールアプリやブラウザを変えてもう一度お試しください。
            </p>
          </td>
        </tr>

        <tr>
          <td style="padding:20px 28px 24px;background:#f9fafb;border-top:1px solid #e5e7eb;">
            <p style="margin:0 0 8px;font-size:12px;line-height:1.7;color:#6b7280;">
              このメールに心当たりがない場合は、どなたかが誤ってメールアドレスを入力した可能性があります。何も操作せず、このメールを破棄してください。
            </p>
            <p style="margin:0;font-size:12px;line-height:1.7;color:#6b7280;">
              このメールは送信専用です。
            </p>
          </td>
        </tr>
      </table>
    </td>
  </tr>
</table>
```

注意:

- CTA の `href="{{ .ConfirmationURL }}"` は Supabase の確認リンクとして必要です。
- 本文中に `{{ .ConfirmationURL }}` を表示しないでください。Supabase のプレビューや一部の見え方で、テンプレート変数がそのままユーザーに見えることがあります。
- 実務メールとしての互換性を優先し、主要レイアウトは `table` と inline style で組んでいます。
- 確認後の遷移先は iOS 側の `EMAIL_CONFIRMATION_REDIRECT_URL` と同じ `https://okusuri-mimamori.com/auth/confirmed` を使います。
