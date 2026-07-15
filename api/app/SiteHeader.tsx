type SiteHeaderProps = {
  current?: "home" | "guide" | "legal";
};

export function SiteHeader({ current = "legal" }: SiteHeaderProps) {
  const startLink =
    current === "home"
      ? { href: "#overview", label: "はじめ方" }
      : { href: "/#overview", label: "はじめ方" };
  const guideLink =
    current === "guide" ? { href: "#start", label: "使い方" } : { href: "/guide", label: "使い方" };
  const links = [
    startLink,
    guideLink,
    { href: "/privacy", label: "プライバシー" },
    { href: "/terms", label: "利用規約" },
    { href: "/support", label: "サポート" }
  ];

  return (
    <header className="site-header">
      <nav className="site-header-inner" aria-label="サイト内リンク">
        <a className="site-brand" href="/" aria-label="お薬見守り トップページ">
          <img src="/brand-mark.png" alt="" width="48" height="48" />
          <span>お薬見守り</span>
        </a>
        <div className="desktop-nav">
          {links.map((link) => (
            <a
              className={
                (current === "home" && link.label === "はじめ方") ||
                (current === "guide" && link.label === "使い方")
                  ? "is-current"
                  : undefined
              }
              href={link.href}
              key={link.href}
            >
              {link.label}
            </a>
          ))}
        </div>
        <details className="mobile-nav">
          <summary>メニュー</summary>
          <div>
            {links.map((link) => (
              <a href={link.href} key={link.href}>
                {link.label}
              </a>
            ))}
          </div>
        </details>
      </nav>
    </header>
  );
}
