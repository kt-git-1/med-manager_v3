import Link from "next/link";
import type { ReactNode } from "react";

type LegalSection = {
  title: string;
  summary?: string;
  body: ReactNode;
};

type LegalPageProps = {
  title: string;
  description: string;
  lead?: string;
  updatedAt?: string;
  notice?: string;
  sections: LegalSection[];
};

const navItems = [
  { href: "/", label: "ホーム" },
  { href: "/privacy", label: "プライバシー" },
  { href: "/terms", label: "利用規約" },
  { href: "/support", label: "サポート" }
];

const supportEmail = "support@okusuri-mimamori.com";

export function LegalPage({
  title,
  description,
  lead,
  updatedAt,
  notice,
  sections
}: LegalPageProps) {
  return (
    <main className="legal-page">
      <div className="legal-shell">
        <nav className="site-nav" aria-label="サイト内リンク">
          <Link className="brand-link" href="/">
            <span>お薬見守り</span>
          </Link>
          <div>
            {navItems.map((item) => (
              <Link key={item.href} href={item.href}>
                {item.label}
              </Link>
            ))}
          </div>
        </nav>

        <header className="legal-hero">
          <div>
            <p className="eyebrow">Legal / Support</p>
            <h1>{title}</h1>
            <span>{description}</span>
          </div>
          <aside className="hero-summary" aria-label="ページの要点">
            {updatedAt ? (
              <div>
                <span>最終更新</span>
                <strong>{updatedAt}</strong>
              </div>
            ) : null}
            <div>
              <span>問い合わせ</span>
              <a href={`mailto:${supportEmail}`}>{supportEmail}</a>
            </div>
          </aside>
        </header>

        {lead || notice ? (
          <section className="intro-band" aria-label="重要な案内">
            {lead ? <p>{lead}</p> : null}
            {notice ? <strong>{notice}</strong> : null}
          </section>
        ) : null}

        <div className="legal-layout">
          <aside className="toc" aria-label="目次">
            <p>目次</p>
            {sections.map((section, index) => (
              <a key={section.title} href={`#section-${index + 1}`}>
                {section.title}
              </a>
            ))}
          </aside>

          <div className="legal-content">
            {sections.map((section, index) => (
              <section id={`section-${index + 1}`} key={section.title}>
                <span className="section-number">{String(index + 1).padStart(2, "0")}</span>
                <div>
                  <h2>{section.title}</h2>
                  {section.summary ? <strong>{section.summary}</strong> : null}
                  <p>{section.body}</p>
                </div>
              </section>
            ))}
          </div>
        </div>

        <section className="contact-panel" aria-label="サポートへの問い合わせ">
          <div>
            <p className="eyebrow">Need help?</p>
            <h2>不明点や削除依頼はサポートへ</h2>
            <p>
              アプリの利用方法、アカウント削除、データ削除、不具合についてはメールでお問い合わせください。
            </p>
          </div>
          <a className="mail-button" href={`mailto:${supportEmail}`}>
            メールで問い合わせる
          </a>
        </section>
      </div>
      <style>{`
        * {
          box-sizing: border-box;
        }

        body {
          margin: 0;
          background: #f6f8f7;
        }

        .legal-page {
          min-height: 100vh;
          font-family: -apple-system, BlinkMacSystemFont, "Hiragino Sans", "Yu Gothic", "Helvetica Neue", sans-serif;
          color: #12221d;
          background:
            linear-gradient(180deg, #eef6f2 0, #f6f8f7 360px),
            #f6f8f7;
          padding: 32px 20px 72px;
        }

        .legal-shell {
          width: min(1080px, 100%);
          margin: 0 auto;
        }

        .site-nav {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 18px;
          margin-bottom: 28px;
        }

        .site-nav div {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
          justify-content: flex-end;
        }

        .site-nav a {
          display: inline-flex;
          align-items: center;
          min-height: 38px;
          padding: 0 12px;
          border-radius: 999px;
          color: #24614d;
          font-weight: 800;
          text-decoration: none;
          background: rgba(255, 255, 255, 0.72);
          border: 1px solid rgba(18, 34, 29, 0.08);
        }

        .brand-link {
          color: #123b32 !important;
          background: transparent !important;
          border: 0 !important;
          padding: 0 !important;
          font-size: 17px;
        }

        .legal-hero {
          display: grid;
          grid-template-columns: minmax(0, 1fr) minmax(240px, 320px);
          gap: 28px;
          align-items: end;
          padding: 44px;
          border-radius: 8px;
          background:
            linear-gradient(135deg, rgba(18, 59, 50, 0.98), rgba(36, 97, 77, 0.9)),
            #123b32;
          color: #ffffff;
        }

        .eyebrow {
          margin: 0;
          color: #b9ead5;
          font-size: 13px;
          font-weight: 800;
          letter-spacing: 0;
        }

        h1 {
          margin: 10px 0 0;
          font-size: 42px;
          line-height: 1.2;
          letter-spacing: 0;
        }

        .legal-hero span {
          display: block;
          margin-top: 14px;
          color: #e8fff5;
          line-height: 1.8;
        }

        .hero-summary {
          display: grid;
          gap: 12px;
        }

        .hero-summary div {
          display: grid;
          gap: 5px;
          padding: 16px;
          border-radius: 8px;
          background: rgba(255, 255, 255, 0.1);
          border: 1px solid rgba(255, 255, 255, 0.16);
        }

        .hero-summary span {
          margin: 0;
          color: #b9ead5;
          font-size: 12px;
          font-weight: 800;
        }

        .hero-summary strong,
        .hero-summary a {
          color: #ffffff;
          font-size: 15px;
          font-weight: 800;
          text-decoration: none;
        }

        .intro-band {
          display: grid;
          gap: 12px;
          margin-top: 20px;
          padding: 22px 26px;
          border-radius: 8px;
          background: #fffdf7;
          border: 1px solid rgba(119, 81, 14, 0.16);
        }

        .intro-band p,
        .intro-band strong {
          margin: 0;
          color: #4d6159;
          line-height: 1.85;
        }

        .intro-band strong {
          color: #77510e;
        }

        .legal-layout {
          display: grid;
          grid-template-columns: 230px minmax(0, 1fr);
          gap: 20px;
          align-items: start;
          margin-top: 20px;
        }

        .toc {
          position: sticky;
          top: 18px;
          display: grid;
          gap: 4px;
          padding: 18px;
          border-radius: 8px;
          background: #ffffff;
          border: 1px solid rgba(18, 34, 29, 0.08);
        }

        .toc p {
          margin: 0 0 8px;
          color: #12221d;
          font-size: 13px;
          font-weight: 800;
        }

        .toc a {
          padding: 8px 0;
          color: #52635d;
          font-size: 14px;
          font-weight: 700;
          line-height: 1.45;
          text-decoration: none;
          border-top: 1px solid #edf2ef;
        }

        .legal-content {
          display: grid;
          gap: 12px;
        }

        section {
          scroll-margin-top: 18px;
        }

        .legal-content section {
          display: grid;
          grid-template-columns: 42px minmax(0, 1fr);
          gap: 16px;
          padding: 26px;
          border: 1px solid rgba(18, 34, 29, 0.08);
          border-radius: 8px;
          background: #ffffff;
          box-shadow: 0 14px 36px rgba(18, 34, 29, 0.05);
        }

        .section-number {
          display: inline-grid;
          place-items: center;
          width: 42px;
          height: 42px;
          border-radius: 999px;
          background: #e7f5ee;
          color: #24614d;
          font-size: 13px;
          font-weight: 900;
        }

        h2 {
          margin: 0 0 10px;
          font-size: 22px;
          line-height: 1.35;
          letter-spacing: 0;
        }

        .legal-content section strong {
          display: block;
          margin: 0 0 8px;
          color: #123b32;
          line-height: 1.65;
        }

        p {
          margin: 0;
          color: #4d6159;
          line-height: 1.9;
        }

        .legal-content a {
          color: #24614d;
          font-weight: 800;
          text-decoration-thickness: 0.08em;
          text-underline-offset: 0.18em;
        }

        .contact-panel {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 20px;
          margin-top: 20px;
          padding: 28px;
          border-radius: 8px;
          background: #123b32;
          color: #ffffff;
        }

        .contact-panel h2 {
          margin-bottom: 8px;
        }

        .contact-panel p {
          color: #e8fff5;
        }

        .mail-button {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          flex: 0 0 auto;
          min-height: 46px;
          padding: 0 18px;
          border-radius: 999px;
          background: #ffffff;
          color: #123b32;
          font-weight: 900;
          text-decoration: none;
        }

        @media (max-width: 780px) {
          .legal-page {
            padding: 22px 14px 56px;
          }

          .site-nav {
            display: grid;
            gap: 14px;
          }

          .site-nav div {
            justify-content: flex-start;
          }

          .legal-hero,
          .legal-layout,
          .contact-panel {
            grid-template-columns: 1fr;
          }

          .legal-hero,
          .legal-content section,
          .contact-panel {
            padding: 24px;
          }

          .toc {
            position: static;
          }

          .legal-content section {
            grid-template-columns: 1fr;
          }

          h1 {
            font-size: 30px;
          }

          .contact-panel {
            display: grid;
          }

          .mail-button {
            width: 100%;
          }
        }
      `}</style>
    </main>
  );
}
