import type { ReactNode } from "react";
import { SiteFooter } from "./SiteFooter";
import { SiteHeader } from "./SiteHeader";

type LegalSection = { title: string; summary?: string; body: ReactNode };
type LegalPageProps = {
  title: string;
  description: string;
  lead?: string;
  updatedAt?: string;
  notice?: string;
  sections: LegalSection[];
};

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
    <main className="page-shell">
      <SiteHeader current="legal" />
      <div className="legal-shell">
        <header className="legal-hero">
          <div>
            <p className="section-label">大切なご案内</p>
            <h1>{title}</h1>
            <p>{description}</p>
          </div>
          <aside aria-label="ページ情報">
            {updatedAt ? (
              <p>
                <span>最終更新</span>
                <strong>{updatedAt}</strong>
              </p>
            ) : null}
            <p>
              <span>問い合わせ</span>
              <a href={`mailto:${supportEmail}`}>{supportEmail}</a>
            </p>
          </aside>
        </header>

        {lead || notice ? (
          <section className="legal-intro" aria-label="重要な案内">
            {lead ? <p>{lead}</p> : null}
            {notice ? <strong>{notice}</strong> : null}
          </section>
        ) : null}

        <div className="legal-layout">
          <aside className="legal-toc" aria-label="目次">
            <strong>このページの内容</strong>
            {sections.map((section, index) => (
              <a key={section.title} href={`#section-${index + 1}`}>
                {section.title}
              </a>
            ))}
          </aside>

          <div className="legal-content">
            {sections.map((section, index) => (
              <section id={`section-${index + 1}`} key={section.title}>
                <span>{index + 1}</span>
                <div>
                  <h2>{section.title}</h2>
                  {section.summary ? <strong>{section.summary}</strong> : null}
                  <p>{section.body}</p>
                </div>
              </section>
            ))}
          </div>
        </div>

        <section className="legal-contact" aria-label="サポートへの問い合わせ">
          <div>
            <p className="section-label">困ったときは</p>
            <h2>不明点や削除依頼はサポートへ</h2>
            <p>
              利用方法、アカウント削除、データ削除、不具合についてメールでお問い合わせいただけます。
            </p>
          </div>
          <a className="primary-button" href={`mailto:${supportEmail}`}>
            メールで問い合わせる
          </a>
        </section>
      </div>
      <SiteFooter />
    </main>
  );
}
