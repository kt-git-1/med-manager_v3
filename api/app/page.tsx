import { SiteHeader } from "./SiteHeader";
import { SiteFooter } from "./SiteFooter";

export const metadata = {
  title: "お薬見守り | 家族で服薬を見守るアプリ",
  description: "お薬見守りは、毎日の服薬予定・服薬記録・お薬の残量を家族で確認できるアプリです。"
};

const featureRows = [
  {
    label: "準備",
    title: "お薬と飲む時間を家族が登録",
    body: "お薬名、飲む量、朝・昼・夜などの時間帯を家族が先に登録します。本人はその日に飲む分だけを確認できます。"
  },
  {
    label: "記録",
    title: "本人は今日のお薬を見て記録",
    body: "本人画面には、次に飲むお薬と時間帯を大きく表示します。飲めたらボタンを押すだけで記録できます。"
  },
  {
    label: "共有",
    title: "離れていても状況が分かる",
    body: "家族は服薬済み・未記録・飲み忘れを確認できます。お薬の残量も見られるので、補充のタイミングにも気づきやすくなります。"
  }
];

const patientSlots = [
  {
    time: "07:30",
    slot: "朝",
    medicines: ["血圧のお薬 1錠", "胃のお薬 1錠"],
    status: "記録済み"
  },
  {
    time: "12:30",
    slot: "昼",
    medicines: ["胃のお薬 1錠"],
    status: "未記録"
  },
  {
    time: "20:30",
    slot: "夜",
    medicines: ["眠る前のお薬 0.5錠"],
    status: "これから"
  }
];

const caregiverTimeline = [
  { time: "07:30", slot: "朝", summary: "2件の薬", status: "飲みました" },
  { time: "12:30", slot: "昼", summary: "1件の薬 / 未記録1件", status: "次に記録" },
  { time: "20:30", slot: "夜", summary: "1件の薬", status: "まだです" }
];

const appStoreUrl = "";

function AppStoreBadge() {
  const content = (
    <>
      <span className="app-store-icon" aria-hidden="true" />
      <span className="app-store-text">
        <span>iPhoneアプリ</span>
        <strong>{appStoreUrl ? "App Storeでダウンロード" : "App Storeで公開予定"}</strong>
      </span>
      {!appStoreUrl && <em>準備中</em>}
    </>
  );

  if (appStoreUrl) {
    return (
      <a className="app-store-badge" href={appStoreUrl} rel="noreferrer" target="_blank">
        {content}
      </a>
    );
  }

  return (
    <span className="app-store-badge app-store-badge-disabled" aria-disabled="true" role="link">
      {content}
    </span>
  );
}

