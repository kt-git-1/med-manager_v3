import { LegalPage } from "../legalPage";

export const metadata = {
  title: "サポート | お薬見守り"
};

const supportEmail = "support@okusuri-mimamori.com";

export default function SupportPage() {
  return (
    <LegalPage
      title="サポート"
      description="お薬見守りに関する問い合わせ、データ削除、トラブル対応の案内です。"
      sections={[
        {
          title: "問い合わせ",
          body: (
            <>
              アプリの利用方法、不具合、アカウント削除、データ削除については{" "}
              <a href={`mailto:${supportEmail}`}>{supportEmail}</a>{" "}
              までお問い合わせください。
            </>
          )
        },
        {
          title: "アカウント削除",
          body: (
            <>
              家族モードでログイン後、設定画面の「アカウントを削除」から削除できます。
              削除に失敗する場合は、上記メールアドレスへご相談ください。
            </>
          )
        },
        {
          title: "通知が届かない場合",
          body: "端末の通知許可、通信状態、アプリ内の通知設定を確認してください。通知は補助機能であり、服薬確認を通知だけに依存しないでください。"
        },
        {
          title: "医療相談について",
          body: "本サービスでは診断、治療、処方変更、薬学的助言は行いません。お薬や体調に関する相談は医師、薬剤師などの専門家へお願いします。"
        }
      ]}
    />
  );
}
