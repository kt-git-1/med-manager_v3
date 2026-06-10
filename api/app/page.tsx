export const metadata = {
  title: "お薬見守り",
  description: "お薬見守りアプリの公開案内ページです。"
};

export default function Home() {
  return (
    <main className="home-shell">
      <section className="home-panel" aria-labelledby="home-title">
        <p className="eyebrow">お薬見守り</p>
        <h1 id="home-title">家族で服薬を見守るためのアプリです</h1>
        <p className="lead">
          このサイトは、お薬見守りアプリの登録確認やサポート用ページです。
          服薬管理はインストール済みのアプリからご利用ください。
        </p>
        <div className="notice">
          <strong>登録メールから開いた方へ</strong>
          <span>
            メール確認が完了した後は、このページを閉じてアプリの家族モードからログインしてください。
          </span>
        </div>
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

        .home-shell {
          min-height: 100vh;
          display: grid;
          place-items: center;
          padding: 24px;
          font-family: -apple-system, BlinkMacSystemFont, "Hiragino Sans", "Yu Gothic", "Helvetica Neue", sans-serif;
          background:
            linear-gradient(180deg, rgba(232, 245, 238, 0.82), rgba(255, 255, 255, 0.98)),
            #f7f8fa;
          color: #172033;
        }

        .home-panel {
          width: min(680px, 100%);
          padding: 40px;
          border: 1px solid rgba(23, 32, 51, 0.08);
          border-radius: 8px;
          background: rgba(255, 255, 255, 0.94);
          box-shadow: 0 18px 50px rgba(23, 32, 51, 0.08);
        }

        .eyebrow {
          margin: 0 0 12px;
          color: #39705b;
          font-size: 13px;
          font-weight: 800;
          letter-spacing: 0;
        }

        h1 {
          margin: 0;
          font-size: 36px;
          line-height: 1.25;
          letter-spacing: 0;
          overflow-wrap: anywhere;
        }

        .lead {
          margin: 18px 0 0;
          color: #526173;
          font-size: 17px;
          line-height: 1.8;
          overflow-wrap: anywhere;
        }

        .notice {
          display: grid;
          gap: 8px;
          margin-top: 28px;
          padding: 18px;
          border-radius: 8px;
          background: #f3faf6;
          border: 1px solid #d8efe3;
          color: #25483b;
          line-height: 1.7;
        }

        .notice strong,
        .notice span {
          overflow-wrap: anywhere;
        }

        @media (max-width: 520px) {
          .home-shell {
            padding: 16px;
          }

          .home-panel {
            padding: 26px;
          }

          h1 {
            font-size: 29px;
          }

          .lead {
            font-size: 16px;
          }
        }
      `}</style>
    </main>
  );
}
