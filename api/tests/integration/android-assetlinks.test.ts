import { afterEach, describe, expect, it } from "vitest";
import { GET, parseAndroidAppLinkFingerprints } from "../../app/.well-known/assetlinks.json/route";

const FINGERPRINT = Array.from({ length: 32 }, (_, index) =>
  index.toString(16).padStart(2, "0")
).join(":");

afterEach(() => {
  delete process.env.ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS;
});

describe("Android Digital Asset Links", () => {
  it("fails closed when no valid production fingerprint is configured", async () => {
    process.env.ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS = "invalid";
    const response = await GET();
    expect(response.status).toBe(503);
    expect(response.headers.get("cache-control")).toBe("no-store");
    await expect(response.json()).resolves.toEqual({ error: "android_app_links_not_configured" });
  });

  it("normalizes, filters and deduplicates comma-separated fingerprints", () => {
    expect(
      parseAndroidAppLinkFingerprints(` ${FINGERPRINT},invalid,${FINGERPRINT.toUpperCase()} `)
    ).toEqual([FINGERPRINT.toUpperCase()]);
  });

  it("serves the Android package association contract", async () => {
    process.env.ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS = FINGERPRINT;
    const response = await GET();
    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toContain("application/json");
    expect(response.headers.get("cache-control")).toBe("public, max-age=300");
    await expect(response.json()).resolves.toEqual([
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: "com.afterlifearchive.medmanager",
          sha256_cert_fingerprints: [FINGERPRINT.toUpperCase()]
        }
      }
    ]);
  });
});
