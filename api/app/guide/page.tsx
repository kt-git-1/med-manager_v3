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

const patientScreenGuides = [
  {
    title: "「今日のお薬」画面",
    summary: "次に飲む時間・お薬・1回量を確認し、飲んだことを記録する毎日の画面です。",
    image: "/screenshots/patient-today.png",
    alt: "本人モードの今日のお薬画面全体",
    steps: [
      "次に飲む時間と、お薬の名前・1回量を確認します。",
      "飲み終えたら、大きな「この時間のお薬を飲んだ」ボタンを押します。",
      "痛い時などに飲む薬は「必要な時のお薬」から記録します。"
    ]
  },
  {
    title: "「履歴」画面",
    summary: "今日と今週の記録状況を見て、飲めた日や未記録の予定を確認する画面です。",
    image: "/screenshots/patient-history-demo.png",
    alt: "本人モードの履歴画面全体",
    steps: [
      "画面下の「履歴」を押します。",
      "今日の進み具合と、残っている回数を確認します。",
      "最近の記録から、日ごとの服薬状況を振り返ります。"
    ]
  },
  {
    title: "「設定」画面",
    summary: "お薬の通知、家族との連携状態、プライバシーやサポートを確認する画面です。",
    image: "/screenshots/patient-settings-demo.png",
    alt: "本人モードの設定画面全体",
    steps: [
      "画面下の「設定」を押します。",
      "必要に応じて「通知を有効にする」を切り替えます。",
      "「連携中」の表示で、家族と記録を共有していることを確認できます。"
    ]
  }
];

const caregiverScreenGuides = [
  {
    title: "「今日」画面",
    summary: "次に記録する時間と、今日どこまで服薬できたかを確認する画面です。",
    image: "/screenshots/caregiver-today.png",
    alt: "家族モードの今日の服薬画面全体",
    steps: [
      "次に記録する時間と、未記録のお薬を確認します。",
      "本人のそばにいる時は、同じ時間帯のお薬をまとめて代理記録できます。",
      "進み具合の表示で、今日の記録済み回数を確認します。"
    ]
  },
  {
    title: "「薬」画面",
    summary: "登録した定時薬・必要な時のお薬と、残りの数を一覧で確認する画面です。",
    image: "/screenshots/caregiver-medications.png",
    alt: "家族モードの薬を管理する画面全体",
    steps: [
      "画面下の「薬」を押します。",
      "定時薬・必要な時のお薬などの分類で絞り込みます。",
      "お薬を選ぶと、名前・1回量・飲む時間・残数を編集できます。"
    ]
  },
  {
    title: "「在庫」画面",
    summary: "残数と補充の目安を確認し、少なくなったお薬に早めに気づくための画面です。",
    image: "/screenshots/caregiver-inventory.png",
    alt: "家族モードの在庫確認画面全体",
    steps: [
      "画面下の「在庫」を押します。",
      "「要確認」や「管理中」の件数を確認します。",
      "補充した時は対象のお薬を開き、実際の残数に更新します。"
    ]
  },
  {
    title: "「履歴」画面",
    summary: "記録済み・飲み忘れ・未記録を、カレンダーと日別の一覧で確認する画面です。",
    image: "/screenshots/caregiver-history.png",
    alt: "家族モードの服薬履歴画面全体",
    steps: [
      "画面下の「履歴」を押します。",
      "カレンダーの日付を押して、その日の記録を表示します。",
      "色分けを見ながら、記録済み・飲み忘れ・未記録を確認します。"
    ]
  },
  {
    title: "「設定」画面",
    summary: "見守る方の選択、本人との連携、服薬時間や通知を管理する画面です。",
    image: "/screenshots/caregiver-settings.png",
    alt: "家族モードの連携・設定画面全体",
    steps: [
      "画面下の「設定」を押し、見守る方を選びます。",
      "本人の端末とつなぐ時は「コード発行」を押し、表示されたコードを本人へ伝えます。",
      "「時間プリセット設定」で服薬時間を調整し、必要に応じて家族への通知を切り替えます。"
    ]
  },
  {
    title: "お薬の登録画面",
    summary: "お薬の名前、1回量、飲む回数と時間を登録する画面です。",
    image: "/screenshots/caregiver-medication-form.png",
    alt: "家族モードのお薬登録画面全体",
    steps: [
      "「薬」画面から新しいお薬の追加を選びます。",
      "お薬の名前・用量・1回量を入力します。",
      "朝・昼・夜・眠前など、飲む時間を設定して保存します。"
    ]
  }
];

function ScreenGuideGrid({
  screens,
  family = false
}: {
  screens: typeof patientScreenGuides;
  family?: boolean;
}) {
  return (
    <div className={`mode-screen-grid${family ? " family-screens" : ""}`}>
      {screens.map((screen, index) => (
        <article className="mode-screen-card" key={screen.title}>
          <div className="mode-screen-copy">
            <span className="screen-index">画面 {index + 1}</span>
            <h3>{screen.title}</h3>
            <p>{screen.summary}</p>
            <ol>
              {screen.steps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
          </div>
          <figure className="mode-screen-shot">
            <img src={screen.image} alt={screen.alt} />
            <figcaption>実際のアプリ画面</figcaption>
          </figure>
        </article>
      ))}
    </div>
  );
}

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
        <header className="content-width guide-section-header">
          <p className="section-label">使い方 1｜最初の準備</p>
          <h2>最初に行う3つの手順</h2>
          <p>
            家族がお薬を登録し、本人の端末と連携して、今日のお薬を確認できるようにします。
          </p>
        </header>
        <div className="steps-list">
          <article className="step-row family-step">
            <span className="step-number">1</span>
            <span className="step-role">家族の操作</span>
            <div className="step-copy">
              <h3>家族モードでお薬を登録します</h3>
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
              <h3>6桁のコードで本人の端末とつなぎます</h3>
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
              <h3>本人モードでお薬を確認・記録します</h3>
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
        <div className="content-width">
          <header className="mode-section-header">
            <p className="section-label">使い方 2｜本人モード</p>
            <h2 className="section-title">3つの画面で、確認・記録・通知設定</h2>
            <p className="section-lead">
              本人モードは「今日のお薬」「履歴」「設定」の3画面です。下の実画面と同じ場所を見ながら操作してください。
            </p>
          </header>
          <ScreenGuideGrid screens={patientScreenGuides} />
        </div>
      </section>

      <section className="info-section soft" id="caregiver">
        <div className="content-width">
          <header className="mode-section-header">
            <p className="section-label" style={{ color: "var(--orange)" }}>
              使い方 3｜家族モード
            </p>
            <h2 className="section-title">6つの画面で、予定・記録・在庫を管理</h2>
            <p className="section-lead">
              家族モードは「今日」「薬」「在庫」「履歴」「設定」と、お薬の登録画面を使います。確認する目的ごとに画面を切り替えます。
            </p>
          </header>
          <ScreenGuideGrid screens={caregiverScreenGuides} family />
        </div>
      </section>

      <section className="info-section" id="sharing">
        <div className="content-width">
          <p className="section-label">使い方 4｜連携のしくみ</p>
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
              <p className="section-label">使い方 5｜アプリを始める</p>
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
