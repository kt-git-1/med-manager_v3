export const metadata = {
  title: "メール確認完了 | お薬見守りアプリ"
};

export default function AuthConfirmedPage() {
  return (
    <main
      style={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        padding: 24,
        fontFamily:
          '-apple-system, BlinkMacSystemFont, "Helvetica Neue", sans-serif',
        background: "#f7f8fa",
        color: "#111827"
      }}
    >
      <section
        style={{
          width: "100%",
          maxWidth: 520,
          padding: 28,
          borderRadius: 20,
          background: "#ffffff",
          boxShadow: "0 18px 60px rgba(17, 24, 39, 0.10)",
          textAlign: "center"
        }}
      >
        <div
          aria-hidden="true"
          style={{
            width: 64,
            height: 64,
            margin: "0 auto 18px",
            borderRadius: "50%",
            display: "grid",
            placeItems: "center",
            background: "#e8f5ee",
            color: "#15803d",
            fontSize: 34,
            fontWeight: 800
          }}
        >
          ✓
        </div>
        <h1 style={{ margin: "0 0 12px", fontSize: 26, lineHeight: 1.3 }}>
          メール確認が完了しました
        </h1>
        <p
          style={{
            margin: 0,
            color: "#4b5563",
            fontSize: 17,
            lineHeight: 1.7
          }}
        >
          お薬見守りアプリに戻り、家族アカウントでログインしてください。
        </p>
      </section>
    </main>
  );
}
