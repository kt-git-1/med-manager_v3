type SiteHeaderProps = {
  current?: "home" | "guide" | "legal";
};

const legalLinks = [
  { href: "/privacy", label: "プライバシー" },
  { href: "/terms", label: "利用規約" },
  { href: "/support", label: "サポート" }
];

export function SiteHeader({ current = "legal" }: SiteHeaderProps) {
  const sectionLinks =
    current === "home"
      ? [
          { href: "#overview", label: "概要" },
          { href: "/guide", label: "詳しい使い方" },
          { href: "#demo", label: "画面例" },
          { href: "#download", label: "ダウンロード" }
        ]
      : current === "guide"
        ? [
            { href: "#start", label: "はじめ方" },
            { href: "#patient", label: "本人モード" },
            { href: "#caregiver", label: "家族モード" },
            { href: "#sharing", label: "連携のしくみ" }
          ]
        : [
            { href: "/#overview", label: "概要" },
            { href: "/guide", label: "詳しい使い方" },
            { href: "/#demo", label: "画面例" },
            { href: "/#download", label: "ダウンロード" }
          ];
  const navLinks = [...sectionLinks, ...legalLinks];

  return (
    <header className="site-header">
      <nav className="site-header-nav" aria-label="サイト内リンク">
        <a className="site-header-brand" href="/">
          <span className="site-header-mark" aria-hidden="true" />
          <span>お薬見守り</span>
        </a>
        <div className="site-header-links">
          {navLinks.map((item) => (
            <a key={item.href} href={item.href}>
              {item.label}
            </a>
          ))}
        </div>
        <details className="site-header-menu">
          <summary aria-label="メニューを開く">
            <span>メニュー</span>
            <em />
          </summary>
          <div>
            {navLinks.map((item) => (
              <a key={item.href} href={item.href}>
                {item.label}
              </a>
            ))}
          </div>
        </details>
      </nav>
      <style>{`
        .site-header {
          font-family: -apple-system, BlinkMacSystemFont, "Hiragino Sans", "Yu Gothic", "Helvetica Neue", sans-serif;
          background: #f6f8f7;
          border-bottom: 1px solid rgba(18, 34, 29, 0.08);
        }

        .site-header-nav {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 18px;
          width: min(1040px, 100%);
          min-height: 76px;
          margin: 0 auto;
          padding: 0 20px;
        }

        .site-header-brand {
          display: inline-flex;
          align-items: center;
          gap: 10px;
          flex: 0 0 auto;
          color: #123b32;
          font-size: 18px;
          font-weight: 800;
          text-decoration: none;
        }

        .site-header-mark {
          display: block;
          width: 44px;
          height: 44px;
          border-radius: 12px;
          background:
            url("/brand-mark.png") center / contain no-repeat,
            #ffffff;
          box-shadow: 0 8px 22px rgba(18, 34, 29, 0.12);
        }

        .site-header-links {
          display: flex;
          flex-wrap: wrap;
          justify-content: flex-end;
          gap: 8px;
        }

        .site-header-links a,
        .site-header-menu a {
          display: inline-flex;
          align-items: center;
          min-height: 38px;
          padding: 0 12px;
          border-radius: 999px;
          background: #ffffff;
          border: 1px solid rgba(18, 34, 29, 0.08);
          color: #24614d;
          font-size: 14px;
          font-weight: 800;
          text-decoration: none;
          white-space: nowrap;
        }

        .site-header-menu {
          display: none;
        }

        .site-header-menu summary {
          display: inline-flex;
          align-items: center;
          gap: 10px;
          min-height: 42px;
          padding: 0 14px 0 16px;
          border-radius: 8px;
          background: #123b32;
          border: 1px solid rgba(18, 34, 29, 0.08);
          color: #ffffff;
          cursor: pointer;
          list-style: none;
          box-shadow: 0 12px 28px rgba(18, 34, 29, 0.18);
        }

        .site-header-menu summary::-webkit-details-marker {
          display: none;
        }

        .site-header-menu summary:focus-visible {
          outline: 3px solid rgba(60, 138, 105, 0.34);
          outline-offset: 3px;
        }

        .site-header-menu summary span {
          color: #ffffff;
          font-size: 13px;
          font-weight: 900;
          line-height: 1;
        }

        .site-header-menu summary em {
          width: 8px;
          height: 8px;
          border-right: 2px solid currentColor;
          border-bottom: 2px solid currentColor;
          transform: translateY(-2px) rotate(45deg);
          transition: transform 160ms ease;
        }

        .site-header-menu[open] summary em {
          transform: translateY(2px) rotate(225deg);
        }

        .site-header-menu div {
          position: absolute;
          top: calc(100% + 10px);
          right: 16px;
          z-index: 10;
          display: grid;
          gap: 6px;
          width: min(260px, calc(100vw - 32px));
          padding: 10px;
          border-radius: 8px;
          background: rgba(255, 255, 255, 0.98);
          border: 1px solid rgba(18, 34, 29, 0.08);
          box-shadow: 0 22px 60px rgba(18, 34, 29, 0.2);
          backdrop-filter: blur(12px);
        }

        .site-header-menu a {
          justify-content: flex-start;
          width: 100%;
          min-height: 44px;
          border-radius: 6px;
          background: transparent;
          border: 0;
        }

        .site-header-menu a:hover {
          background: #e7f5ee;
        }

        @media (max-width: 1080px) {
          .site-header-nav {
            position: relative;
            min-height: auto;
            padding: 18px 20px;
          }

          .site-header-links {
            display: none;
          }

          .site-header-menu {
            display: block;
          }
        }

        @media (max-width: 480px) {
          .site-header-nav {
            padding: 18px 16px;
          }

          .site-header-brand {
            font-size: 17px;
          }
        }
      `}</style>
    </header>
  );
}
