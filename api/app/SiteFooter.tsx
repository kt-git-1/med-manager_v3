export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer-inner">
        <div className="footer-brand">
          <img src="/brand-mark.png" alt="" width="44" height="44" />
          <div>
            <strong>お薬見守り</strong>
            <span>毎日の服薬を、本人と家族で確認</span>
          </div>
        </div>
        <nav aria-label="フッターリンク">
          <a href="/guide">詳しい使い方</a>
          <a href="/privacy">プライバシーポリシー</a>
          <a href="/terms">利用規約</a>
          <a href="/support">サポート</a>
        </nav>
        <p>© 2026 お薬見守り. All rights reserved.</p>
      </div>
    </footer>
  );
}
