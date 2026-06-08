import crypto from "node:crypto";
import { EventEmitter } from "node:events";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { requestMock } = vi.hoisted(() => ({
  requestMock: vi.fn()
}));

const logMock = vi.fn();

vi.mock("node:https", () => ({
  default: {
    request: requestMock
  }
}));

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

function mockHttpsResponse(statusCode: number, body: string, headers: Record<string, string> = {}) {
  requestMock.mockImplementationOnce(
    (
      _url: URL,
      options: { headers?: Record<string, string> },
      callback: (response: EventEmitter & { statusCode: number; headers: Record<string, string> }) => void
    ) => {
      const request = new EventEmitter() as EventEmitter & {
        write: ReturnType<typeof vi.fn>;
        end: ReturnType<typeof vi.fn>;
      };
      request.write = vi.fn();
      request.end = vi.fn(() => {
        const response = new EventEmitter() as EventEmitter & {
          statusCode: number;
          headers: Record<string, string>;
        };
        response.statusCode = statusCode;
        response.headers = headers;
        callback(response);
        response.emit("data", Buffer.from(body));
        response.emit("end");
      });
      return request;
    }
  );
}

describe("sendFcmMessage", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.unstubAllGlobals();
    requestMock.mockReset();
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
    expect(requestMock).not.toHaveBeenCalled();
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
      );
    vi.stubGlobal("fetch", fetchMock);
    mockHttpsResponse(200, JSON.stringify({ name: "message-id-1" }));

    const { sendFcmMessage } = await import("../../src/services/fcmService");

    const result = await sendFcmMessage(
      "device-token-1",
      { title: "服薬記録", body: "服用しました" },
      { type: "DOSE_TAKEN" }
    );

    expect(result).toEqual({ success: true });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(requestMock).toHaveBeenCalledTimes(1);

    const [url, init] = requestMock.mock.calls[0] as [
      URL,
      { method: string; headers: Record<string, string> }
    ];
    expect(url.toString()).toBe("https://fcm.googleapis.com/v1/projects/firebase-project-1/messages:send");
    expect(init.method).toBe("POST");
    expect(init.headers).toMatchObject({
      Authorization: "Bearer oauth-token-1",
      "content-type": "application/json"
    });
    expect(init.headers["content-length"]).toBeDefined();
  });

  it("logs non-secret auth diagnostics when FCM rejects the send request", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ access_token: "oauth-token-1" }), { status: 200 })
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            scope: "https://www.googleapis.com/auth/firebase.messaging",
            expires_in: 3599
          }),
          { status: 200 }
        )
      );
    vi.stubGlobal("fetch", fetchMock);
    mockHttpsResponse(
      401,
      JSON.stringify({
        error: {
          code: 401,
          message: "Request is missing required authentication credential."
        }
      }),
      { "www-authenticate": "Bearer realm=\"https://accounts.google.com/\"" }
    );

    const { sendFcmMessage } = await import("../../src/services/fcmService");

    const result = await sendFcmMessage(
      "device-token-1",
      { title: "服薬記録", body: "服用しました" },
      { type: "DOSE_TAKEN" }
    );

    expect(result).toEqual({ success: false, errorCode: "UNKNOWN" });
    expect(logMock).toHaveBeenCalledWith(
      "warn",
      expect.stringContaining("FCM send error: status=401")
    );
    expect(logMock).toHaveBeenCalledWith(
      "warn",
      expect.stringContaining("authHeader=true")
    );
    expect(logMock).toHaveBeenCalledWith(
      "warn",
      expect.stringContaining("accessTokenLength=13")
    );
    expect(logMock).toHaveBeenCalledWith(
      "warn",
      expect.stringContaining("tokenInfoHasFcmScope=true")
    );
    expect(logMock).not.toHaveBeenCalledWith(
      "warn",
      expect.stringContaining("oauth-token-1")
    );
  });
});
