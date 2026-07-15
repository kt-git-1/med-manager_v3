export function SiteFooter() {
  return (
    <footer className="site-footer" aria-label="公開情報">
      <div className="site-footer-inner">
        <div className="site-footer-main">
          <span>お薬見守り</span>
          <div>
            <a href="/guide">詳しい使い方</a>
            <a href="/privacy">プライバシーポリシー</a>
            <a href="/terms">利用規約</a>
            <a href="/support">サポート</a>
          </div>
        </div>
        <p>© 2026 お薬見守り. All rights reserved.</p>
      </div>
      <style>{`
        .site-footer {
          font-family: -apple-system, BlinkMacSystemFont, "Hiragino Sans", "Yu Gothic", "Helvetica Neue", sans-serif;
          background: #f6f8f7;
          color: #12221d;
        }

        .site-footer-inner {
          display: grid;
          gap: 16px;
          width: min(1040px, 100%);
          margin: 0 auto;
          padding: 34px 20px 56px;
        }

        .site-footer-main {
          display: flex;
          align-items: center;
          justify-content: space-between;
          flex-wrap: wrap;
          gap: 14px;
        }

        .site-footer-main span {
          color: #12221d;
          font-weight: 900;
        }

        .site-footer-main div {
          display: flex;
          flex-wrap: wrap;
          gap: 14px;
        }

        .site-footer a {
          color: #24614d;
          font-weight: 800;
          text-decoration: none;
        }

        .site-footer p {
          margin: 0;
          color: #667771;
          font-size: 13px;
          line-height: 1.5;
        }
      `}</style>
    </footer>
  );
}
