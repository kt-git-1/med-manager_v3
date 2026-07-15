import { SiteFooter } from "../../SiteFooter";
import { SiteHeader } from "../../SiteHeader";

export const metadata = {
  title: "メール確認完了 | お薬見守り",
  description: "家族アカウントのメール確認完了後に行う操作をご案内します。"
};

const appLoginUrl = "okusurimimamori://auth/login";
const steps = [
  ["アプリを開く", "下のボタンから、お薬見守りアプリのログイン画面を開きます。"],
  ["家族モードを選ぶ", "最初の画面で「家族として使う」を選択します。"],
  ["登録したメールでログイン", "確認したメールアドレスとパスワードでログインします。"]
];

export default function AuthConfirmedPage() {
  return (
    <main className="page-shell">
      <SiteHeader current="legal" />
      <div className="confirmed-shell">
        <section className="confirmed-hero" aria-labelledby="confirmed-title">
          <div>
            <p className="section-label">登録の確認ができました</p>
            <h1 id="confirmed-title">メール確認が完了しました</h1>
            <p>
              家族モードでログインできる状態になりました。続きはお薬見守りアプリで行ってください。
            </p>
            <a className="primary-button" href={appLoginUrl}>
              アプリでログインへ進む
            </a>
            <small>
              ボタンで開かない場合は、アプリを起動して家族モードからログインしてください。
            </small>
          </div>
          <aside>
            <strong>次にできること</strong>
            <ul>
              <li>見守る方の登録</li>
              <li>お薬と服薬時間の設定</li>
              <li>服薬記録と在庫の確認</li>
            </ul>
          </aside>
        </section>

        <section className="confirmed-steps">
          <p className="section-label">次にすること</p>
          <h2 className="section-title">アプリで利用を始めるまで</h2>
          <ol>
            {steps.map(([title, body], index) => (
              <li key={title}>
                <span>{index + 1}</span>
                <div>
                  <strong>{title}</strong>
                  <p>{body}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>

        <section className="notice-box confirmed-help">
          <strong>ログインできないとき</strong>
          <p>
            アプリを一度閉じてから再度お試しください。複数回メールを送った場合は、最新の確認メールから開いたリンクだけが有効です。
          </p>
          <a href="/support">詳しいサポートを見る</a>
        </section>
      </div>
      <SiteFooter />
    </main>
  );
}