export default function Home() {
  return (
    <main className="page">
      <SiteHeader current="home" />

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
                <div className="app-nav">
                  <span>本人モード</span>
                  <strong>今日のお薬</strong>
                </div>
                <div className="app-hero-card">
                  <span>次に飲むお薬</span>
                  <div>
                    <strong>昼のお薬</strong>
                    <em>12:30</em>
                  </div>
                  <p>1錠 / 1種類</p>
                  <div className="medicine-line">
                    <b>胃のお薬</b>
                    <small>1錠</small>
                  </div>
                  <button className="demo-action" type="button">
                    この時間のお薬を飲んだ
                  </button>
                </div>
                <div className="app-section-title">
                  <strong>今日のお薬</strong>
                  <span>2/4 記録済み</span>
                </div>
                <div className="compact-slot-list">
                  {patientSlots.slice(0, 2).map((row) => (
                    <div className="compact-slot" key={row.slot}>
                      <div>
                        <strong>{row.slot}</strong>
                        <span>{row.time}</span>
                      </div>
                      <p>{row.medicines[0]}</p>
                      <em className={row.status === "記録済み" ? "status-done" : "status-pending"}>
                        {row.status}
                      </em>
                    </div>
                  ))}
                </div>
                <div className="prn-tile">
                  <div>
                    <strong>必要な時のお薬</strong>
                    <span>頓服薬が2件あります</span>
                  </div>
                  <b>›</b>
                </div>
                <div className="home-indicator" />
              </div>
            </div>
          </div>
        </div>

        <div className="hero-copy">
          <p className="eyebrow">お薬見守り</p>
          <h1 id="home-title">今日のお薬を、家族で見守る。</h1>
          <p>
            お薬見守りは、毎日の服薬予定・記録・残量を家族で確認できるアプリです。
            本人は飲むお薬を迷わず確認でき、家族は離れていても服薬状況に気づけます。
          </p>
          <div className="hero-download" aria-label="アプリのダウンロード">
            <AppStoreBadge />
            <span className="hero-download-note">
              iPhone版を準備中です。公開後、このページからApp Storeへ進めます。
            </span>
          </div>
        </div>
      </section>

      <section className="overview" id="overview" aria-label="アプリの概要">
        <div className="section-heading">
          <p className="eyebrow">Overview</p>
          <h2>服薬予定・記録・残量をひとつに</h2>
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

      <section className="demo-section" id="demo" aria-labelledby="demo-title">
        <div className="section-heading">
          <p className="eyebrow">Demo</p>
          <h2 id="demo-title">画面例</h2>
        </div>

        <div className="demo-layout">
          <div className="demo-copy">
            <h3>本人は迷わず記録。家族は必要な状況を確認。</h3>
            <p>
              本人には、次に飲むお薬と時間を分かりやすく表示します。
              家族は、今日の進み具合や未記録の予定を確認できるため、声をかけるタイミングを判断しやすくなります。
            </p>
            <div className="mini-metrics" aria-label="デモ指標">
              <div>
                <strong>今日飲む分が分かる</strong>
                <span>次に飲む分を分かりやすく案内</span>
              </div>
              <div>
                <strong>まとめて記録できる</strong>
                <span>朝・昼・夜など時間帯ごとに整理</span>
              </div>
              <div>
                <strong>進み具合が見える</strong>
                <span>家族が記録状況を確認</span>
              </div>
              <div>
                <strong>頓服にも対応</strong>
                <span>必要な時のお薬も記録</span>
              </div>
            </div>
          </div>

          <div className="demo-stage" aria-label="アプリ画面デモ">
            <figure className="demo-device">
              <figcaption>
                <strong>本人用</strong>
                <span>今日飲むお薬を確認して記録</span>
              </figcaption>
              <div className="demo-phone app-demo-phone main-demo">
                <div className="demo-statusbar">
                  <span>9:41</span>
                  <span>●●●</span>
                </div>
                <div className="app-nav">
                  <span>本人モード</span>
                  <strong>今日のお薬</strong>
                </div>
                <div className="app-hero-card">
                  <span>次に飲むお薬</span>
                  <div>
                    <strong>昼のお薬</strong>
                    <em>12:30</em>
                  </div>
                  <p>1錠 / 1種類</p>
                  <div className="medicine-line">
                    <b>胃のお薬</b>
                    <small>1錠</small>
                  </div>
                  <button className="demo-action" type="button">
                    この時間のお薬を飲んだ
                  </button>
                </div>
                <div className="app-section-title">
                  <strong>今日のお薬</strong>
                  <span>時間帯ごと</span>
                </div>
                <div className="slot-card-list">
                  {patientSlots.map((row) => (
                    <div className="slot-card" key={row.slot}>
                      <div className="slot-card-head">
                        <i />
                        <div>
                          <strong>{row.slot}</strong>
                          <span>{row.time}</span>
                        </div>
                        <em
                          className={
                            row.status === "記録済み"
                              ? "status-done"
                              : row.status === "未記録"
                                ? "status-pending"
                                : "status-waiting"
                          }
                        >
                          {row.status}
                        </em>
                      </div>
                      {row.medicines.map((medicine) => (
                        <div className="medicine-line" key={medicine}>
                          <b>{medicine.replace(/ [0-9.]+錠$/, "")}</b>
                          <small>{medicine.match(/[0-9.]+錠$/)?.[0]}</small>
                        </div>
                      ))}
                    </div>
                  ))}
                </div>
                <div className="prn-tile">
                  <div>
                    <strong>必要な時のお薬</strong>
                    <span>痛い時・つらい時だけ記録</span>
                  </div>
                  <b>›</b>
                </div>
              </div>
            </figure>

            <figure className="demo-device">
              <figcaption>
                <strong>家族用</strong>
                <span>服薬状況と未記録を確認</span>
              </figcaption>
              <div className="demo-phone app-demo-phone watcher-demo">
                <div className="demo-statusbar">
                  <span>9:41</span>
                  <span>●●●</span>
                </div>
                <div className="caregiver-title">
                  <div className="avatar">太</div>
                  <div>
                    <span>山田 太郎さん</span>
                    <strong>今日の服薬</strong>
                  </div>
                </div>
                <div className="caregiver-action-card">
                  <span>次にすること</span>
                  <div>
                    <b>昼 12:30</b>
                    <em>未記録</em>
                  </div>
                  <p>この時間帯の未記録1件をまとめて記録します</p>
                  <button className="demo-action" type="button">
                    この時間帯をまとめて記録
                  </button>
                </div>
                <div className="caregiver-progress">
                  <div className="progress-ring">2/4</div>
                  <div>
                    <span>今日の進み具合</span>
                    <strong>2/4回分 完了</strong>
                    <p>昼が次に記録する服薬です</p>
                  </div>
                </div>
                <div className="app-section-title">
                  <strong>今日の予定</strong>
                  <span>見守り</span>
                </div>
                <div className="timeline-list">
                  {caregiverTimeline.map((row) => (
                    <div className="timeline-row" key={row.slot}>
                      <div>
                        <strong>{row.slot}</strong>
                        <span>{row.time}</span>
                      </div>
                      <div>
                        <strong>{row.summary}</strong>
                        <span>{row.status}</span>
                      </div>
                      <em
                        className={
                          row.status === "飲みました"
                            ? "status-done"
                            : row.status === "次に記録"
                              ? "status-pending"
                              : "status-waiting"
                        }
                      >
                        {row.status === "飲みました"
                          ? "済"
                          : row.status === "次に記録"
                            ? "次"
                            : "未"}
                      </em>
                    </div>
                  ))}
                </div>
              </div>
            </figure>
          </div>
        </div>
      </section>

      <section className="mail-note" aria-label="登録メールから開いた方へ">
        <div>
          <p className="eyebrow">For signup</p>
          <h2>メール確認が終わった方へ</h2>
          <p>
            メール確認が完了したら、このページを閉じてアプリに戻ってください。
            家族モードからログインすると、確認済みのアカウントとして利用できます。
          </p>
        </div>
      </section>

      <section className="download-note" id="download" aria-labelledby="download-title">
        <div>
          <div>
            <p className="eyebrow">Download</p>
            <h2 id="download-title">公開後はApp Storeからダウンロードできます</h2>
            <p>
              現在、iPhone版の公開準備中です。公開後は、このページのボタンをApp
              Storeへのリンクに切り替えます。
            </p>
          </div>
          <AppStoreBadge />
        </div>
      </section>

      <section className="public-info" aria-labelledby="public-info-title">
        <div className="section-heading">
          <p className="eyebrow">Legal / Support</p>
          <h2 id="public-info-title">安心して使うための情報</h2>
        </div>
        <div className="public-grid">
          <a href="/privacy">
            <span>データの扱い</span>
            <strong>プライバシーポリシー</strong>
            <p>取得する情報、利用目的、外部サービス、削除依頼について確認できます。</p>
          </a>
          <a href="/terms">
            <span>利用時の約束</span>
            <strong>利用規約</strong>
            <p>サービス内容、医療上の注意、利用者の責任、免責事項をまとめています。</p>
          </a>
          <a href="/support">
            <span>困ったとき</span>
            <strong>サポート</strong>
            <p>問い合わせ、アカウント削除、不具合、通知が届かない場合の案内です。</p>
          </a>
        </div>
      </section>

      <SiteFooter />

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
          .download-note,
          .public-info {
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

        .hero-download {
          display: flex;
          align-items: center;
          flex-wrap: wrap;
          gap: 14px;
          max-width: 680px;
          margin-top: 26px;
        }

        .hero-download-note {
          max-width: 360px;
          color: #d8f6e9;
          font-size: 14px;
          font-weight: 700;
          line-height: 1.7;
        }

        .app-store-badge {
          display: inline-flex;
          align-items: center;
          gap: 12px;
          min-width: 270px;
          padding: 12px 14px;
          border-radius: 8px;
          background: #ffffff;
          color: #12221d;
          text-decoration: none;
          box-shadow: 0 18px 40px rgba(4, 18, 15, 0.18);
          border: 1px solid rgba(18, 34, 29, 0.08);
        }

        .app-store-icon {
          flex: 0 0 auto;
          width: 42px;
          height: 42px;
          border-radius: 10px;
          background:
            url("/brand-mark.png") center / contain no-repeat,
            #f5f8f6;
          box-shadow: inset 0 0 0 1px rgba(18, 34, 29, 0.08);
        }

        .app-store-text {
          display: grid;
          gap: 2px;
          min-width: 0;
        }

        .app-store-text span {
          color: #52635d;
          font-size: 11px;
          font-weight: 800;
          line-height: 1.1;
        }

        .app-store-text strong {
          color: #12221d;
          font-size: 17px;
          line-height: 1.25;
        }

        .app-store-badge em {
          margin-left: auto;
          padding: 5px 8px;
          border-radius: 999px;
          background: #e7f5ee;
          color: #2f745d;
          font-size: 11px;
          font-style: normal;
          font-weight: 900;
          white-space: nowrap;
        }

        .app-store-badge-disabled {
          cursor: default;
        }

        .overview,
        .demo-section,
        .mail-note,
        .download-note,
        .public-info {
          padding: 72px 20px 0;
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

        .public-grid {
          display: grid;
          grid-template-columns: repeat(3, minmax(0, 1fr));
          gap: 16px;
        }

        .feature,
        .mail-note > div,
        .download-note > div,
        .public-grid a {
          border: 1px solid rgba(18, 34, 29, 0.08);
          border-radius: 8px;
          background: #ffffff;
          box-shadow: 0 18px 50px rgba(18, 34, 29, 0.08);
        }

        .feature,
        .public-grid a {
          padding: 26px;
        }

        .feature span,
        .public-grid span {
          display: inline-flex;
          margin-bottom: 18px;
          padding: 6px 10px;
          border-radius: 999px;
          background: #e7f5ee;
          color: #2f745d;
          font-size: 13px;
          font-weight: 800;
        }

        .public-grid a {
          color: inherit;
          text-decoration: none;
        }

        .feature h3,
        .public-grid strong {
          display: block;
          margin: 0;
          font-size: 20px;
          line-height: 1.45;
          letter-spacing: 0;
          word-break: normal;
          overflow-wrap: anywhere;
        }

        .feature p,
        .public-grid p,
        .demo-copy p,
        .mail-note p,
        .download-note p {
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
          gap: 22px;
          align-items: start;
          justify-content: center;
          padding: 28px;
          border-radius: 8px;
          background:
            linear-gradient(135deg, #e7f5ee 0%, #fffdf7 100%);
          border: 1px solid rgba(18, 34, 29, 0.08);
          overflow: hidden;
        }

        .demo-device {
          display: grid;
          gap: 12px;
          justify-items: center;
          margin: 0;
        }

        .demo-device figcaption {
          display: grid;
          gap: 3px;
          width: min(268px, 100%);
          color: #12221d;
        }

        .demo-device figcaption strong {
          font-size: 18px;
          line-height: 1.25;
        }

        .demo-device figcaption span {
          color: #52635d;
          font-size: 13px;
          font-weight: 700;
          line-height: 1.45;
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

        .app-demo-phone {
          aspect-ratio: 393 / 852;
          display: grid;
          align-content: start;
          gap: 10px;
          overflow: hidden;
          padding: 14px;
          border: 10px solid #10231e;
          background: #f5f8f6;
        }

        .demo-statusbar {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 0 10px 2px;
          color: #10231e;
          font-size: 12px;
          font-weight: 900;
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

        .app-nav,
        .app-hero-card,
        .slot-card,
        .prn-tile,
        .caregiver-action-card,
        .caregiver-progress,
        .timeline-row {
          background: #ffffff;
          box-shadow: 0 10px 24px rgba(18, 34, 29, 0.08);
        }

        .app-nav {
          display: grid;
          gap: 3px;
          padding: 14px 16px;
          border-radius: 22px 22px 14px 14px;
        }

        .app-nav span,
        .app-hero-card > span,
        .app-section-title span,
        .slot-card span,
        .prn-tile span,
        .caregiver-title span,
        .caregiver-action-card > span,
        .caregiver-progress span,
        .timeline-row span {
          color: #667771;
          font-size: 12px;
          font-weight: 700;
        }

        .app-nav strong {
          font-size: 22px;
          line-height: 1.2;
        }

        .app-hero-card {
          display: grid;
          gap: 10px;
          padding: 16px;
          border-radius: 18px;
          border-left: 6px solid #2f745d;
        }

        .app-hero-card div:first-of-type {
          display: flex;
          align-items: baseline;
          justify-content: space-between;
          gap: 10px;
        }

        .app-hero-card div:first-of-type strong {
          min-width: 0;
        }

        .app-hero-card strong {
          color: #174f40;
          font-size: 26px;
          line-height: 1.1;
        }

        .app-hero-card em {
          flex: 0 0 auto;
          color: #667771;
          font-size: 18px;
          font-style: normal;
          font-weight: 800;
          white-space: nowrap;
        }

        .app-hero-card p {
          margin: 0;
          color: #667771;
          font-size: 13px;
          font-weight: 700;
        }

        .medicine-line {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 10px;
          padding: 10px 12px;
          border-radius: 12px;
          background: #f5f8f6;
          border: 1px solid #e4ece8;
        }

        .medicine-line b {
          font-size: 13px;
        }

        .medicine-line small {
          color: #667771;
          font-size: 12px;
          font-weight: 800;
        }

        .app-section-title {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 10px;
          padding: 2px 4px;
        }

        .app-section-title strong {
          font-size: 15px;
        }

        .compact-slot-list,
        .slot-card-list,
        .timeline-list {
          display: grid;
          gap: 9px;
        }

        .compact-slot {
          display: grid;
          grid-template-columns: 42px minmax(0, 1fr) auto;
          gap: 9px;
          align-items: center;
          padding: 10px;
          border-radius: 14px;
          background: #ffffff;
          border: 1px solid #e4ece8;
        }

        .compact-slot div {
          display: grid;
          gap: 2px;
        }

        .compact-slot p {
          margin: 0;
          font-size: 12px;
          font-weight: 800;
        }

        .compact-slot em,
        .slot-card em,
        .timeline-row em,
        .caregiver-action-card em {
          padding: 5px 8px;
          border-radius: 999px;
          font-size: 11px;
          font-style: normal;
          font-weight: 800;
          white-space: nowrap;
        }

        .status-done {
          background: #e7f5ee;
          color: #2f745d;
        }

        .status-pending {
          background: #eaf2ff;
          color: #245a9c;
        }

        .status-waiting {
          background: #fff3d8;
          color: #77510e;
        }

        .prn-tile {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 12px;
          padding: 13px 14px;
          border-radius: 16px;
          border-left: 5px solid #d88a22;
        }

        .prn-tile div {
          display: grid;
          gap: 3px;
        }

        .prn-tile strong {
          font-size: 15px;
        }

        .prn-tile b {
          color: #667771;
          font-size: 24px;
          line-height: 1;
        }

        .slot-card {
          display: grid;
          gap: 10px;
          padding: 12px;
          border-radius: 16px;
          border-left: 5px solid #2f745d;
        }

        .slot-card-head {
          display: grid;
          grid-template-columns: 10px minmax(0, 1fr) auto;
          gap: 9px;
          align-items: center;
        }

        .slot-card-head i {
          width: 10px;
          height: 10px;
          border-radius: 50%;
          background: #2f745d;
        }

        .slot-card-head div {
          display: grid;
          gap: 2px;
        }

        .slot-card-head strong {
          font-size: 17px;
        }

        .caregiver-title {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 7px 4px 2px;
        }

        .avatar {
          display: grid;
          place-items: center;
          width: 46px;
          height: 46px;
          border-radius: 50%;
          background: #dff2ea;
          color: #174f40;
          font-weight: 900;
        }

        .caregiver-title div:last-child {
          display: grid;
          gap: 2px;
        }

        .caregiver-title strong {
          font-size: 22px;
          line-height: 1.2;
        }

        .caregiver-action-card {
          display: grid;
          gap: 10px;
          padding: 16px;
          border-radius: 18px;
          border-left: 6px solid #2f745d;
        }

        .caregiver-action-card div {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 10px;
        }

        .caregiver-action-card b {
          color: #174f40;
          font-size: 22px;
        }

        .caregiver-action-card p {
          margin: 0;
          color: #667771;
          font-size: 12px;
          font-weight: 700;
          line-height: 1.45;
        }

        .caregiver-progress {
          display: grid;
          grid-template-columns: 64px minmax(0, 1fr);
          gap: 12px;
          align-items: center;
          padding: 14px;
          border-radius: 16px;
        }

        .progress-ring {
          display: grid;
          place-items: center;
          width: 62px;
          height: 62px;
          border-radius: 50%;
          background:
            radial-gradient(circle at center, #ffffff 55%, transparent 57%),
            conic-gradient(#3c8a69 0 50%, #dce7e2 50% 100%);
          color: #174f40;
          font-size: 16px;
          font-weight: 900;
        }

        .caregiver-progress div:last-child {
          display: grid;
          gap: 4px;
        }

        .caregiver-progress strong {
          font-size: 17px;
        }

        .caregiver-progress p {
          margin: 0;
          color: #667771;
          font-size: 12px;
          font-weight: 700;
          line-height: 1.45;
        }

        .timeline-row {
          display: grid;
          grid-template-columns: 42px minmax(0, 1fr) auto;
          gap: 9px;
          align-items: center;
          padding: 10px;
          border-radius: 14px;
        }

        .timeline-row div {
          display: grid;
          gap: 2px;
        }

        .timeline-row strong {
          font-size: 13px;
        }

        .hero-phone {
          transform: none;
        }

        .hero-phone .app-hero-card strong {
          font-size: 23px;
        }

        .hero-phone .app-hero-card em {
          font-size: 16px;
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
          padding-bottom: 0;
        }

        .mail-note > div {
          padding: 28px 32px;
          background: #fffdf7;
        }

        .mail-note h2 {
          font-size: 24px;
        }

        .download-note > div {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 24px;
          padding: 30px 32px;
          background:
            linear-gradient(135deg, #ffffff 0%, #edf8f2 100%);
          border-left: 6px solid #3c8a69;
        }

        .download-note h2 {
          margin: 0;
          font-size: 24px;
          line-height: 1.3;
        }

        .download-note .app-store-badge {
          flex: 0 0 auto;
          box-shadow: 0 12px 28px rgba(18, 34, 29, 0.12);
        }

        @media (max-width: 920px) {
          .hero {
            min-height: auto;
            display: block;
            padding: 34px 16px 36px;
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
          .public-grid,
          .demo-layout {
            grid-template-columns: 1fr;
          }

          .overview,
          .demo-section,
          .mail-note,
          .download-note,
          .public-info {
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

          .download-note > div {
            display: grid;
            justify-items: start;
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

          .app-store-badge {
            width: 100%;
            min-width: 0;
          }

          .public-grid {
            grid-template-columns: 1fr;
          }

          .public-grid strong {
            font-size: 18px;
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
