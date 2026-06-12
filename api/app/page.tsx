export const metadata = {
  title: "お薬見守り | 家族で服薬を見守るアプリ",
  description:
    "お薬見守りは、服薬予定・服薬記録・お薬の在庫を家族で確認できるアプリです。"
};

const featureRows = [
  {
    label: "登録",
    title: "家族がお薬と服薬時間を設定",
    body: "家族モードからお薬名・飲む量・朝昼夕などの服薬時間を登録できます。本人が迷わず記録できるよう、先に予定を整えられます。"
  },
  {
    label: "見守り",
    title: "家族が記録状況を確認",
    body: "家族モードでは、服薬済み・未記録・飲み忘れを確認できます。離れていても、必要なタイミングで気づけます。"
  },
  {
    label: "予定と在庫",
    title: "今日の予定と残量をまとめて確認",
    body: "登録した服薬時間に沿って、今日飲むお薬を時間帯ごとに表示します。お薬の残り数も管理し、補充が必要なものを見落としにくくします。"
  }
];

const demoRows = [
  { time: "07:30", slot: "朝", name: "血圧のお薬", amount: "1錠", status: "服用済み" },
  { time: "12:30", slot: "昼", name: "胃のお薬", amount: "1錠", status: "これから" },
  { time: "20:30", slot: "夜", name: "眠る前のお薬", amount: "0.5錠", status: "未記録" }
];

