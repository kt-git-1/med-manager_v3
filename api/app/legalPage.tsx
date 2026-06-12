import Link from "next/link";

type LegalSection = {
  title: string;
  body: string;
};

type LegalPageProps = {
  title: string;
  description: string;
  sections: LegalSection[];
};

export function LegalPage({ title, description, sections }: LegalPageProps) {
  return (
    <main className="legal-page">
      <div className="legal-shell">
        <Link className="back-link" href="/">
          お薬見守り
        </Link>
        <header>
          <p>お薬見守り</p>
          <h1>{title}</h1>
          <span>{description}</span>
        </header>
        <div className="legal-content">
          {sections.map((section) => (
            <section key={section.title}>
              <h2>{section.title}</h2>
              <p>{section.body}</p>
            </section>
          ))}
        </div>
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
          background: #f6f8f7;
          padding: 32px 20px 72px;
        }

        .legal-shell {
          width: min(880px, 100%);
          margin: 0 auto;
        }

        .back-link {
          display: inline-flex;
          margin-bottom: 24px;
          color: #24614d;
          font-weight: 800;
          text-decoration: none;
        }

        header {
          display: grid;
          gap: 12px;
          padding: 36px;
          border-radius: 8px;
          background: #123b32;
          color: #ffffff;
        }

        header p {
          margin: 0;
          color: #b9ead5;
          font-size: 13px;
          font-weight: 800;
        }

        h1 {
          margin: 0;
          font-size: 38px;
          line-height: 1.2;
          letter-spacing: 0;
        }

        header span {
          color: #e8fff5;
          line-height: 1.8;
        }

        .legal-content {
          display: grid;
          gap: 16px;
          margin-top: 20px;
        }

        section {
          padding: 28px;
          border: 1px solid rgba(18, 34, 29, 0.08);
          border-radius: 8px;
          background: #ffffff;
          box-shadow: 0 18px 50px rgba(18, 34, 29, 0.06);
        }

        h2 {
          margin: 0 0 10px;
          font-size: 22px;
          line-height: 1.35;
          letter-spacing: 0;
        }

        p {
          margin: 0;
          color: #4d6159;
          line-height: 1.9;
        }

        @media (max-width: 640px) {
          .legal-page {
            padding: 22px 14px 56px;
          }

          header,
          section {
            padding: 24px;
          }

          h1 {
            font-size: 30px;
          }
        }
      `}</style>
    </main>
  );
}
