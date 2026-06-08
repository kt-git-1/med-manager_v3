// ---------------------------------------------------------------------------
// FCM sender service
//
// Uses the official Firebase Admin SDK for FCM sends. The service account is
// still loaded from FCM_SERVICE_ACCOUNT_JSON as base64-encoded JSON.
// ---------------------------------------------------------------------------

import { cert, getApps, initializeApp, type App, type ServiceAccount } from "firebase-admin/app";
import { getMessaging, type Message } from "firebase-admin/messaging";
import { log } from "../logging/logger";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type FcmNotification = {
  title: string;
  body: string;
};

export type FcmDataPayload = Record<string, string>;

export type FcmApnsOverride = {
  payload?: {
    aps?: {
      sound?: string;
      "thread-id"?: string;
      [key: string]: unknown;
    };
  };
};

export type FcmSendResult = {
  success: boolean;
  errorCode?: "UNREGISTERED" | "UNKNOWN";
};

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

interface FcmConfig {
  clientEmail: string;
  privateKey: string;
  projectId: string;
}

const ADMIN_APP_NAME = "med-manager-fcm";

function getConfig(): FcmConfig | null {
  const raw = process.env.FCM_SERVICE_ACCOUNT_JSON;
  if (!raw) return null;

  try {
    const json = JSON.parse(Buffer.from(raw, "base64").toString("utf-8")) as {
      client_email?: unknown;
      private_key?: unknown;
      project_id?: unknown;
    };
    const clientEmail = json.client_email;
    const privateKey = json.private_key;
    const projectId = json.project_id;

    if (
      typeof clientEmail !== "string" ||
      typeof privateKey !== "string" ||
      typeof projectId !== "string"
    ) {
      return null;
    }

    return { clientEmail, privateKey, projectId };
  } catch {
    log("error", "FCM: Failed to parse FCM_SERVICE_ACCOUNT_JSON");
    return null;
  }
}

/**
 * Returns true if FCM is configured (FCM_SERVICE_ACCOUNT_JSON env var present).
 */
export function isFcmConfigured(): boolean {
  return !!process.env.FCM_SERVICE_ACCOUNT_JSON;
}

function getFirebaseApp(config: FcmConfig): App {
  const existing = getApps().find((app) => app.name === ADMIN_APP_NAME);
  if (existing) return existing;

  const serviceAccount: ServiceAccount = {
    projectId: config.projectId,
    clientEmail: config.clientEmail,
    privateKey: config.privateKey
  };

  return initializeApp(
    {
      credential: cert(serviceAccount),
      projectId: config.projectId
    },
    ADMIN_APP_NAME
  );
}

function isUnregisteredError(error: unknown): boolean {
  if (typeof error !== "object" || error === null) return false;
  const code = "code" in error ? String(error.code) : "";
  const message = error instanceof Error ? error.message : String(error);

  return (
    code === "messaging/registration-token-not-registered" ||
    code === "messaging/invalid-registration-token" ||
    message.includes("registration-token-not-registered") ||
    message.includes("UNREGISTERED")
  );
}

// ---------------------------------------------------------------------------
// Send FCM message
// ---------------------------------------------------------------------------

/**
 * Send a push notification via Firebase Admin SDK.
 *
 * Returns { success: true } on success, or { success: false, errorCode } on error.
 * errorCode "UNREGISTERED" means the token is stale and should be disabled.
 */
export async function sendFcmMessage(
  token: string,
  notification: FcmNotification,
  data: FcmDataPayload,
  apnsOverride?: FcmApnsOverride
): Promise<FcmSendResult> {
  const config = getConfig();
  if (!config) {
    log("warn", "FCM not configured – skipping push notification");
    return { success: false, errorCode: "UNKNOWN" };
  }

  try {
    const app = getFirebaseApp(config);
    const message: Message = {
      token,
      notification,
      data
    };

    if (apnsOverride) {
      message.apns = apnsOverride as NonNullable<Message["apns"]>;
    }

    await getMessaging(app).send(message);
    return { success: true };
  } catch (error) {
    if (isUnregisteredError(error)) {
      log("warn", "FCM: token unregistered");
      return { success: false, errorCode: "UNREGISTERED" };
    }

    const code =
      typeof error === "object" && error !== null && "code" in error
        ? String(error.code)
        : "unknown";
    log(
      "warn",
      [
        "FCM send error",
        `projectId=${config.projectId}`,
        `code=${code}`,
        `message=${error instanceof Error ? error.message : String(error)}`
      ].join(" ")
    );
    return { success: false, errorCode: "UNKNOWN" };
  }
}
