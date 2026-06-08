import crypto from "node:crypto";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const logMock = vi.fn();

vi.mock("../../src/logging/logger", () => ({
  log: (...args: unknown[]) => logMock(...args)
}));

function serviceAccountEnv(): string {
  const { privateKey } = crypto.generateKeyPairSync("rsa", {
    modulusLength: 2048,
    publicKeyEncoding: { type: "spki", format: "pem" },
    privateKeyEncoding: { type: "pkcs8", format: "pem" }
  });

  return Buffer.from(
    JSON.stringify({
      project_id: "firebase-project-1",
      client_email: "fcm-sender@firebase-project-1.iam.gserviceaccount.com",
      private_key: privateKey
    })
  ).toString("base64");
}

describe("sendFcmMessage", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.unstubAllGlobals();
    logMock.mockReset();
    process.env.FCM_SERVICE_ACCOUNT_JSON = serviceAccountEnv();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.FCM_SERVICE_ACCOUNT_JSON;
  });

  it("does not call FCM send when OAuth2 token response has no access_token", async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({}), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const { sendFcmMessage } = await import("../../src/services/fcmService");

    const result = await sendFcmMessage(
      "device-token-1",
      { title: "服薬記録", body: "服用しました" },
      { type: "DOSE_TAKEN" }
    );

    expect(result).toEqual({ success: false, errorCode: "UNKNOWN" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(logMock).toHaveBeenCalledWith(
      "error",
      "FCM OAuth2 token response missing access_token"
    );
  });

  it("sends FCM request with OAuth2 bearer token when token exchange succeeds", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ access_token: "oauth-token-1" }), { status: 200 })
      )
      .mockResolvedValueOnce(new Response(JSON.stringify({ name: "message-id-1" }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const { sendFcmMessage } = await import("../../src/services/fcmService");

    const result = await sendFcmMessage(
      "device-token-1",
      { title: "服薬記録", body: "服用しました" },
      { type: "DOSE_TAKEN" }
    );

    expect(result).toEqual({ success: true });
    expect(fetchMock).toHaveBeenCalledTimes(2);

    const [url, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(url).toBe("https://fcm.googleapis.com/v1/projects/firebase-project-1/messages:send");
    expect(init.headers).toMatchObject({
      Authorization: "Bearer oauth-token-1",
      "content-type": "application/json"
    });
  });
});
