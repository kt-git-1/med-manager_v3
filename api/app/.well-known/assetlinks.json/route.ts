const ANDROID_PACKAGE_NAME = "com.afterlifearchive.medmanager";
const SHA256_FINGERPRINT = /^(?:[0-9a-fA-F]{2}:){31}[0-9a-fA-F]{2}$/;

export function parseAndroidAppLinkFingerprints(value: string | undefined): string[] {
  if (!value) return [];
  return [
    ...new Set(
      value
        .split(",")
        .map((fingerprint) => fingerprint.trim().toUpperCase())
        .filter((fingerprint) => SHA256_FINGERPRINT.test(fingerprint))
    )
  ];
}

export async function GET() {
  const fingerprints = parseAndroidAppLinkFingerprints(
    process.env.ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS
  );
  if (fingerprints.length === 0) {
    return Response.json(
      { error: "android_app_links_not_configured" },
      { status: 503, headers: { "Cache-Control": "no-store" } }
    );
  }

  return Response.json(
    [
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: ANDROID_PACKAGE_NAME,
          sha256_cert_fingerprints: fingerprints
        }
      }
    ],
    { headers: { "Cache-Control": "public, max-age=300" } }
  );
}
