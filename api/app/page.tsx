import { SiteFooter } from "./SiteFooter";
import { SiteHeader } from "./SiteHeader";

export const metadata = {
  title: "お薬見守り | 家族で服薬を見守るアプリ",
  description: "お薬見守りは、毎日の服薬予定・服薬記録・お薬の残量を家族で確認できるアプリです。"
};

const appStoreUrl =
  "https://apps.apple.com/jp/app/%E3%81%8A%E8%96%AC%E8%A6%8B%E5%AE%88%E3%82%8A/id6787427428";

const features = [
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
    body: "家族は服薬済み・未記録・飲み忘れを確認できます。お薬の残量も見られるので、補充の時期にも気づきやすくなります。"
  }
];

const benefits = [
  ["今日飲む分が分かる", "次に飲む分を分かりやすく案内"],
  ["まとめて記録できる", "朝・昼・夜など時間帯ごとに整理"],
  ["進み具合が見える", "家族が記録状況を確認"],
  ["頓服にも対応", "必要な時のお薬も記録"]
];

function StoreActions() {
  return (
    <div className="store-actions" aria-label="アプリのダウンロード">
      <a className="primary-button" href={appStoreUrl} rel="noreferrer" target="_blank">
        iPhone版をダウンロード
      </a>
      <span className="store-coming-soon">Android版は今後配信予定です</span>
    </div>
  );
}

export default function Home() {
  return (
    <main className="page-shell home-page">
      <SiteHeader current="home" />

      <section className="site-hero" aria-labelledby="home-title">
        <div className="site-hero-inner">
          <div className="site-hero-copy">
            <p className="section-label">お薬見守り</p>
            <h1 id="home-title">今日のお薬を、家族で見守る。</h1>
            <p>
              毎日の服薬予定・記録・残量を家族で確認できるアプリです。本人は飲むお薬を迷わず確認でき、家族は離れていても服薬状況に気づけます。
            </p>
            <StoreActions />
          </div>

          <div className="screenshot-pair" aria-label="実際のアプリ画面">
            <figure className="app-screenshot">
              <figcaption>本人モード</figcaption>
              <img src="/screenshots/patient-today.png" alt="本人モードの今日のお薬画面" />
            </figure>
            <figure className="app-screenshot family">
              <figcaption>家族モード</figcaption>
              <img src="/screenshots/caregiver-today.png" alt="家族モードの今日の服薬画面" />
            </figure>
          </div>
        </div>
      </section>

      <section className="home-section" id="overview" aria-labelledby="overview-title">
        <div className="content-width">
          <p className="section-label">アプリでできること</p>
          <h2 className="section-title" id="overview-title">
            服薬予定・記録・残量をひとつに
          </h2>
          <div className="home-feature-grid">
            {features.map((feature) => (
              <article className="home-feature" key={feature.title}>
                <span>{feature.label}</span>
                <h3>{feature.title}</h3>
                <p>{feature.body}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="home-guide-entry" aria-labelledby="guide-entry-title">
        <div className="content-width home-guide-entry-inner">
          <div>
            <p className="section-label">詳しい使い方</p>
            <h2 id="guide-entry-title">本人と家族で使う流れを、画面を見ながら確認</h2>
            <p>
              連携コードの発行から、本人の服薬記録、家族によるお薬・在庫・履歴の管理まで順番にご案内します。
            </p>
          </div>
          <a className="primary-button" href="/guide">
            詳しい使い方を見る
          </a>
        </div>
      </section>

      <section className="home-section home-demo" id="demo" aria-labelledby="demo-title">
        <div className="content-width">
          <p className="section-label">実際の画面</p>
          <h2 className="section-title" id="demo-title">
            本人は迷わず記録。家族は必要な状況を確認。
          </h2>
          <div className="home-demo-layout">
            <div>
              <p className="section-lead">
                本人には次に飲むお薬と時間を分かりやすく表示します。家族は今日の進み具合や未記録の予定を確認できるため、声をかける時期を判断しやすくなります。
              </p>
              <div className="home-benefits">
                {benefits.map(([title, description]) => (
                  <div key={title}>
                    <strong>{title}</strong>
                    <span>{description}</span>
                  </div>
                ))}
              </div>
            </div>
            <div
              className="screenshot-pair home-demo-screens"
              aria-label="本人用と家族用の実際の画面"
            >
              <figure className="app-screenshot">
                <figcaption>本人用：今日のお薬を確認して記録</figcaption>
                <img src="/screenshots/patient-today.png" alt="本人用の服薬確認・記録画面" />
              </figure>
              <figure className="app-screenshot family">
                <figcaption>家族用：服薬状況と未記録を確認</figcaption>
                <img src="/screenshots/caregiver-today.png" alt="家族用の服薬状況確認画面" />
              </figure>
            </div>
          </div>
        </div>
      </section>

      <section className="home-mail-note" aria-labelledby="mail-note-title">
        <div className="content-width">
          <p className="section-label">登録メールから開いた方へ</p>
          <h2 id="mail-note-title">メール確認が終わった方へ</h2>
          <p>
            メール確認が完了したら、このページを閉じてアプリに戻ってください。家族モードからログインすると、確認済みのアカウントとして利用できます。
          </p>
        </div>
      </section>

      <section
        className="home-section home-download"
        id="download"
        aria-labelledby="download-title"
      >
        <div className="content-width home-download-inner">
          <div>
            <p className="section-label">アプリを始める</p>
            <h2 className="section-title" id="download-title">
              iPhone版を配信中です
            </h2>
            <p className="section-lead">
              App Storeからダウンロードし、まずは家族モードで見守る方とお薬を登録してください。
            </p>
            <StoreActions />
          </div>
          <a className="home-qr" href={appStoreUrl} rel="noreferrer" target="_blank">
            <img src="/app-store-qr.svg" alt="iPhone版のApp Storeページを開くQRコード" />
            <span>
              <strong>iPhone版QRコード</strong>
              <small>スマートフォンのカメラで読み取れます</small>
            </span>
          </a>
        </div>
      </section>

      <section className="home-section home-public-info" aria-labelledby="public-info-title">
        <div className="content-width">
          <p className="section-label">安心して使うために</p>
          <h2 className="section-title" id="public-info-title">
            大切な情報をご確認ください
          </h2>
          <div className="home-public-grid">
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
        </div>
      </section>

      <SiteFooter />
    </main>
  );
}
