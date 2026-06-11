export const metadata = {
  title: "メール確認完了 | お薬見守りアプリ",
  description:
    "お薬見守りアプリの家族アカウント登録メール確認が完了したことを案内するページです。"
};

const steps = [
  {
    title: "アプリを開く",
    body: "下のボタンから、お薬見守りアプリのログイン画面を開きます。"
  },
  {
    title: "家族モードを選ぶ",
    body: "最初の画面で「家族として使う」を選択します。"
  },
  {
    title: "登録したメールでログイン",
    body: "確認したメールアドレスとパスワードでログインすると、利用を開始できます。"
  }
];

const helpItems = [
  "メール確認後もログインできない場合は、アプリを一度閉じてから再度お試しください。",
  "複数回メールを送っている場合は、最新の確認メールから開いたリンクだけが有効です。",
  "リンクの有効期限が切れている場合は、アプリのログイン画面から確認メールを再送してください。"
];

const appLoginUrl = "https://okusuri-mimamori.com/auth/login";

export default function AuthConfirmedPage() {
  return (
    <main className="page-shell">
      <section className="hero" aria-labelledby="confirmed-title">
        <div className="status-panel">
          <div className="status-mark" aria-hidden="true">
            <span>✓</span>
          </div>
          <p className="eyebrow">家族アカウント登録</p>
          <h1 id="confirmed-title">メール確認が完了しました</h1>
          <p className="lead">
            ご登録ありがとうございます。家族モードでログインできる状態になりました。
            続きはお薬見守りアプリで行ってください。
          </p>
          <a className="app-open-button" href={appLoginUrl}>
            アプリでログインへ進む
          </a>
          <p className="app-open-fallback">
            ボタンで開かない場合は、アプリを起動して家族モードからログインしてください。
          </p>
        </div>

        <div className="summary-panel" aria-label="登録完了後にできること">
          <div>
            <p className="summary-label">これで利用できます</p>
            <p className="summary-title">家族として服薬管理を始められます</p>
          </div>
          <ul>
            <li>患者さんの登録</li>
            <li>お薬と服薬時間の設定</li>
            <li>服薬記録と在庫の確認</li>
          </ul>
        </div>
      </section>

      <section className="content-grid" aria-label="次の操作">
        <div className="section-block">
          <div className="section-heading">
            <p className="eyebrow">Next step</p>
            <h2>次にすること</h2>
          </div>
          <ol className="steps">
            {steps.map((step, index) => (
              <li key={step.title}>
                <span className="step-number">{index + 1}</span>
                <div>
                  <h3>{step.title}</h3>
                  <p>{step.body}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>

        <aside className="section-block help-block" aria-label="困った時">
          <div className="section-heading">
            <p className="eyebrow">Support</p>
            <h2>ログインできない時</h2>
          </div>
          <ul className="help-list">
            {helpItems.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
          <p className="note">
            このページは閉じても問題ありません。確認状態はアカウントに保存されています。
          </p>
        </aside>
      </section>

      <style>{`
        :root {
          color-scheme: light;
        }

        * {
          box-sizing: border-box;
        }

        body {
          margin: 0;
        }

        .page-shell {
          min-height: 100vh;
          padding: 48px 20px;
          font-family: -apple-system, BlinkMacSystemFont, "Hiragino Sans", "Yu Gothic", "Helvetica Neue", sans-serif;
          background:
            linear-gradient(180deg, rgba(232, 245, 238, 0.84) 0%, rgba(247, 248, 250, 0.96) 42%, #ffffff 100%),
            #f7f8fa;
          color: #172033;
        }

        .hero,
        .content-grid {
          width: min(1040px, 100%);
          margin: 0 auto;
        }

        .hero {
          display: grid;
          grid-template-columns: minmax(0, 1.15fr) minmax(280px, 0.85fr);
          gap: 18px;
          align-items: stretch;
        }

        .status-panel,
        .summary-panel,
        .section-block {
          border: 1px solid rgba(23, 32, 51, 0.08);
          border-radius: 8px;
          background: rgba(255, 255, 255, 0.92);
          box-shadow: 0 18px 50px rgba(23, 32, 51, 0.08);
        }

        .status-panel {
          padding: 44px;
        }

        .status-mark {
          width: 76px;
          height: 76px;
          display: grid;
          place-items: center;
          border-radius: 50%;
          background: #e2f6ea;
          color: #128043;
          border: 1px solid rgba(18, 128, 67, 0.16);
          margin-bottom: 24px;
        }

        .status-mark span {
          font-size: 42px;
          line-height: 1;
          font-weight: 800;
        }

        .eyebrow,
        .summary-label {
          margin: 0 0 10px;
          color: #39705b;
          font-size: 13px;
          font-weight: 800;
          letter-spacing: 0;
          text-transform: uppercase;
        }

        h1,
        h2,
        h3,
        p {
          overflow-wrap: anywhere;
        }

        h1 {
          margin: 0;
          max-width: 720px;
          font-size: 42px;
          line-height: 1.18;
          letter-spacing: 0;
        }

        .lead {
          margin: 18px 0 0;
          max-width: 680px;
          color: #526173;
          font-size: 18px;
          line-height: 1.8;
        }

        .app-open-button {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          min-height: 52px;
          margin-top: 28px;
          padding: 0 24px;
          border-radius: 8px;
          background: #167a4a;
          color: #ffffff;
          font-size: 16px;
          font-weight: 800;
          line-height: 1.4;
          text-decoration: none;
          box-shadow: 0 12px 28px rgba(22, 122, 74, 0.22);
        }

        .app-open-button:focus-visible {
          outline: 3px solid rgba(22, 122, 74, 0.28);
          outline-offset: 3px;
        }

        .app-open-fallback {
          margin: 14px 0 0;
          max-width: 560px;
          color: #657386;
          font-size: 14px;
          line-height: 1.7;
        }

        .summary-panel {
          display: flex;
          flex-direction: column;
          justify-content: space-between;
          padding: 32px;
          background: #173d35;
          color: #ffffff;
        }

        .summary-label {
          color: #b9ead5;
        }

        .summary-title {
          margin: 0;
          font-size: 25px;
          line-height: 1.45;
          font-weight: 800;
        }

        .summary-panel ul {
          display: grid;
          gap: 12px;
          margin: 30px 0 0;
          padding: 0;
          list-style: none;
        }

        .summary-panel li {
          padding: 12px 14px;
          border-radius: 8px;
          background: rgba(255, 255, 255, 0.1);
          color: #edfdf5;
          font-size: 15px;
          line-height: 1.5;
        }

        .content-grid {
          display: grid;
          grid-template-columns: minmax(0, 1fr) minmax(300px, 0.78fr);
          gap: 18px;
          margin-top: 18px;
        }

        .section-block {
          padding: 30px;
        }

        .section-heading h2 {
          margin: 0;
          font-size: 24px;
          line-height: 1.35;
          letter-spacing: 0;
        }

        .steps {
          display: grid;
          gap: 16px;
          margin: 24px 0 0;
          padding: 0;
          list-style: none;
        }

        .steps li {
          display: grid;
          grid-template-columns: 42px minmax(0, 1fr);
          gap: 14px;
          align-items: start;
          padding: 18px;
          border: 1px solid #e6ebf0;
          border-radius: 8px;
          background: #fbfcfd;
        }

        .step-number {
          width: 42px;
          height: 42px;
          display: grid;
          place-items: center;
          border-radius: 50%;
          background: #dff3e8;
          color: #12653b;
          font-size: 18px;
          font-weight: 800;
        }

        .steps h3 {
          margin: 0 0 6px;
          font-size: 17px;
          line-height: 1.45;
        }

        .steps p,
        .help-list li,
        .note {
          color: #526173;
          font-size: 15px;
          line-height: 1.75;
        }

        .steps p {
          margin: 0;
        }

        .help-block {
          background: #fffdf7;
        }

        .help-list {
          display: grid;
          gap: 14px;
          margin: 24px 0 0;
          padding: 0;
          list-style: none;
        }

        .help-list li {
          position: relative;
          padding-left: 24px;
        }

        .help-list li::before {
          content: "";
          position: absolute;
          left: 0;
          top: 0.72em;
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: #d39120;
        }

        .note {
          margin: 24px 0 0;
          padding: 14px 16px;
          border-radius: 8px;
          background: #ffffff;
          border: 1px solid #f0e5cb;
        }

        @media (max-width: 760px) {
          .page-shell {
            padding: 22px 14px;
          }

          .hero,
          .content-grid {
            grid-template-columns: 1fr;
          }

          .status-panel,
          .summary-panel,
          .section-block {
            padding: 24px;
          }

          h1 {
            font-size: 32px;
          }

          .lead {
            font-size: 16px;
          }

          .app-open-button {
            width: 100%;
          }

          .summary-title {
            font-size: 21px;
          }
        }

        @media (max-width: 420px) {
          .steps li {
            grid-template-columns: 1fr;
          }

          .status-mark {
            width: 66px;
            height: 66px;
          }

          .status-mark span {
            font-size: 36px;
          }
        }
      `}</style>
    </main>
  );
}