export default function Home() {
  return (
    <main className="page">
      <section className="hero" aria-labelledby="home-title">
        <div className="hero-bg" aria-hidden="true">
          <div className="iphone-shell hero-phone">
            <div className="iphone-frame">
              <div className="dynamic-island" />
              <div className="ios-status">
                <span>9:41</span>
                <div>
                  <span />
                  <span />
                  <span />
                </div>
              </div>
              <div className="iphone-screen">
                <div className="demo-caption">
                  <span>本人画面</span>
                  <strong>次に飲む薬がすぐ分かる</strong>
                </div>
                <div className="phone-top">
                  <span>お薬見守り</span>
                  <strong>今日のお薬</strong>
                </div>
                <div className="next-card">
                  <span>次に飲むお薬</span>
                  <strong>昼 12:30</strong>
                  <p>胃のお薬 1錠</p>
                </div>
                <button className="demo-action" type="button">
                  飲んだらここを押す
                </button>
                <div className="progress-panel">
                  <span>家族にも共有されます</span>
                  <strong>今日 2/3回分 完了</strong>
                  <div className="progress-track">
                    <span />
                  </div>
                </div>
                <div className="family-card">
                  <span>家族画面</span>
                  <strong>昼のお薬がまだ未記録です</strong>
                  <p>必要なときだけ状況を確認できます。</p>
                </div>
                <div className="home-indicator" />
              </div>
            </div>
          </div>
        </div>

        <div className="hero-copy">
          <p className="eyebrow">お薬見守り</p>
          <h1 id="home-title">飲み忘れに、家族で気づけるように。</h1>
          <p>
            お薬見守りは、家族がお薬と服薬時間を登録し、毎日の服薬予定・服薬記録・お薬の在庫を確認するためのアプリです。
            本人の記録しやすさと、家族の見守りやすさを両方大切にしています。
          </p>
        </div>
      </section>

      <section className="overview" aria-label="アプリの概要">
        <div className="section-heading">
          <p className="eyebrow">Overview</p>
          <h2>毎日の服薬を、見える形にします</h2>
        </div>
        <div className="feature-grid">
          {featureRows.map((feature) => (
            <article className="feature" key={feature.title}>
              <span>{feature.label}</span>
              <h3>{feature.title}</h3>
              <p>{feature.body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="demo-section" aria-labelledby="demo-title">
        <div className="section-heading">
          <p className="eyebrow">Demo</p>
          <h2 id="demo-title">画面イメージ</h2>
        </div>

        <div className="demo-layout">
          <div className="demo-copy">
            <h3>本人画面は、大きく迷わない操作に。</h3>
            <p>
              家族が登録したお薬と服薬時間をもとに、本人画面では予定の時間帯ごとにお薬をまとめます。
              飲めたら大きなボタンで記録します。
              家族側では同じ記録を確認し、飲み忘れや未記録に気づけます。
            </p>
            <div className="mini-metrics" aria-label="デモ指標">
              <div>
                <strong>薬登録</strong>
                <span>家族が設定</span>
              </div>
              <div>
                <strong>朝・昼・夜</strong>
                <span>服薬時間を設定</span>
              </div>
              <div>
                <strong>2/3回分</strong>
                <span>進捗を表示</span>
              </div>
              <div>
                <strong>残り12錠</strong>
                <span>在庫も確認</span>
              </div>
            </div>
          </div>

          <div className="demo-stage" aria-label="アプリ画面デモ">
            <div className="demo-phone mode-phone main-demo">
              <div className="phone-top">
                <span>本人モード</span>
                <strong>今日のお薬</strong>
              </div>
              <div className="next-slot">
                <span>次のお薬</span>
                <strong>昼 12:30</strong>
                <p>胃のお薬 1錠</p>
              </div>
              <button className="demo-action" type="button">
                飲んだら記録する
              </button>
              <div className="slot-list">
                {demoRows.slice(0, 2).map((row) => (
                  <div className="dose-row" key={row.name}>
                    <div>
                      <span className="time">{row.time}</span>
                      <strong>{row.slot}</strong>
                    </div>
                    <div>
                      <strong>{row.name}</strong>
                      <span>{row.amount}</span>
                    </div>
                    <em
                      className={
                        row.status === "服用済み"
                          ? "status-done"
                          : row.status === "未記録"
                            ? "status-missed"
                            : "status-pending"
                      }
                    >
                      {row.status}
                    </em>
                  </div>
                ))}
              </div>
            </div>

            <div className="demo-phone mode-phone watcher-demo">
              <div className="phone-top">
                <span>家族モード</span>
                <strong>見守り</strong>
              </div>
              <div className="watch-summary">
                <span>山田 太郎さん</span>
                <strong>今日 2/3回分 完了</strong>
                <div className="progress-track">
                  <span />
                </div>
              </div>
              <div className="watch-alert">
                <span>確認が必要</span>
                <strong>昼のお薬がまだ未記録です</strong>
                <p>本人画面で記録されると、家族画面にも反映されます。</p>
              </div>
              <div className="watch-list">
                <div>
                  <span>朝 07:30</span>
                  <strong>服用済み</strong>
                </div>
                <div>
                  <span>昼 12:30</span>
                  <strong>未記録</strong>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="mail-note" aria-label="登録メールから開いた方へ">
        <div>
          <p className="eyebrow">For signup</p>
          <h2>登録メールから開いた方へ</h2>
          <p>
            メール確認が完了した後は、このページを閉じて、お薬見守りアプリの家族モードからログインしてください。
            確認状態はアカウントに保存されています。
          </p>
        </div>
      </section>

      <footer className="footer-links" aria-label="公開情報">
        <a href="/privacy">プライバシーポリシー</a>
        <a href="/terms">利用規約</a>
        <a href="/support">サポート</a>
      </footer>

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

        .page {
          min-height: 100vh;
          font-family: -apple-system, BlinkMacSystemFont, "Hiragino Sans", "Yu Gothic", "Helvetica Neue", sans-serif;
          background: #f6f8f7;
          color: #12221d;
        }

        .hero {
          position: relative;
          min-height: 640px;
          display: grid;
          align-items: end;
          overflow: hidden;
          padding: 72px 20px;
          background:
            linear-gradient(90deg, rgba(14, 41, 35, 0.94) 0%, rgba(14, 41, 35, 0.84) 44%, rgba(14, 41, 35, 0.12) 100%),
            radial-gradient(circle at 76% 28%, rgba(120, 212, 170, 0.42), transparent 34%),
            linear-gradient(135deg, #173d35 0%, #e6f4ed 100%);
        }

        .hero-bg {
          position: absolute;
          inset: 0;
          display: flex;
          justify-content: flex-end;
          align-items: center;
          padding: 54px max(32px, calc((100vw - 1040px) / 2));
          pointer-events: none;
        }

        .hero-copy,
        .overview,
          .demo-section,
          .mail-note,
          .footer-links {
            width: min(1040px, 100%);
            margin: 0 auto;
          }

        .hero-copy {
          position: relative;
          z-index: 1;
          color: #ffffff;
        }

        .eyebrow {
          margin: 0 0 12px;
          color: #2f745d;
          font-size: 13px;
          font-weight: 800;
          letter-spacing: 0;
        }

        .hero .eyebrow {
          color: #b9ead5;
        }

        h1,
        h2,
        h3,
        p,
        strong,
        span,
        em {
          overflow-wrap: anywhere;
        }

        h1 {
          max-width: 650px;
          margin: 0;
          font-size: 58px;
          line-height: 1.08;
          letter-spacing: 0;
        }

        .hero-copy p:not(.eyebrow) {
          max-width: 660px;
          margin: 22px 0 0;
          color: #e8fff5;
          font-size: 18px;
          line-height: 1.9;
        }

        .overview,
        .demo-section,
        .mail-note {
          padding: 72px 20px 0;
        }

        .footer-links {
          display: flex;
          flex-wrap: wrap;
          gap: 14px;
          padding: 40px 20px 56px;
        }

        .footer-links a {
          color: #24614d;
          font-weight: 800;
          text-decoration: none;
        }

        .section-heading {
          display: grid;
          gap: 6px;
          margin-bottom: 24px;
        }

        .section-heading h2,
        .mail-note h2 {
          margin: 0;
          font-size: 34px;
          line-height: 1.25;
          letter-spacing: 0;
        }

        .feature-grid {
          display: grid;
          grid-template-columns: repeat(3, minmax(0, 1fr));
          gap: 16px;
        }

        .feature,
        .mail-note > div {
          border: 1px solid rgba(18, 34, 29, 0.08);
          border-radius: 8px;
          background: #ffffff;
          box-shadow: 0 18px 50px rgba(18, 34, 29, 0.08);
        }

        .feature {
          padding: 26px;
        }

        .feature span {
          display: inline-flex;
          margin-bottom: 18px;
          padding: 6px 10px;
          border-radius: 999px;
          background: #e7f5ee;
          color: #2f745d;
          font-size: 13px;
          font-weight: 800;
        }

        .feature h3 {
          margin: 0;
          font-size: 20px;
          line-height: 1.45;
          letter-spacing: 0;
        }

        .feature p,
        .demo-copy p,
        .mail-note p {
          margin: 12px 0 0;
          color: #52635d;
          font-size: 15px;
          line-height: 1.85;
        }

        .demo-layout {
          display: grid;
          grid-template-columns: minmax(0, 0.8fr) minmax(360px, 1.2fr);
          gap: 24px;
          align-items: center;
        }

        .demo-copy h3 {
          margin: 0;
          font-size: 29px;
          line-height: 1.3;
          letter-spacing: 0;
        }

        .mini-metrics {
          display: grid;
          gap: 10px;
          margin-top: 24px;
        }

        .mini-metrics div {
          display: grid;
          gap: 4px;
          padding: 14px 16px;
          border-left: 4px solid #3c8a69;
          background: #ffffff;
        }

        .mini-metrics strong {
          font-size: 16px;
        }

        .mini-metrics span {
          color: #667771;
          font-size: 13px;
        }

        .demo-stage {
          position: relative;
          display: grid;
          grid-template-columns: repeat(2, minmax(220px, 1fr));
          gap: 18px;
          align-items: center;
          justify-content: center;
          padding: 28px;
          border-radius: 8px;
          background:
            linear-gradient(135deg, #e7f5ee 0%, #fffdf7 100%);
          border: 1px solid rgba(18, 34, 29, 0.08);
          overflow: hidden;
        }

        .demo-phone {
          width: min(268px, 100%);
          justify-self: center;
          border-radius: 34px;
          padding: 12px;
          background: #10231e;
          box-shadow: 0 28px 70px rgba(18, 34, 29, 0.28);
          color: #12221d;
        }

        .mode-phone {
          aspect-ratio: 393 / 852;
          display: grid;
          align-content: start;
          gap: 9px;
          overflow: hidden;
        }

        .mode-phone .phone-top {
          padding: 14px;
        }

        .mode-phone .phone-top strong {
          font-size: 21px;
        }

        .mode-phone .next-slot,
        .mode-phone .watch-summary,
        .mode-phone .watch-alert {
          padding: 14px;
        }

        .mode-phone .next-slot strong,
        .mode-phone .watch-summary strong {
          font-size: 19px;
        }

        .mode-phone .demo-action {
          min-height: 44px;
          margin-top: 0;
        }

        .mode-phone .slot-list {
          margin-top: 0;
        }

        .mode-phone .dose-row {
          grid-template-columns: 44px minmax(0, 1fr);
        }

        .mode-phone .dose-row em {
          grid-column: 2;
          width: fit-content;
        }

        .iphone-shell {
          width: 280px;
          aspect-ratio: 393 / 852;
          border-radius: 52px;
          padding: 10px;
          background:
            linear-gradient(145deg, #1e2b28 0%, #07110f 54%, #293834 100%);
          box-shadow:
            0 34px 80px rgba(4, 18, 15, 0.42),
            inset 0 0 0 1px rgba(255, 255, 255, 0.12);
        }

        .iphone-frame {
          position: relative;
          height: 100%;
          overflow: hidden;
          border-radius: 44px;
          padding: 18px 12px 12px;
          background: #f5f8f6;
          box-shadow: inset 0 0 0 1px rgba(18, 34, 29, 0.08);
        }

        .dynamic-island {
          position: absolute;
          top: 13px;
          left: 50%;
          z-index: 3;
          width: 92px;
          height: 28px;
          border-radius: 999px;
          background: #07110f;
          transform: translateX(-50%);
          box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.05);
        }

        .ios-status {
          position: relative;
          z-index: 2;
          display: flex;
          align-items: center;
          justify-content: space-between;
          height: 34px;
          padding: 0 12px;
          color: #10231e;
          font-size: 13px;
          font-weight: 800;
        }

        .ios-status div {
          display: flex;
          align-items: center;
          gap: 5px;
        }

        .ios-status div span {
          display: block;
          background: #10231e;
        }

        .ios-status div span:nth-child(1) {
          width: 16px;
          height: 10px;
          border-radius: 3px 3px 2px 2px;
          clip-path: polygon(0 100%, 0 70%, 18% 70%, 18% 52%, 38% 52%, 38% 34%, 60% 34%, 60% 16%, 82% 16%, 82% 0, 100% 0, 100% 100%);
        }

        .ios-status div span:nth-child(2) {
          width: 15px;
          height: 11px;
          border-radius: 999px 999px 2px 2px;
          clip-path: polygon(50% 0, 100% 38%, 84% 58%, 50% 34%, 16% 58%, 0 38%);
        }

        .ios-status div span:nth-child(3) {
          width: 19px;
          height: 10px;
          border-radius: 3px;
          box-shadow: inset -4px 0 0 #f5f8f6;
        }

        .iphone-screen {
          display: grid;
          grid-template-rows: auto auto auto auto auto auto 1fr;
          gap: 8px;
          height: calc(100% - 34px);
          padding-top: 12px;
        }

        .home-indicator {
          align-self: end;
          width: 112px;
          height: 5px;
          margin: 4px auto 0;
          border-radius: 999px;
          background: #10231e;
          opacity: 0.78;
        }

        .hero-phone {
          transform: none;
        }

        .main-demo {
          position: relative;
          z-index: 2;
        }

        .phone-top,
        .progress-panel,
        .next-card,
        .family-card,
        .next-slot,
        .slot-list,
        .demo-action {
          background: #f8fbfa;
        }

        .demo-caption {
          display: grid;
          gap: 4px;
          padding: 8px 4px 0;
        }

        .demo-caption span {
          color: #3c8a69;
          font-size: 11px;
          font-weight: 800;
        }

        .demo-caption strong {
          color: #12221d;
          font-size: 18px;
          line-height: 1.28;
        }

        .phone-top {
          display: grid;
          gap: 4px;
          padding: 18px;
          border-radius: 24px 24px 12px 12px;
        }

        .phone-top span,
        .progress-panel span,
        .next-card span,
        .family-card span,
        .next-slot span,
        .time,
        .caregiver-head span,
        .inventory-line span {
          color: #667771;
          font-size: 12px;
          font-weight: 700;
        }

        .phone-top strong {
          font-size: 24px;
        }

        .progress-panel,
        .next-card,
        .family-card,
        .next-slot {
          display: grid;
          gap: 8px;
          padding: 16px;
          border-radius: 16px;
        }

        .progress-panel strong,
        .next-card strong,
        .next-slot strong {
          font-size: 22px;
        }

        .family-card strong {
          color: #77510e;
          font-size: 15px;
          line-height: 1.35;
        }

        .next-card {
          background: #dcf4e8;
        }

        .family-card {
          background: #fff3d8;
        }

        .next-card p,
        .family-card p {
          margin: 0;
          color: #38554b;
          font-size: 13px;
          line-height: 1.5;
        }

        .next-slot {
          background: #dcf4e8;
        }

        .next-slot p {
          margin: 0;
          color: #38554b;
          font-size: 14px;
        }

        .progress-track {
          height: 9px;
          border-radius: 999px;
          background: #dce7e2;
          overflow: hidden;
        }

        .progress-track span {
          display: block;
          width: 66%;
          height: 100%;
          background: #3c8a69;
        }

        .slot-list {
          display: grid;
          gap: 8px;
          margin-top: 10px;
          padding: 10px;
          border-radius: 16px;
        }

        .dose-row {
          display: grid;
          grid-template-columns: 48px minmax(0, 1fr) auto;
          gap: 10px;
          align-items: center;
          padding: 12px;
          border-radius: 12px;
          background: #ffffff;
          border: 1px solid #e4ece8;
        }

        .dose-row div {
          display: grid;
          gap: 2px;
        }

        .dose-row strong {
          font-size: 14px;
        }

        .dose-row div:nth-child(2) span {
          color: #667771;
          font-size: 12px;
        }

        .dose-row em {
          padding: 5px 8px;
          border-radius: 999px;
          font-size: 11px;
          font-style: normal;
          font-weight: 800;
        }

        .dose-row em.status-done {
          background: #e7f5ee;
          color: #2f745d;
        }

        .dose-row em.status-pending {
          background: #eaf2ff;
          color: #245a9c;
        }

        .dose-row em.status-missed {
          background: #fff3d8;
          color: #77510e;
        }

        .demo-action {
          width: 100%;
          min-height: 48px;
          margin-top: 10px;
          border: 0;
          border-radius: 999px;
          background: #3c8a69;
          color: #ffffff;
          font-size: 14px;
          font-weight: 800;
        }

        .caregiver-demo {
          position: absolute;
          right: 34px;
          bottom: 54px;
          z-index: 3;
          width: 260px;
          padding: 18px;
          border-radius: 8px;
          background: rgba(255, 255, 255, 0.96);
          border: 1px solid rgba(18, 34, 29, 0.08);
          box-shadow: 0 18px 50px rgba(18, 34, 29, 0.16);
        }

        .caregiver-head {
          display: grid;
          gap: 4px;
        }

        .caregiver-head strong {
          font-size: 20px;
        }

        .alert-line {
          margin-top: 14px;
          padding: 12px;
          border-radius: 8px;
          background: #fff3d8;
          color: #77510e;
          font-size: 13px;
          font-weight: 800;
        }

        .inventory-line {
          display: grid;
          gap: 4px;
          margin-top: 10px;
          padding: 12px;
          border-radius: 8px;
          background: #f8fbfa;
          border: 1px solid #e4ece8;
        }

        .inventory-line strong {
          font-size: 14px;
        }

        .watch-summary,
        .watch-alert,
        .watch-list {
          display: grid;
          gap: 8px;
          padding: 16px;
          border-radius: 16px;
          background: #f8fbfa;
        }

        .watch-summary {
          background: #e7f5ee;
        }

        .watch-alert {
          background: #fff3d8;
        }

        .watch-summary span,
        .watch-alert span,
        .watch-list span {
          color: #667771;
          font-size: 12px;
          font-weight: 700;
        }

        .watch-summary strong {
          font-size: 20px;
        }

        .watch-alert strong {
          color: #77510e;
          font-size: 16px;
          line-height: 1.35;
        }

        .watch-alert p {
          margin: 0;
          color: #77510e;
          font-size: 13px;
          line-height: 1.55;
        }

        .watch-list {
          gap: 8px;
          padding: 10px;
        }

        .watch-list div {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 10px;
          padding: 12px;
          border-radius: 12px;
          background: #ffffff;
          border: 1px solid #e4ece8;
        }

        .watch-list strong {
          font-size: 13px;
          text-align: right;
        }

        .mail-note {
          padding-bottom: 72px;
        }

        .mail-note > div {
          padding: 28px 32px;
          background: #fffdf7;
        }

        .mail-note h2 {
          font-size: 24px;
        }

        @media (max-width: 920px) {
          .hero {
            min-height: auto;
            display: block;
            padding: 22px 16px 36px;
            background:
              linear-gradient(180deg, rgba(14, 41, 35, 0.24) 0%, rgba(14, 41, 35, 0.94) 58%, rgba(14, 41, 35, 0.98) 100%),
              radial-gradient(circle at 50% 10%, rgba(120, 212, 170, 0.45), transparent 42%),
              linear-gradient(135deg, #173d35 0%, #e6f4ed 100%);
          }

          .hero-bg {
            position: relative;
            inset: auto;
            justify-content: center;
            align-items: flex-start;
            padding: 0 0 22px;
          }

          .hero-phone {
            width: 220px;
            transform: none;
          }

          .iphone-frame {
            border-radius: 38px;
          }

          h1 {
            font-size: 36px;
          }

          .hero-copy p:not(.eyebrow) {
            font-size: 15px;
            line-height: 1.75;
          }

          .feature-grid,
          .demo-layout {
            grid-template-columns: 1fr;
          }

          .overview,
          .demo-section,
          .mail-note {
            padding: 52px 16px 0;
          }

          .section-heading h2 {
            font-size: 28px;
          }

          .demo-stage {
            grid-template-columns: repeat(2, minmax(210px, 1fr));
            overflow-x: auto;
            justify-content: start;
          }

          .main-demo {
            transform: none;
          }

          .caregiver-demo {
            right: auto;
            left: 50%;
            bottom: 32px;
            transform: translateX(-50%);
          }

          .mail-note {
            padding-bottom: 52px;
          }
        }

        @media (max-width: 480px) {
          .hero {
            padding-top: 20px;
          }

          h1 {
            font-size: 32px;
          }

          .demo-phone {
            width: min(292px, 100%);
          }

          .hero-phone {
            width: min(204px, 68vw);
          }

          .demo-stage {
            grid-template-columns: 1fr;
            padding: 18px;
          }

          .main-demo {
            transform: none;
          }

          .caregiver-demo {
            width: min(260px, calc(100% - 36px));
          }

          .dose-row {
            grid-template-columns: 44px minmax(0, 1fr);
          }

          .dose-row em {
            grid-column: 2;
            width: fit-content;
          }
        }
      `}</style>
    </main>
  );
}
