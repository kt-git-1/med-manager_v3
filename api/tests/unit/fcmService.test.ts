import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { certMock, getAppsMock, initializeAppMock, getMessagingMock, sendMock } = vi.hoisted(() => ({
  certMock: vi.fn((serviceAccount) => ({ serviceAccount })),
  getAppsMock: vi.fn(),
  initializeAppMock: vi.fn((_options, name) => ({ name })),
  getMessagingMock: vi.fn(),
  sendMock: vi.fn()
}));

const logMock = vi.fn();

vi.mock("firebase-admin/app", () => ({
  cert: certMock,
  getApps: getAppsMock,
  initializeApp: initializeAppMock
}));

vi.mock("firebase-admin/messaging", () => ({
  getMessaging: getMessagingMock
}));

vi.mock("../../src/logging/logger", () => ({
  log: (...args: unknown[]) => logMock(...args)
}));

function serviceAccountEnv(): string {
  return Buffer.from(
    JSON.stringify({
      project_id: "firebase-project-1",
      client_email: "fcm-sender@firebase-project-1.iam.gserviceaccount.com",
      private_key: "-----BEGIN PRIVATE KEY-----\\ntest\\n-----END PRIVATE KEY-----\\n"
    })
  ).toString("base64");
}

describe("sendFcmMessage", () => {
  beforeEach(() => {
    vi.resetModules();
    certMock.mockClear();
    getAppsMock.mockReset();
    initializeAppMock.mockClear();
    getMessagingMock.mockReset();
    sendMock.mockReset();
    logMock.mockReset();
    process.env.FCM_SERVICE_ACCOUNT_JSON = serviceAccountEnv();

    getAppsMock.mockReturnValue([]);
    getMessagingMock.mockReturnValue({ send: sendMock });
    sendMock.mockResolvedValue("message-id-1");
  });

  afterEach(() => {
    delete process.env.FCM_SERVICE_ACCOUNT_JSON;
  });

  it("sends FCM request through Firebase Admin SDK", async () => {
    const { sendFcmMessage } = await import("../../src/services/fcmService");

    const result = await sendFcmMessage(
      "device-token-1",
      { title: "服薬記録", body: "服用しました" },
      { type: "DOSE_TAKEN" },
      { payload: { aps: { sound: "default", "thread-id": "patient-patient-1" } } }
    );

    expect(result).toEqual({ success: true });
    expect(certMock).toHaveBeenCalledWith({
      projectId: "firebase-project-1",
      clientEmail: "fcm-sender@firebase-project-1.iam.gserviceaccount.com",
      privateKey: "-----BEGIN PRIVATE KEY-----\\ntest\\n-----END PRIVATE KEY-----\\n"
    });
    expect(initializeAppMock).toHaveBeenCalledWith(
      {
        credential: {
          serviceAccount: {
            projectId: "firebase-project-1",
            clientEmail: "fcm-sender@firebase-project-1.iam.gserviceaccount.com",
            privateKey: "-----BEGIN PRIVATE KEY-----\\ntest\\n-----END PRIVATE KEY-----\\n"
          }
        },
        projectId: "firebase-project-1"
      },
      "med-manager-fcm"
    );
    expect(sendMock).toHaveBeenCalledWith({
      token: "device-token-1",
      notification: { title: "服薬記録", body: "服用しました" },
      data: { type: "DOSE_TAKEN" },
      apns: { payload: { aps: { sound: "default", "thread-id": "patient-patient-1" } } }
    });
  });

  it("reuses existing Firebase Admin app", async () => {
    getAppsMock.mockReturnValue([{ name: "med-manager-fcm" }]);
    const { sendFcmMessage } = await import("../../src/services/fcmService");

    const result = await sendFcmMessage(
      "device-token-1",
      { title: "服薬記録", body: "服用しました" },
      { type: "DOSE_TAKEN" }
    );

    expect(result).toEqual({ success: true });
    expect(initializeAppMock).not.toHaveBeenCalled();
    expect(getMessagingMock).toHaveBeenCalledWith({ name: "med-manager-fcm" });
  });

  it("maps unregistered token errors for device cleanup", async () => {
    sendMock.mockRejectedValue(
      Object.assign(new Error("registration-token-not-registered"), {
        code: "messaging/registration-token-not-registered"
      })
    );
    const { sendFcmMessage } = await import("../../src/services/fcmService");

    const result = await sendFcmMessage(
      "stale-device-token",
      { title: "服薬記録", body: "服用しました" },
      { type: "DOSE_TAKEN" }
    );

    expect(result).toEqual({ success: false, errorCode: "UNREGISTERED" });
    expect(logMock).toHaveBeenCalledWith("warn", "FCM: token unregistered");
  });

  it("logs non-secret diagnostics for other send errors", async () => {
    sendMock.mockRejectedValue(
      Object.assign(new Error("SenderId mismatch"), {
        code: "messaging/mismatched-credential"
      })
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
      "FCM send error projectId=firebase-project-1 code=messaging/mismatched-credential message=SenderId mismatch"
    );
  });
});
