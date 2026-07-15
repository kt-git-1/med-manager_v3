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
    <main className="page-shell confirmed-page">
      <SiteHeader current="legal" />
      <section className="confirmed-hero" aria-labelledby="confirmed-title">
        <div className="content-width confirmed-hero-inner">
          <div className="confirmed-message">
            <span className="confirmed-check" aria-hidden="true">✓</span>
            <div>
              <p className="section-label">家族アカウントの登録</p>
              <h1 id="confirmed-title">メール確認が<br />完了しました</h1>
              <p className="confirmed-lead">
                登録したメールアドレスで、家族モードにログインできます。続きはお薬見守りアプリで行ってください。
              </p>
              <div className="confirmed-actions">
                <a className="primary-button" href={appLoginUrl}>
                  アプリでログインへ進む
                </a>
                <a className="confirmed-guide-link" href="/guide">
                  使い方を確認する
                </a>
              </div>
              <small>
                ボタンで開かない場合は、アプリを起動して「家族として使う」からログインしてください。
              </small>
            </div>
          </div>
          <aside className="confirmed-ready">
            <p className="confirmed-card-label">登録後にできること</p>
            <h2>ご家族のお薬を見守れます</h2>
            <ul>
              <li>見守る方の登録</li>
              <li>お薬と服薬時間の設定</li>
              <li>服薬記録と在庫の確認</li>
            </ul>
          </aside>
        </div>
      </section>

      <section className="confirmed-steps" aria-labelledby="confirmed-steps-title">
        <div className="content-width">
          <p className="section-label">次にすること</p>
          <h2 className="section-title" id="confirmed-steps-title">アプリで利用を始めるまで</h2>
          <p className="confirmed-section-intro">次の3つの手順で、すぐに見守りを始められます。</p>
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
        </div>
      </section>

      <section className="confirmed-support" aria-labelledby="confirmed-help-title">
        <div className="content-width confirmed-support-inner">
          <div>
            <p className="section-label">うまく進められないとき</p>
            <h2 id="confirmed-help-title">ログインできない場合</h2>
          </div>
          <div className="confirmed-help">
            <p>
              アプリを一度閉じてから、もう一度お試しください。確認メールを複数回送った場合は、最新のメールにあるリンクだけが有効です。
            </p>
            <a href="/support">詳しいサポートを見る</a>
          </div>
        </div>
      </section>
      <SiteFooter />
    </main>
  );
}
