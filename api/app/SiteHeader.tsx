type SiteHeaderProps = {
  current?: "home" | "guide" | "legal";
};

export function SiteHeader({ current = "legal" }: SiteHeaderProps) {
  const guideLink =
    current === "guide" ? { href: "#start", label: "使い方" } : { href: "/guide", label: "使い方" };

  return (
    <header className="site-header">
      <nav className="site-header-inner" aria-label="サイト内リンク">
        <a className="site-brand" href="/" aria-label="お薬見守り トップページ">
          <img src="/brand-mark.png" alt="" width="48" height="48" />
          <span>お薬見守り</span>
        </a>
        <div className="desktop-nav">
          <a className={current === "guide" ? "is-current" : undefined} href={guideLink.href}>
            {guideLink.label}
          </a>
        </div>
      </nav>
    </header>
  );
}
