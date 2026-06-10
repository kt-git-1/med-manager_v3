export const metadata = {
  title: "お薬見守り | 家族で服薬を見守るアプリ",
  description:
    "お薬見守りは、家族で服薬予定・服薬記録・お薬の在庫を確認できるアプリです。"
};

const features = [
  {
    title: "今日のお薬を確認",
    body: "朝・昼・夕・寝る前など、時間帯ごとの服薬予定を見やすく整理します。"
  },
  {
    title: "家族で記録を見守る",
    body: "本人の服薬記録を家族が確認でき、飲み忘れに早く気づけます。"
  },
  {
    title: "在庫切れを防ぐ",
    body: "お薬の残量を確認し、補充が必要なものを見落としにくくします。"
  }
];

export default function Home() {
  return (
    <main className="home-shell">
      <section className="hero" aria-labelledby="home-title">
        <div className="hero-copy">
          <p className="eyebrow">お薬見守り</p>
          <h1 id="home-title">飲み忘れに、家族で気づけるように。</h1>
          <p className="lead">
            お薬見守りは、服薬予定・服薬記録・お薬の在庫を家族で確認するためのアプリです。
            毎日の服薬を、本人だけに任せきりにしない仕組みをつくります。
          </p>
        </div>

        <div className="status-card" aria-label="メール確認後の案内">
          <p className="status-label">登録メールから開いた方へ</p>
          <h2>確認後はアプリに戻ってください</h2>
          <p>
            メール確認が完了したら、このページを閉じて、お薬見守りアプリの家族モードからログインしてください。
          </p>
        </div>
      </section>

      <section className="feature-grid" aria-label="主な機能">
        {features.map((feature) => (
          <article className="feature" key={feature.title}>
            <h2>{feature.title}</h2>
            <p>{feature.body}</p>
          </article>
        ))}
      </section>

      <section className="support-band" aria-label="ご案内">
        <div>
          <h2>このサイトについて</h2>
          <p>
            このドメインは、お薬見守りアプリの登録確認ページやサポート情報のために利用しています。
            服薬管理の操作は、インストール済みのアプリから行ってください。
          </p>
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
          padding: 48px 20px;
          font-family: -apple-system, BlinkMacSystemFont, "Hiragino Sans", "Yu Gothic", "Helvetica Neue", sans-serif;
          background:
            linear-gradient(180deg, rgba(232, 245, 238, 0.86) 0%, rgba(247, 248, 250, 0.98) 44%, #ffffff 100%),
            #f7f8fa;
          color: #172033;
        }

        .hero,
        .feature-grid,
        .support-band {
          width: min(1040px, 100%);
          margin: 0 auto;
        }

        .hero {
          display: grid;
          grid-template-columns: minmax(0, 1.25fr) minmax(300px, 0.75fr);
          gap: 18px;
          align-items: stretch;
        }

        .hero-copy,
        .status-card,
        .feature,
        .support-band {
          border: 1px solid rgba(23, 32, 51, 0.08);
          border-radius: 8px;
          background: rgba(255, 255, 255, 0.94);
          box-shadow: 0 18px 50px rgba(23, 32, 51, 0.08);
        }

        .hero-copy {
          padding: 48px;
        }

        .eyebrow,
        .status-label {
          margin: 0 0 12px;
          color: #39705b;
          font-size: 13px;
          font-weight: 800;
          letter-spacing: 0;
        }

        h1,
        h2,
        p {
          overflow-wrap: anywhere;
        }

        h1 {
          margin: 0;
          max-width: 760px;
          font-size: 44px;
          line-height: 1.18;
          letter-spacing: 0;
        }

        .lead {
          margin: 20px 0 0;
          max-width: 720px;
          color: #526173;
          font-size: 18px;
          line-height: 1.85;
        }

        .status-card {
          display: flex;
          flex-direction: column;
          justify-content: center;
          padding: 32px;
          background: #173d35;
          color: #ffffff;
        }

        .status-label {
          color: #b9ead5;
        }

        .status-card h2 {
          margin: 0;
          font-size: 26px;
          line-height: 1.4;
          letter-spacing: 0;
        }

        .status-card p {
          margin: 16px 0 0;
          color: #edfdf5;
          font-size: 15px;
          line-height: 1.8;
        }

        .feature-grid {
          display: grid;
          grid-template-columns: repeat(3, minmax(0, 1fr));
          gap: 18px;
          margin-top: 18px;
        }

        .feature {
          padding: 26px;
        }

        .feature h2,
        .support-band h2 {
          margin: 0;
          font-size: 20px;
          line-height: 1.45;
          letter-spacing: 0;
        }

        .feature p,
        .support-band p {
          margin: 12px 0 0;
          color: #526173;
          font-size: 15px;
          line-height: 1.8;
        }

        .support-band {
          margin-top: 18px;
          padding: 28px 32px;
          background: #fffdf7;
        }

        @media (max-width: 820px) {
          .home-shell {
            padding: 22px 14px;
          }

          .hero,
          .feature-grid {
            grid-template-columns: 1fr;
          }

          .hero-copy,
          .status-card,
          .feature,
          .support-band {
            padding: 24px;
          }

          h1 {
            font-size: 33px;
          }

          .lead {
            font-size: 16px;
          }

          .status-card h2 {
            font-size: 22px;
          }
        }
      `}</style>
    </main>
  );
}
