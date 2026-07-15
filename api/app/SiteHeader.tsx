type SiteHeaderProps = {
  current?: "home" | "guide" | "legal";
};

const publicLinks = [
  { href: "/guide#patient", label: "本人モード" },
  { href: "/guide#caregiver", label: "家族モード" },
  { href: "/guide#sharing", label: "連携のしくみ" },
  { href: "/privacy", label: "プライバシー" },
  { href: "/terms", label: "利用規約" },
  { href: "/support", label: "サポート" }
];

export function SiteHeader({ current = "legal" }: SiteHeaderProps) {
  const firstLink =
    current === "home"
      ? { href: "#overview", label: "はじめ方" }
      : { href: "/#overview", label: "はじめ方" };
  const guideLink =
    current === "guide" ? { href: "#start", label: "使い方" } : { href: "/guide", label: "使い方" };
  const links = [firstLink, guideLink, ...publicLinks];

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
                (current === "guide" && link.label === "使い方") ||
                (current === "home" && link.label === "はじめ方")
                  ? "is-current"
                  : undefined
              }
              href={link.href}
              key={`${link.href}-${link.label}`}
            >
              {link.label}
            </a>
          ))}
        </div>
        <details className="mobile-nav">
          <summary>メニュー</summary>
          <div>
            {links.map((link) => (
              <a href={link.href} key={`${link.href}-${link.label}`}>
                {link.label}
              </a>
            ))}
          </div>
        </details>
      </nav>
    </header>
  );
}
