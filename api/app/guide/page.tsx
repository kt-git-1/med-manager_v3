import type { ReactNode } from "react";
import { SiteFooter } from "../SiteFooter";
import { SiteHeader } from "../SiteHeader";

export const metadata = {
  title: "詳しい使い方 | お薬見守り",
  description:
    "お薬見守りの本人モード・家族モードの使い方と、連携コードで服薬予定や記録を共有する手順を画面例つきでご案内します。"
};

const startSteps = [
  ["1", "家族モードを始める", "家族の端末でアカウントを作成し、メール確認後にログインします。"],
  [
    "2",
    "見守る方とお薬を登録",
    "本人の名前、定時薬・頓服、飲む時間、必要に応じて在庫数を登録します。"
  ],
  [
    "3",
    "6桁の連携コードを発行",
    "「連携・設定」から本人を選び、コードを発行してコピーまたは共有します。"
  ],
  [
    "4",
    "本人の端末でコードを入力",
    "本人モードを選び、受け取ったコードを入力すると同じ服薬予定につながります。"
  ]
];

const sharingRows = [
  [
    "家族がお薬・時間を登録／変更",
    "本人の「今日のお薬」に反映",
    "本人が自分で複雑な予定を入力する必要がありません。"
  ],
  [
    "本人が「飲んだ」を記録",
    "家族の今日・履歴に反映",
    "離れていても、服薬できたかを家族が確認できます。"
  ],
  [
    "家族が代理で服薬を記録",
    "本人・家族の履歴に反映",
    "本人のそばにいるときは家族側からも記録できます。"
  ],
  ["服薬を記録", "管理中の在庫数に反映", "家族が残数や補充の目安を確認しやすくなります。"]
];

function Phone({
  label,
  children,
  tone = "green"
}: {
  label: string;
  children: ReactNode;
  tone?: "green" | "orange";
}) {
  return (
    <figure className={`guide-phone-wrap ${tone}`}>
      <figcaption>{label}</figcaption>
      <div className="guide-phone">
        <div className="guide-status">
          <span>9:41</span>
          <span>● ● ●</span>
        </div>
        <div className="guide-phone-body">{children}</div>
        <div className="guide-home-indicator" />
      </div>
    </figure>
  );
}

function LinkCodePhone() {
  return (
    <Phone label="家族の端末：連携コードを発行" tone="orange">
      <div className="phone-title">
        <span>連携・設定</span>
        <strong>山田 太郎さん</strong>
      </div>
      <div className="phone-card code-card">
        <span>本人モード用</span>
        <strong>連携コード</strong>
        <div className="code-digits">2 8 4 6 1 9</div>
        <small>有効期限内に本人の端末で入力してください</small>
      </div>
      <button className="phone-button orange-button" type="button">
        コードを共有
      </button>
      <div className="phone-note">本人の服薬予定と記録を家族で共有できます</div>
    </Phone>
  );
}

function CodeEntryPhone() {
  return (
    <Phone label="本人の端末：受け取ったコードを入力">
      <div className="mode-chip">本人モード</div>
      <div className="phone-title centered">
        <span>家族と連携</span>
        <strong>連携コードを入力</strong>
      </div>
      <div className="entry-code">284619</div>
      <button className="phone-button" type="button">
        連携する
      </button>
      <div className="phone-note">連携後は、家族が登録した今日のお薬が表示されます</div>
    </Phone>
  );
}

function PatientTodayPhone() {
  return (
    <Phone label="本人モード：今日のお薬">
      <div className="phone-title">
        <span>本人モード</span>
        <strong>今日のお薬</strong>
      </div>
      <div className="phone-card next-card">
        <span>次に飲むお薬</span>
        <div className="title-row">
          <strong>昼のお薬</strong>
          <b>12:30</b>
        </div>
        <div className="medicine-row">
          <span>胃のお薬</span>
          <b>1錠</b>
        </div>
        <button className="phone-button" type="button">
          この時間のお薬を飲んだ
        </button>
      </div>
      <div className="slot-row done">
        <b>朝</b>
        <span>07:30　血圧のお薬</span>
        <em>記録済み</em>
      </div>
      <div className="slot-row pending">
        <b>昼</b>
        <span>12:30　胃のお薬</span>
        <em>未記録</em>
      </div>
      <div className="prn-row">
        <div>
          <b>必要な時のお薬</b>
          <span>痛い時・つらい時だけ記録</span>
        </div>
        <strong>›</strong>
      </div>
      <div className="phone-tabs three">
        <b>今日</b>
        <span>履歴</span>
        <span>設定</span>
      </div>
    </Phone>
  );
}

