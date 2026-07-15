import { SiteFooter } from "../SiteFooter";
import { SiteHeader } from "../SiteHeader";

export const metadata = {
  title: "詳しい使い方 | お薬見守り",
  description:
    "本人モード・家族モードの使い方と、6桁のコードで連携する手順を実際のアプリ画面でご案内します。"
};

const sharingRows = [
  ["家族がお薬や時間を登録", "本人の『今日のお薬』に表示"],
  ["本人が飲んだことを記録", "家族の今日・履歴に反映"],
  ["家族が代理で記録", "本人・家族の共通履歴に反映"],
  ["服薬を記録", "家族が管理する在庫数に反映"]
];

export default function GuidePage() {
  return (
    <main className="page-shell guide-page">
      <SiteHeader current="guide" />

      <section className="site-hero" aria-labelledby="guide-title">
        <div className="site-hero-inner">
          <div className="site-hero-copy">
            <h1 id="guide-title">
              はじめてでも、
              <br />
              順番に進めれば大丈夫です
            </h1>
            <p>
              家族の方がお薬を登録し、本人の端末とつなぎます。実際の画面を見ながら、一つずつ進めましょう。
            </p>
            <div className="hero-actions">
              <a className="primary-button" href="#start">
                最初の手順を見る
              </a>
            </div>
            <p className="needed-note">
              登録に必要なもの：本人のお名前・お薬の情報・本人が使う端末
            </p>
          </div>

          <div className="screenshot-pair" aria-label="実際のアプリ画面">
            <figure className="app-screenshot">
              <figcaption>本人モードの画面</figcaption>
              <img
                src="/screenshots/patient-today.png"
                alt="本人モードで次に飲むお薬を確認する実際の画面"
              />
            </figure>
            <figure className="app-screenshot family">
              <figcaption>家族モードの画面</figcaption>
              <img
                src="/screenshots/caregiver-today.png"
                alt="家族モードで今日の服薬を確認する実際の画面"
              />
            </figure>
          </div>
        </div>
      </section>

      <section className="steps-section" id="start" aria-label="使い始める手順">
        <div className="steps-list">
          <article className="step-row family-step">
            <span className="step-number">1</span>
            <span className="step-role">家族の操作</span>
            <div className="step-copy">
              <h2>家族モードでお薬を登録します</h2>
              <p>家族の端末でアカウントを作成し、見守る方のお名前、お薬、飲む時間を登録します。</p>
            </div>
            <div className="step-detail">
              <strong>登録する内容</strong>
              <p>定時薬・頓服、1回に飲む量、朝・昼・夜・眠前などの時間</p>
            </div>
            <figure className="step-shot family-shot">
              <img
                src="/screenshots/caregiver-medication-form.png"
                alt="家族モードで新しいお薬の名前、用量、飲む回数を登録する実際の画面"
              />
              <figcaption>家族モードのお薬登録画面</figcaption>
            </figure>
          </article>
          <article className="step-row">
            <span className="step-number">2</span>
            <span className="step-role">本人と連携</span>
            <div className="step-copy">
              <h2>6桁のコードで本人の端末とつなぎます</h2>
              <p>
                家族モードの「連携・設定」でコードを発行し、本人モードを選んだ端末で入力します。
              </p>
            </div>
            <div className="step-detail">
              <strong>コードは本人にだけ共有</strong>
              <p>有効期限内に入力します。コードや確認メールを第三者へ送らないでください。</p>
            </div>
            <figure className="step-shot">
              <img
                src="/screenshots/patient-link-code.png"
                alt="本人モードで家族から受け取った6桁の連携コードを入力する実際の画面"
              />
              <figcaption>本人モードの連携コード入力画面</figcaption>
            </figure>
          </article>
          <article className="step-row">
            <span className="step-number">3</span>
            <span className="step-role">本人の操作</span>
            <div className="step-copy">
              <h2>本人モードでお薬を確認・記録します</h2>
              <p>
                連携後は、家族が登録した今日のお薬が表示されます。本人側で同じ内容を登録する必要はありません。
              </p>
            </div>
            <div className="step-detail">
              <strong>連携した内容は共有されます</strong>
              <p>本人の記録は家族の画面へ、家族の予定変更は本人の画面へ反映されます。</p>
            </div>
            <figure className="step-shot">
              <img
                src="/screenshots/patient-today.png"
                alt="本人モードで今日飲むお薬を確認して記録する実際の画面"
              />
              <figcaption>本人モードの服薬確認・記録画面</figcaption>
            </figure>
          </article>
        </div>
      </section>

      <section className="info-section" id="patient">
        <div className="content-width two-column guide-mode-layout">
          <div>
            <p className="section-label">本人モード</p>
            <h2 className="section-title">今日飲むお薬を確認して、飲んだら記録</h2>
            <p className="section-lead">
              本人モードは、毎日の操作をできるだけ少なくしています。次に飲む分と、今日の記録状況を大きく表示します。
            </p>
            <ol className="numbered-guide">
              <li>
                <span>1</span>
                <div>
                  <strong>次に飲むお薬を確認</strong>
                  <p>時間、お薬の名前、1回に飲む量を確認します。</p>
                </div>
              </li>
              <li>
                <span>2</span>
                <div>
                  <strong>飲んだらまとめて記録</strong>
                  <p>同じ時間帯のお薬は、大きなボタンでまとめて記録できます。</p>
                </div>
              </li>
              <li>
                <span>3</span>
                <div>
                  <strong>必要な時のお薬も記録</strong>
                  <p>頓服は定時薬と分けて、使った時刻を記録できます。</p>
                </div>
              </li>
              <li>
                <span>4</span>
                <div>
                  <strong>履歴と通知を確認</strong>
                  <p>過去の記録を日付ごとに確認し、お薬の時間に通知を受け取れます。</p>
                </div>
              </li>
            </ol>
          </div>
          <figure className="guide-large-shot">
            <img
              src="/screenshots/patient-today.png"
              alt="本人モードの今日のお薬画面。昼のお薬と記録ボタンが表示されています"
            />
            <figcaption>実際の本人モード画面</figcaption>
          </figure>
        </div>
      </section>

      <section className="info-section soft" id="caregiver">
        <div className="content-width two-column guide-mode-layout reverse">
          <figure className="guide-large-shot family-shot">
            <img
              src="/screenshots/caregiver-today.png"
              alt="家族モードの今日の服薬画面。次に記録する時間と進み具合が表示されています"
            />
            <figcaption>実際の家族モード画面</figcaption>
          </figure>
          <div>
            <p className="section-label" style={{ color: "var(--orange)" }}>
              家族モード
            </p>
            <h2 className="section-title">予定を整え、離れていても状況を確認</h2>
            <p className="section-lead">
              家族モードでは、お薬の準備から今日の服薬状況、履歴、在庫、本人との連携までを管理します。
            </p>
            <ol className="numbered-guide family-guide">
              <li>
                <span>1</span>
                <div>
                  <strong>今日の進み具合を確認</strong>
                  <p>服薬済み・未記録・これからの予定を時間帯ごとに見られます。</p>
                </div>
              </li>
              <li>
                <span>2</span>
                <div>
                  <strong>お薬と飲む時間を管理</strong>
                  <p>定時薬・頓服と、朝・昼・夜・眠前の時刻を登録します。</p>
                </div>
              </li>
              <li>
                <span>3</span>
                <div>
                  <strong>代理記録と履歴の確認</strong>
                  <p>本人のそばにいる時は、家族側からも記録できます。</p>
                </div>
              </li>
              <li>
                <span>4</span>
                <div>
                  <strong>在庫と通知で気づく</strong>
                  <p>残数と補充の目安を確認し、未記録などの変化に気づきやすくします。</p>
                </div>
              </li>
            </ol>
          </div>
        </div>
      </section>

      <section className="info-section" id="sharing">
        <div className="content-width">
          <p className="section-label">連携のしくみ</p>
          <h2 className="section-title">本人と家族は、同じ予定と記録を見ます</h2>
          <p className="section-lead">どちらから記録しても、共通の履歴として確認できます。</p>
          <div className="sharing-list" role="table" aria-label="本人と家族で共有される内容">
            {sharingRows.map(([action, result]) => (
              <div role="row" key={action}>
                <strong role="cell">{action}</strong>
                <span role="cell">{result}</span>
              </div>
            ))}
          </div>
          <div className="two-column safety-notes">
            <div className="notice-box">
              <strong>通知は気づくための補助です</strong>
              <p>
                端末の設定や通信状況により、通知が届かないことがあります。アプリの画面も確認してください。
              </p>
            </div>
            <div className="notice-box">
              <strong>医師・薬剤師の指示を優先してください</strong>
              <p>
                このアプリは診断や治療を行いません。服用方法や体調に不安がある場合は専門家へ相談してください。
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="info-section soft">
        <div className="content-width">
          <div className="cta-band">
            <div>
              <h2>まずは家族モードで登録しましょう</h2>
              <p>アプリ内の初回案内でも、本人の登録と連携コード発行まで順番にご案内します。</p>
            </div>
            <a className="primary-button" href="/#download">
              アプリをダウンロード
            </a>
          </div>
          <p className="screen-note">
            掲載している画面はiPhone版アプリの実際の画面です。アプリのバージョンや端末により、表示が一部異なる場合があります。
          </p>
        </div>
      </section>

      <SiteFooter />
    </main>
  );
}
