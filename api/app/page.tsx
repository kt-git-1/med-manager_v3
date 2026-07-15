import { SiteFooter } from "./SiteFooter";
import { SiteHeader } from "./SiteHeader";

export const metadata = {
  title: "お薬見守り | 家族で服薬を見守るアプリ",
  description: "毎日の服薬予定・服薬記録・お薬の残量を、本人と家族で確認できるアプリです。"
};

const appStoreUrl =
  "https://apps.apple.com/jp/app/%E3%81%8A%E8%96%AC%E8%A6%8B%E5%AE%88%E3%82%8A/id6787427428";

export default function Home() {
  return (
    <main className="page-shell">
      <SiteHeader current="home" />

      <section className="site-hero" aria-labelledby="home-title">
        <div className="site-hero-inner">
          <div className="site-hero-copy">
            <p className="section-label">本人と家族の服薬をつなぐアプリ</p>
            <h1 id="home-title">毎日のお薬を、家族で見守る。</h1>
            <p>
              家族がお薬と飲む時間を登録し、本人は今日飲む分を確認して記録します。
              離れていても、服薬状況とお薬の残りを家族で確認できます。
            </p>
            <div className="hero-actions">
              <a className="primary-button" href="/guide">
                使い方を順番に見る
              </a>
              <a className="secondary-button" href={appStoreUrl} target="_blank" rel="noreferrer">
                iPhone版をダウンロード
              </a>
            </div>
            <p className="needed-note">Android版は今後の配信を予定しています。</p>
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

      <section className="steps-section" id="overview" aria-label="使い始めるまでの流れ">
        <div className="steps-list">
          <article className="step-row family-step">
            <span className="step-number">1</span>
            <span className="step-role">家族の操作</span>
            <div className="step-copy">
              <h2>家族モードでお薬を登録します</h2>
              <p>お薬の名前、量、朝・昼・夜などの飲む時間を登録します。</p>
            </div>
            <div className="step-detail">
              <strong>準備するもの</strong>
              <p>本人のお名前、お薬の情報、本人が使う端末</p>
            </div>
          </article>
          <article className="step-row">
            <span className="step-number">2</span>
            <span className="step-role">本人と連携</span>
            <div className="step-copy">
              <h2>6桁のコードで本人の端末とつなぎます</h2>
              <p>家族モードでコードを発行し、本人モードの端末で入力します。</p>
            </div>
            <a className="secondary-button" href="/guide#start">
              連携方法を見る
            </a>
          </article>
          <article className="step-row">
            <span className="step-number">3</span>
            <span className="step-role">本人の操作</span>
            <div className="step-copy">
              <h2>本人モードでお薬を確認・記録します</h2>
              <p>飲むお薬を確認し、飲んだら大きなボタンで記録します。</p>
            </div>
            <a className="secondary-button" href="/guide#patient">
              本人モードを見る
            </a>
          </article>
        </div>
      </section>

      <section className="info-section">
        <div className="content-width two-column">
          <div>
            <p className="section-label">本人モード</p>
            <h2 className="section-title">今日飲む分だけを、分かりやすく表示</h2>
            <ul className="plain-list">
              <li>
                <strong>次に飲むお薬が分かる</strong>
                <span>時間とお薬の名前、量を大きく表示します。</span>
              </li>
              <li>
                <strong>同じ時間のお薬をまとめて記録</strong>
                <span>朝・昼・夜など、時間帯ごとに記録できます。</span>
              </li>
              <li>
                <strong>頓服と履歴も確認</strong>
                <span>必要な時のお薬と、これまでの記録を確認できます。</span>
              </li>
            </ul>
          </div>
          <div>
            <p className="section-label" style={{ color: "var(--orange)" }}>
              家族モード
            </p>
            <h2 className="section-title">予定を整え、離れていても状況を確認</h2>
            <ul className="plain-list">
              <li>
                <strong>お薬と飲む時間を登録</strong>
                <span>定時薬・頓服・生活に合わせた時間を管理します。</span>
              </li>
              <li>
                <strong>今日の服薬と履歴を確認</strong>
                <span>未記録や飲み忘れに気づきやすくなります。</span>
              </li>
              <li>
                <strong>在庫と補充の目安を確認</strong>
                <span>お薬の残数を見て、補充の時期を判断できます。</span>
              </li>
            </ul>
          </div>
        </div>
      </section>

      <section className="info-section soft">
        <div className="content-width">
          <div className="cta-band">
            <div>
              <h2>画面を見ながら、順番に始めましょう</h2>
              <p>連携コードの発行から、本人・家族それぞれの使い方まで詳しくご案内します。</p>
            </div>
            <a className="primary-button" href="/guide">
              詳しい使い方を見る
            </a>
          </div>
          <div className="notice-box" style={{ marginTop: 28 }}>
            <strong>通知は服薬に気づくための補助機能です</strong>
            <p>
              重要な服薬判断は、医師・薬剤師の指示を優先し、アプリの通知だけに頼らないでください。
            </p>
          </div>
        </div>
      </section>

      <section className="info-section" id="download">
        <div className="content-width two-column">
          <div>
            <p className="section-label">アプリを始める</p>
            <h2 className="section-title">iPhone版を配信中です</h2>
            <p className="section-lead">
              App Storeからダウンロードし、まずは家族モードで見守る方とお薬を登録してください。
            </p>
            <div className="hero-actions">
              <a className="primary-button" href={appStoreUrl} target="_blank" rel="noreferrer">
                App Storeを開く
              </a>
            </div>
          </div>
          <div className="notice-box">
            <strong>メール確認が終わった方へ</strong>
            <p>
              このページを閉じてアプリへ戻り、家族モードから登録したメールアドレスでログインしてください。
            </p>
          </div>
        </div>
      </section>

      <SiteFooter />
    </main>
  );
}