function PatientHistoryPhone() {
  return (
    <Phone label="本人モード：履歴と通知">
      <div className="phone-title">
        <span>本人モード</span>
        <strong>服薬の記録</strong>
      </div>
      <div className="calendar-strip">
        <span>
          月<br />
          <b>8</b>
        </span>
        <span>
          火<br />
          <b>9</b>
        </span>
        <span className="selected">
          水<br />
          <b>10</b>
        </span>
        <span>
          木<br />
          <b>11</b>
        </span>
        <span>
          金<br />
          <b>12</b>
        </span>
      </div>
      <div className="phone-card compact-card">
        <span>6月10日（水）</span>
        <strong>3回分 記録済み</strong>
        <div className="progress">
          <i />
        </div>
      </div>
      <div className="history-row">
        <i className="check">✓</i>
        <div>
          <b>朝のお薬</b>
          <span>07:35に記録</span>
        </div>
      </div>
      <div className="history-row">
        <i className="check">✓</i>
        <div>
          <b>昼のお薬</b>
          <span>12:28に記録</span>
        </div>
      </div>
      <div className="history-row">
        <i>○</i>
        <div>
          <b>夜のお薬</b>
          <span>これから</span>
        </div>
      </div>
      <div className="notice-row">
        <b>通知</b>
        <span>お薬の時間にこの端末へお知らせ</span>
        <em>ON</em>
      </div>
      <div className="phone-tabs three">
        <span>今日</span>
        <b>履歴</b>
        <span>設定</span>
      </div>
    </Phone>
  );
}

function CaregiverTodayPhone() {
  return (
    <Phone label="家族モード：今日の状況" tone="orange">
      <div className="person-title">
        <i>太</i>
        <div>
          <span>山田 太郎さん</span>
          <strong>今日の服薬</strong>
        </div>
      </div>
      <div className="phone-card caregiver-next">
        <span>次にすること</span>
        <div className="title-row">
          <strong>昼 12:30</strong>
          <em>未記録</em>
        </div>
        <small>未記録1件を家族側からも記録できます</small>
        <button className="phone-button orange-button" type="button">
          この時間帯をまとめて記録
        </button>
      </div>
      <div className="care-progress">
        <b>2/4</b>
        <div>
          <span>今日の進み具合</span>
          <strong>2/4回分 完了</strong>
        </div>
      </div>
      <div className="timeline">
        <i className="done-dot" />
        <b>朝 07:30</b>
        <span>飲みました</span>
      </div>
      <div className="timeline">
        <i className="pending-dot" />
        <b>昼 12:30</b>
        <span>未記録</span>
      </div>
      <div className="timeline">
        <i />
        <b>夜 20:30</b>
        <span>これから</span>
      </div>
      <div className="phone-tabs five">
        <span>薬</span>
        <b>今日</b>
        <span>履歴</span>
        <span>在庫</span>
        <span>連携</span>
      </div>
    </Phone>
  );
}

function CaregiverManagePhone() {
  return (
    <Phone label="家族モード：お薬と在庫" tone="orange">
      <div className="phone-title">
        <span>山田 太郎さん</span>
        <strong>お薬</strong>
      </div>
      <button className="add-button" type="button">
        ＋ お薬を登録
      </button>
      <div className="phone-card med-card">
        <div>
          <i className="pill">●</i>
          <strong>血圧のお薬</strong>
          <em>定時薬</em>
        </div>
        <span>朝 07:30　1錠</span>
        <small>在庫 12錠 ・ 補充の目安</small>
      </div>
      <div className="phone-card med-card">
        <div>
          <i className="pill orange-pill">●</i>
          <strong>頭痛のお薬</strong>
          <em>頓服</em>
        </div>
        <span>痛い時　1錠</span>
        <small>在庫 5錠</small>
      </div>
      <div className="stock-alert">
        <b>残り少ないお薬があります</b>
        <span>在庫タブで残数を確認してください</span>
      </div>
      <div className="phone-tabs five">
        <b>薬</b>
        <span>今日</span>
        <span>履歴</span>
        <span>在庫</span>
        <span>連携</span>
      </div>
    </Phone>
  );
}

export default function GuidePage() {
  return (
    <main className="guide-page">
      <SiteHeader current="guide" />

      <section className="guide-hero">
        <div className="guide-hero-copy">
          <p className="guide-eyebrow">APP GUIDE</p>
          <h1>
            「本人」と「家族」がつながる、
            <br />
            お薬見守りの使い方
          </h1>
          <p>
            最初の連携から毎日の記録まで、実際の操作に沿った画面イメージでご案内します。まず家族がお薬を登録し、6桁のコードで本人の端末とつなぎます。
          </p>
          <div className="hero-links">
            <a href="#start">はじめ方を見る</a>
            <a className="secondary" href="#patient">
              モード別の機能を見る
            </a>
          </div>
        </div>
        <div className="connection-visual" aria-label="家族モードと本人モードの連携イメージ">
          <div>
            <span>家族モード</span>
            <strong>予定を登録・状況を確認</strong>
          </div>
          <i>
            <b>↔</b>
            <span>連携</span>
          </i>
          <div>
            <span>本人モード</span>
            <strong>今日のお薬を確認・記録</strong>
          </div>
        </div>
      </section>

      <nav className="guide-jump" aria-label="使い方ページ内メニュー">
        <a href="#start">
          <span>01</span>はじめ方
        </a>
        <a href="#patient">
          <span>02</span>本人モード
        </a>
        <a href="#caregiver">
          <span>03</span>家族モード
        </a>
        <a href="#sharing">
          <span>04</span>連携のしくみ
        </a>
      </nav>

      <section className="guide-section" id="start">
        <header className="section-intro">
          <p className="guide-eyebrow">GETTING STARTED</p>
          <h2>最初は家族モードから準備します</h2>
          <p>本人が毎日迷わず使えるように、家族側で服薬予定を整えてから連携します。</p>
        </header>
        <ol className="start-grid">
          {startSteps.map(([number, title, body]) => (
            <li key={number}>
              <span>{number}</span>
              <div>
                <h3>{title}</h3>
                <p>{body}</p>
              </div>
            </li>
          ))}
        </ol>
        <div className="link-demo">
          <LinkCodePhone />
          <div className="transfer">
            <span>6桁のコードを共有</span>
            <b>→</b>
            <small>コードは大切に扱い、連携する本人にだけ伝えてください</small>
          </div>
          <CodeEntryPhone />
        </div>
        <aside className="guide-tip">
          <b>連携できたら</b>
          <p>
            家族が登録したお薬と時間が本人モードに表示されます。本人側で同じ内容をもう一度登録する必要はありません。
          </p>
        </aside>
      </section>

      <section className="guide-section tinted" id="patient">
        <header className="section-intro">
          <p className="guide-eyebrow">PATIENT MODE</p>
          <h2>本人モード：今日飲む分を、迷わず確認・記録</h2>
          <p>毎日の操作をできるだけ少なくし、次に飲むお薬と記録状況を大きく表示します。</p>
        </header>
        <div className="mode-showcase">
          <div className="phone-pair">
            <PatientTodayPhone />
            <PatientHistoryPhone />
          </div>
          <div className="feature-list">
            <article>
              <span>01</span>
              <div>
                <h3>次に飲むお薬を確認</h3>
                <p>朝・昼・夜・眠前など、家族が設定した時間帯ごとに薬名と量を確認できます。</p>
              </div>
            </article>
            <article>
              <span>02</span>
              <div>
                <h3>飲んだらまとめて記録</h3>
                <p>同じ時間帯のお薬は、分かりやすいボタンからまとめて記録できます。</p>
              </div>
            </article>
            <article>
              <span>03</span>
              <div>
                <h3>頓服も必要な時に記録</h3>
                <p>痛い時などに使う頓服は、定時薬と分けて選び、使った時刻を残せます。</p>
              </div>
            </article>
            <article>
              <span>04</span>
              <div>
                <h3>履歴と通知を確認</h3>
                <p>
                  過去の服薬記録を日付ごとに振り返れます。設定した時刻に端末で通知を受け取ることもできます。
                </p>
              </div>
            </article>
          </div>
        </div>
        <aside className="mode-note patient-note">
          <b>本人モードで行わないこと</b>
          <p>
            お薬名・服用量・時間の登録や変更は家族モードで行います。本人は「確認して記録する」ことに集中できます。
          </p>
        </aside>
      </section>

      <section className="guide-section" id="caregiver">
        <header className="section-intro">
          <p className="guide-eyebrow orange-text">FAMILY MODE</p>
          <h2>家族モード：予定を整え、離れていても状況を確認</h2>
          <p>お薬の準備から、今日の服薬状況、履歴、在庫、本人との連携までを管理します。</p>
        </header>
        <div className="mode-showcase reverse">
          <div className="feature-list orange-list">
            <article>
              <span>01</span>
              <div>
                <h3>今日の進み具合を見守る</h3>
                <p>
                  服薬済み・未記録・これからの予定を時間帯ごとに確認し、必要な時だけ声をかけられます。
                </p>
              </div>
            </article>
            <article>
              <span>02</span>
              <div>
                <h3>お薬・飲む時間を管理</h3>
                <p>
                  定時薬と頓服を登録し、朝・昼・夜・眠前の時刻を本人の生活リズムに合わせて設定できます。
                </p>
              </div>
            </article>
            <article>
              <span>03</span>
              <div>
                <h3>代理記録と履歴の確認</h3>
                <p>
                  本人のそばにいる時は家族側から代理で記録できます。日付ごとの履歴も確認できます。
                </p>
              </div>
            </article>
            <article>
              <span>04</span>
              <div>
                <h3>在庫と通知で気づく</h3>
                <p>
                  お薬の残数と補充の目安を確認できます。服薬や未記録に関する通知を受け取り、変化に気づきやすくします。
                </p>
              </div>
            </article>
          </div>
          <div className="phone-pair">
            <CaregiverTodayPhone />
            <CaregiverManagePhone />
          </div>
        </div>
        <div className="family-tabs" aria-label="家族モードの主なタブ">
          <div>
            <b>薬</b>
            <span>定時薬・頓服・服用時間</span>
          </div>
          <div>
            <b>今日</b>
            <span>今日の予定と進み具合</span>
          </div>
          <div>
            <b>履歴</b>
            <span>日付ごとの服薬記録</span>
          </div>
          <div>
            <b>在庫</b>
            <span>残数と補充の目安</span>
          </div>
          <div>
            <b>連携・設定</b>
            <span>見守る方・コード・通知</span>
          </div>
        </div>
      </section>

      <section className="guide-section sharing-section" id="sharing">
        <header className="section-intro">
          <p className="guide-eyebrow">SHARING</p>
          <h2>連携すると、何が共有される？</h2>
          <p>
            本人と家族は同じ服薬予定・記録を見ます。どちらから記録しても、共通の履歴として確認できます。
          </p>
        </header>
        <div
          className="sharing-table"
          role="table"
          aria-label="本人モードと家族モードで共有される内容"
        >
          <div className="sharing-head" role="row">
            <b role="columnheader">操作</b>
            <b role="columnheader">反映先</b>
            <b role="columnheader">できること</b>
          </div>
          {sharingRows.map(([action, result, detail]) => (
            <div className="sharing-row" role="row" key={action}>
              <strong role="cell">{action}</strong>
              <span role="cell">{result}</span>
              <p role="cell">{detail}</p>
            </div>
          ))}
        </div>
        <div className="safety-grid">
          <article>
            <span>通知について</span>
            <h3>通知は気づくための補助です</h3>
            <p>
              端末の設定や通信状況などにより、通知が届かないことがあります。重要な服薬管理を通知だけに頼らず、アプリの画面も確認してください。
            </p>
          </article>
          <article>
            <span>医療上の注意</span>
            <h3>処方内容は医師・薬剤師の指示を優先</h3>
            <p>
              本アプリは診断や治療を行うものではありません。服用方法の変更や体調に不安がある場合は、医師・薬剤師へ相談してください。
            </p>
          </article>
        </div>
      </section>

      <section className="guide-cta">
        <div>
          <p className="guide-eyebrow">READY TO START?</p>
          <h2>まずは家族モードで、見守る方とお薬を登録</h2>
          <p>アプリ内の初回チュートリアルでも、登録と連携コード発行まで順番にご案内します。</p>
        </div>
        <div>
          <a href="/#download">アプリをダウンロード</a>
          <a className="secondary" href="/support">
            困ったときはサポートへ
          </a>
        </div>
      </section>

      <p className="screen-disclaimer">
        掲載画面は機能を分かりやすく説明するためのイメージです。アプリのバージョンや端末により表示が一部異なる場合があります。
      </p>
      <SiteFooter />
      <style>{`
        :root { color-scheme: light; scroll-behavior: smooth; }
        * { box-sizing: border-box; }
        body { margin: 0; }
        .guide-page { min-height: 100vh; overflow: hidden; background: #f6f8f7; color: #12221d; font-family: -apple-system, BlinkMacSystemFont, "Hiragino Sans", "Yu Gothic", "Helvetica Neue", sans-serif; }
        .guide-page h1,.guide-page h2,.guide-page h3,.guide-page p,.guide-page span,.guide-page strong { overflow-wrap: anywhere; }
        .guide-hero { display: grid; grid-template-columns: minmax(0,1.1fr) minmax(350px,.9fr); gap: 52px; align-items: center; width: min(1120px,100%); min-height: 580px; margin: 0 auto; padding: 78px 40px; }
        .guide-eyebrow { margin: 0 0 12px; color: #2f745d; font-size: 12px; font-weight: 900; letter-spacing: .12em; }
        .guide-hero h1 { margin: 0; font-size: clamp(40px,5vw,64px); line-height: 1.13; letter-spacing: -.035em; }
        .guide-hero-copy > p:not(.guide-eyebrow) { max-width: 720px; margin: 24px 0 0; color: #52635d; font-size: 17px; line-height: 1.9; }
        .hero-links { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 30px; }
        .hero-links a,.guide-cta a { display: inline-flex; align-items: center; justify-content: center; min-height: 52px; padding: 0 20px; border: 1px solid #123b32; border-radius: 8px; background: #123b32; color: #fff; font-size: 14px; font-weight: 900; text-decoration: none; }
        .hero-links a.secondary,.guide-cta a.secondary { background: #fff; color: #123b32; }
        .connection-visual { position: relative; display: grid; gap: 14px; padding: 34px; border: 1px solid rgba(18,34,29,.08); border-radius: 12px; background: linear-gradient(145deg,#e4f4ec,#fffaf0); box-shadow: 0 28px 80px rgba(18,34,29,.12); }
        .connection-visual > div { display: grid; gap: 5px; padding: 24px; border-radius: 10px; background: #fff; box-shadow: 0 12px 30px rgba(18,34,29,.09); }
        .connection-visual > div:first-child { border-left: 5px solid #dc8c2f; }
        .connection-visual > div:last-child { border-left: 5px solid #3c8a69; }
        .connection-visual div span { color: #667771; font-size: 12px; font-weight: 800; }
        .connection-visual div strong { font-size: 18px; }
        .connection-visual > i { display: flex; align-items: center; justify-content: center; gap: 9px; color: #2f745d; font-style: normal; }
        .connection-visual > i b { font-size: 26px; }.connection-visual > i span { font-size: 12px; font-weight: 900; }
        .guide-jump { display: grid; grid-template-columns: repeat(4,1fr); width: min(1040px,calc(100% - 40px)); margin: 0 auto 28px; border: 1px solid rgba(18,34,29,.08); border-radius: 10px; background: #fff; box-shadow: 0 14px 40px rgba(18,34,29,.08); overflow: hidden; }
        .guide-jump a { display: flex; align-items: center; gap: 12px; min-height: 72px; padding: 0 20px; border-right: 1px solid #e5ece8; color: #123b32; font-size: 14px; font-weight: 900; text-decoration: none; }
        .guide-jump a:last-child { border: 0; }.guide-jump span { color: #3c8a69; font-size: 12px; }
        .guide-section { width: min(1120px,100%); margin: 0 auto; padding: 88px 40px; scroll-margin-top: 20px; }
        .guide-section.tinted { width: 100%; padding-left: max(40px,calc((100vw - 1040px)/2)); padding-right: max(40px,calc((100vw - 1040px)/2)); background: #eef6f2; }
        .section-intro { max-width: 780px; margin-bottom: 38px; }.section-intro h2 { margin: 0; font-size: clamp(30px,4vw,43px); line-height: 1.25; letter-spacing: -.025em; }.section-intro > p:not(.guide-eyebrow) { margin: 16px 0 0; color: #52635d; font-size: 16px; line-height: 1.85; }.orange-text { color: #a95b13; }
        .start-grid { display: grid; grid-template-columns: repeat(4,1fr); gap: 14px; margin: 0 0 48px; padding: 0; list-style: none; counter-reset: none; }
        .start-grid li { display: grid; align-content: start; gap: 18px; min-height: 230px; padding: 24px; border: 1px solid rgba(18,34,29,.08); border-radius: 9px; background: #fff; box-shadow: 0 14px 36px rgba(18,34,29,.06); }
        .start-grid li > span { display: grid; place-items: center; width: 38px; height: 38px; border-radius: 50%; background: #e2f2e9; color: #24614d; font-weight: 900; }.start-grid h3 { margin: 0; font-size: 18px; line-height: 1.45; }.start-grid p { margin: 10px 0 0; color: #596a64; font-size: 14px; line-height: 1.75; }
        .link-demo { display: grid; grid-template-columns: minmax(240px,300px) minmax(150px,1fr) minmax(240px,300px); gap: 28px; align-items: center; justify-content: center; padding: 42px; border-radius: 12px; background: linear-gradient(135deg,#edf7f2,#fff9ed); }
        .transfer { display: grid; justify-items: center; gap: 10px; text-align: center; }.transfer span { color: #24614d; font-size: 14px; font-weight: 900; }.transfer b { color: #3c8a69; font-size: 42px; }.transfer small { max-width: 210px; color: #667771; font-size: 11px; line-height: 1.6; }
        .guide-tip,.mode-note { display: flex; gap: 18px; margin-top: 22px; padding: 22px 26px; border-left: 5px solid #3c8a69; background: #fff; box-shadow: 0 10px 30px rgba(18,34,29,.06); }.guide-tip b,.mode-note b { flex: 0 0 auto; color: #24614d; }.guide-tip p,.mode-note p { margin: 0; color: #52635d; line-height: 1.7; }
        .guide-phone-wrap { display: grid; gap: 10px; justify-items: center; margin: 0; }.guide-phone-wrap figcaption { width: 100%; color: #24614d; font-size: 12px; font-weight: 900; text-align: center; }.guide-phone-wrap.orange figcaption { color: #a95b13; }
        .guide-phone { position: relative; width: 100%; aspect-ratio: 393/852; padding: 22px 13px 20px; border: 9px solid #10231e; border-radius: 38px; background: #f5f8f6; box-shadow: 0 28px 60px rgba(18,34,29,.22); overflow: hidden; }.guide-status { display: flex; justify-content: space-between; padding: 0 8px 9px; color: #10231e; font-size: 10px; font-weight: 900; }.guide-phone-body { display: grid; align-content: start; gap: 9px; height: calc(100% - 18px); }.guide-home-indicator { position: absolute; left: 50%; bottom: 7px; width: 92px; height: 4px; border-radius: 9px; background: #10231e; transform: translateX(-50%); }
        .phone-title { display: grid; gap: 2px; padding: 3px 4px 6px; }.phone-title span,.person-title span { color: #667771; font-size: 10px; font-weight: 800; }.phone-title strong { font-size: 18px; }.phone-title.centered { text-align: center; margin-top: 24px; }.phone-title.centered strong { font-size: 20px; }
        .phone-card { display: grid; gap: 9px; padding: 14px; border: 1px solid #dde9e3; border-radius: 15px; background: #fff; box-shadow: 0 8px 18px rgba(18,34,29,.06); }.phone-card > span { color: #667771; font-size: 10px; font-weight: 800; }.phone-card > strong { font-size: 17px; }.code-card { margin-top: 16px; text-align: center; }.code-digits,.entry-code { padding: 13px 8px; border-radius: 10px; background: #edf7f2; color: #123b32; font-size: 21px; font-weight: 900; letter-spacing: .18em; white-space: nowrap; }.code-card small,.phone-note,.caregiver-next small { color: #667771; font-size: 9px; line-height: 1.55; }.entry-code { margin: 15px 4px 0; border: 2px solid #70ac91; background: #fff; text-align: center; }.mode-chip { width: fit-content; margin: 10px auto 0; padding: 6px 10px; border-radius: 99px; background: #e2f2e9; color: #24614d; font-size: 10px; font-weight: 900; }
        .phone-button { min-height: 38px; padding: 0 10px; border: 0; border-radius: 9px; background: #2f745d; color: #fff; font-family: inherit; font-size: 10px; font-weight: 900; }.orange-button { background: #bc691f; }.phone-note { padding: 12px; border-radius: 10px; background: #edf7f2; text-align: center; }
        .title-row,.medicine-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; }.title-row strong { font-size: 17px; }.title-row b { color: #24614d; font-size: 14px; }.medicine-row { padding: 9px; border-radius: 8px; background: #f6f8f7; font-size: 11px; }.next-card { border-top: 4px solid #3c8a69; }
        .slot-row { display: grid; grid-template-columns: 28px 1fr auto; gap: 6px; align-items: center; padding: 11px 9px; border-radius: 10px; background: #fff; border: 1px solid #e3eae7; font-size: 9px; }.slot-row b { font-size: 11px; }.slot-row span { color: #52635d; }.slot-row em { padding: 4px 5px; border-radius: 99px; font-style: normal; font-weight: 900; }.slot-row.done em { background:#e2f2e9;color:#24614d;}.slot-row.pending em { background:#fff0d9;color:#9b5a16;}
        .prn-row { display: flex; justify-content: space-between; align-items: center; padding: 12px; border-radius: 10px; background: #fff; border: 1px solid #e3eae7; }.prn-row div { display: grid; gap: 3px; }.prn-row b { font-size: 11px; }.prn-row span { color:#667771;font-size:8px;}.prn-row > strong { color:#2f745d;font-size:20px; }
        .phone-tabs { display: grid; align-items: center; margin-top: auto; padding: 9px 5px; border-radius: 12px; background: #fff; box-shadow: 0 -4px 16px rgba(18,34,29,.07); color: #77847f; font-size: 8px; text-align: center; }.phone-tabs.three { grid-template-columns: repeat(3,1fr); }.phone-tabs.five { grid-template-columns: repeat(5,1fr); }.phone-tabs b { color:#24614d; }
        .calendar-strip { display:grid;grid-template-columns:repeat(5,1fr);gap:3px;padding:8px;border-radius:11px;background:#fff;text-align:center;color:#667771;font-size:8px;}.calendar-strip span{padding:5px 1px;border-radius:8px;}.calendar-strip b{font-size:12px;color:#12221d;}.calendar-strip .selected{background:#2f745d;color:#fff;}.calendar-strip .selected b{color:#fff;}.compact-card{gap:6px;}.compact-card strong{font-size:14px;}.progress{height:6px;border-radius:9px;background:#e2ebe6;overflow:hidden;}.progress i{display:block;width:75%;height:100%;background:#3c8a69;}
        .history-row { display:flex;align-items:center;gap:9px;padding:10px;border-radius:10px;background:#fff;border:1px solid #e3eae7;}.history-row i{display:grid;place-items:center;width:22px;height:22px;border-radius:50%;background:#edf1ef;color:#667771;font-size:10px;font-style:normal;font-weight:900;}.history-row i.check{background:#dff2e8;color:#24614d;}.history-row div{display:grid;gap:2px;}.history-row b{font-size:10px;}.history-row span{color:#667771;font-size:8px;}.notice-row{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:7px;padding:10px;border-radius:10px;background:#fff3d8;font-size:8px;}.notice-row b{font-size:10px;}.notice-row span{color:#77510e;}.notice-row em{padding:4px 6px;border-radius:99px;background:#bc691f;color:#fff;font-style:normal;font-weight:900;}
        .person-title{display:flex;align-items:center;gap:9px;padding:3px 4px 6px;}.person-title i{display:grid;place-items:center;width:34px;height:34px;border-radius:50%;background:#bc691f;color:#fff;font-style:normal;font-weight:900;}.person-title div{display:grid;gap:1px;}.person-title strong{font-size:15px;}.caregiver-next{border-top:4px solid #bc691f;}.caregiver-next .title-row em{padding:4px 6px;border-radius:99px;background:#fff0d9;color:#9b5a16;font-size:8px;font-style:normal;font-weight:900;}.care-progress{display:flex;align-items:center;gap:11px;padding:11px;border-radius:11px;background:#fff;}.care-progress>b{display:grid;place-items:center;width:40px;height:40px;border:5px solid #78b699;border-radius:50%;font-size:10px;}.care-progress div{display:grid;gap:2px;}.care-progress span{color:#667771;font-size:8px;}.care-progress strong{font-size:12px;}.timeline{display:grid;grid-template-columns:10px 1fr auto;gap:7px;align-items:center;padding:9px;border-radius:9px;background:#fff;border:1px solid #e3eae7;font-size:9px;}.timeline i{width:8px;height:8px;border-radius:50%;background:#c7d0cc;}.timeline i.done-dot{background:#3c8a69;}.timeline i.pending-dot{background:#dc8c2f;}.timeline span{color:#667771;}
        .add-button{min-height:33px;border:1px solid #bc691f;border-radius:8px;background:#fff8ef;color:#a95b13;font-family:inherit;font-size:9px;font-weight:900;}.med-card{gap:7px;}.med-card>div{display:grid;grid-template-columns:15px 1fr auto;gap:6px;align-items:center;}.med-card strong{font-size:11px;}.med-card em{padding:3px 5px;border-radius:99px;background:#e2f2e9;color:#24614d;font-size:7px;font-style:normal;font-weight:900;}.med-card>span{color:#12221d;font-size:9px;}.med-card small{color:#667771;font-size:8px;}.pill{color:#3c8a69;font-style:normal;}.orange-pill{color:#dc8c2f;}.stock-alert{display:grid;gap:3px;padding:11px;border-radius:10px;background:#fff0d9;color:#815017;}.stock-alert b{font-size:10px;}.stock-alert span{font-size:8px;}
        .mode-showcase { display:grid;grid-template-columns:minmax(500px,1.15fr) minmax(320px,.85fr);gap:58px;align-items:center;}.mode-showcase.reverse{grid-template-columns:minmax(320px,.85fr) minmax(500px,1.15fr);}.phone-pair{display:grid;grid-template-columns:repeat(2,minmax(220px,280px));gap:20px;justify-content:center;align-items:start;}.phone-pair .guide-phone-wrap:nth-child(2){margin-top:48px;}
        .feature-list{display:grid;gap:4px;}.feature-list article{display:grid;grid-template-columns:38px 1fr;gap:14px;padding:20px 0;border-bottom:1px solid #dce5e0;}.feature-list article>span{display:grid;place-items:center;width:34px;height:34px;border-radius:50%;background:#dff0e7;color:#24614d;font-size:10px;font-weight:900;}.feature-list h3{margin:0;font-size:18px;line-height:1.4;}.feature-list p{margin:8px 0 0;color:#596a64;font-size:14px;line-height:1.75;}.orange-list article>span{background:#fff0d9;color:#a95b13;}.patient-note{background:#f7fbf9;}
        .family-tabs{display:grid;grid-template-columns:repeat(5,1fr);gap:10px;margin-top:42px;}.family-tabs div{display:grid;gap:5px;padding:18px;border-top:4px solid #dc8c2f;border-radius:8px;background:#fff;box-shadow:0 10px 24px rgba(18,34,29,.06);}.family-tabs b{font-size:14px;}.family-tabs span{color:#667771;font-size:11px;line-height:1.5;}
        .sharing-section{padding-top:72px;}.sharing-table{border:1px solid #dce5e0;border-radius:10px;background:#fff;overflow:hidden;box-shadow:0 16px 46px rgba(18,34,29,.07);}.sharing-head,.sharing-row{display:grid;grid-template-columns:.9fr .9fr 1.2fr;}.sharing-head{background:#123b32;color:#fff;}.sharing-head b{padding:15px 20px;font-size:12px;}.sharing-row>*{margin:0;padding:20px;border-right:1px solid #e1e8e4;border-bottom:1px solid #e1e8e4;}.sharing-row:last-child>*{border-bottom:0;}.sharing-row>*:last-child{border-right:0;}.sharing-row strong{font-size:14px;line-height:1.55;}.sharing-row span{color:#24614d;font-size:13px;font-weight:900;line-height:1.55;}.sharing-row p{color:#596a64;font-size:13px;line-height:1.65;}
        .safety-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:16px;margin-top:26px;}.safety-grid article{padding:26px;border-radius:9px;background:#fff8e9;border:1px solid #f0dfbd;}.safety-grid span{color:#9b5a16;font-size:11px;font-weight:900;}.safety-grid h3{margin:8px 0 0;font-size:18px;}.safety-grid p{margin:10px 0 0;color:#665e50;font-size:13px;line-height:1.75;}
        .guide-cta{display:flex;align-items:center;justify-content:space-between;gap:28px;width:min(1040px,calc(100% - 40px));margin:20px auto 58px;padding:38px 42px;border-radius:12px;background:#123b32;color:#fff;}.guide-cta .guide-eyebrow{color:#9fe0c4;}.guide-cta h2{margin:0;font-size:28px;line-height:1.35;}.guide-cta p:not(.guide-eyebrow){margin:10px 0 0;color:#d8f6e9;line-height:1.7;}.guide-cta>div:last-child{display:grid;gap:10px;flex:0 0 auto;}.guide-cta a{background:#fff;color:#123b32;border-color:#fff;}.guide-cta a.secondary{background:transparent;color:#fff;}.screen-disclaimer{width:min(1040px,calc(100% - 40px));margin:0 auto;padding-bottom:34px;color:#71807b;font-size:11px;line-height:1.6;text-align:center;}
        @media(max-width:920px){.guide-hero{grid-template-columns:1fr;min-height:auto;padding:58px 24px;}.connection-visual{max-width:600px}.guide-jump{grid-template-columns:repeat(2,1fr)}.guide-section,.guide-section.tinted{padding:68px 24px}.start-grid{grid-template-columns:repeat(2,1fr)}.link-demo{grid-template-columns:minmax(220px,290px) 90px minmax(220px,290px);padding:30px 20px}.mode-showcase,.mode-showcase.reverse{grid-template-columns:1fr}.mode-showcase.reverse .feature-list{order:2}.phone-pair{order:1}.feature-list{order:2}.family-tabs{grid-template-columns:repeat(3,1fr)}.guide-cta{display:grid}.guide-cta>div:last-child{grid-template-columns:repeat(2,minmax(0,1fr));width:100%}}
        @media(max-width:640px){.guide-hero{padding:44px 18px}.guide-hero h1{font-size:37px}.guide-hero h1 br{display:none}.guide-jump{width:calc(100% - 28px)}.guide-jump a{min-height:60px;padding:0 12px;font-size:12px}.guide-section,.guide-section.tinted{padding:56px 16px}.start-grid{grid-template-columns:1fr}.start-grid li{min-height:auto;grid-template-columns:38px 1fr}.link-demo{grid-template-columns:1fr;padding:28px 18px}.link-demo .guide-phone-wrap{width:min(290px,100%);margin:auto}.transfer b{transform:rotate(90deg)}.phone-pair{grid-template-columns:1fr}.phone-pair .guide-phone-wrap{width:min(300px,100%);margin:0 auto}.phone-pair .guide-phone-wrap:nth-child(2){margin-top:18px}.guide-tip,.mode-note{display:grid}.family-tabs{grid-template-columns:repeat(2,1fr)}.sharing-head{display:none}.sharing-row{grid-template-columns:1fr}.sharing-row>*{padding:14px 18px;border-right:0}.sharing-row strong{padding-top:20px;border-bottom:0}.sharing-row span{border-bottom:0}.safety-grid{grid-template-columns:1fr}.guide-cta{width:calc(100% - 28px);padding:30px 22px}.guide-cta>div:last-child{grid-template-columns:1fr}.screen-disclaimer{width:calc(100% - 32px)}}
      `}</style>
    </main>
  );
}
