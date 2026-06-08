// ---------------------------------------------------------------------------
// FCM HTTP v1 sender service
//
// Uses Google service account JWT (RS256) to obtain OAuth2 access tokens,
// then sends push notifications via the FCM HTTP v1 REST API.
// No firebase-admin SDK dependency — lightweight for Vercel.
// ---------------------------------------------------------------------------

import crypto from "node:crypto";
import https from "node:https";
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

function getConfig(): FcmConfig | null {
  const raw = process.env.FCM_SERVICE_ACCOUNT_JSON;
  if (!raw) return null;

  try {
    const json = JSON.parse(Buffer.from(raw, "base64").toString("utf-8"));
    const clientEmail = json.client_email;
    const privateKey = json.private_key;
    const projectId = json.project_id;

    if (!clientEmail || !privateKey || !projectId) return null;
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

// ---------------------------------------------------------------------------
// OAuth2 access token generation via Google service account JWT
// ---------------------------------------------------------------------------

let cachedAccessToken: { token: string; issuedAt: number } | null = null;

/** Access tokens are valid for 1 hour. We refresh every 50 minutes. */
const TOKEN_TTL_SECONDS = 50 * 60;

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const TOKEN_URL = "https://oauth2.googleapis.com/token";

function tokenDebugInfo(accessToken: string) {
  return {
    accessTokenLength: accessToken.length,
    accessTokenSha256: crypto.createHash("sha256").update(accessToken).digest("hex").slice(0, 12)
  };
}

function httpsPostJson(
  url: string,
  headers: Record<string, string>,
  body: unknown
): Promise<{ status: number; headers: Record<string, string | string[] | undefined>; body: string }> {
  return new Promise((resolve, reject) => {
    const payload = JSON.stringify(body);
    const request = https.request(
      new URL(url),
      {
        method: "POST",
        headers: {
          ...headers,
          "content-length": Buffer.byteLength(payload).toString()
        }
      },
      (response) => {
        const chunks: Buffer[] = [];
        response.on("data", (chunk) => {
          chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
        });
        response.on("end", () => {
          resolve({
            status: response.statusCode ?? 0,
            headers: response.headers,
            body: Buffer.concat(chunks).toString("utf-8")
          });
        });
      }
    );

    request.on("error", reject);
    request.write(payload);
    request.end();
  });
}

async function getTokenInfoDebug(accessToken: string): Promise<string> {
  try {
    const params = new URLSearchParams({ access_token: accessToken });
    const res = await fetch(`https://oauth2.googleapis.com/tokeninfo?${params.toString()}`);
    const body = (await res.json().catch(() => null)) as {
      scope?: unknown;
      expires_in?: unknown;
      error?: unknown;
      error_description?: unknown;
    } | null;
    const scope = typeof body?.scope === "string" ? body.scope : "";
    const hasFcmScope = scope.split(/\s+/).includes(FCM_SCOPE);
    const expiresIn = typeof body?.expires_in === "number" || typeof body?.expires_in === "string"
      ? body.expires_in
      : "unknown";
    const error = typeof body?.error === "string" ? body.error : undefined;

    return [
      `tokenInfoStatus=${res.status}`,
      `tokenInfoHasFcmScope=${hasFcmScope}`,
      `tokenInfoExpiresIn=${expiresIn}`,
      error ? `tokenInfoError=${error}` : null
    ].filter(Boolean).join(" ");
  } catch (error) {
    return `tokenInfoError=${error instanceof Error ? error.message : String(error)}`;
  }
}

async function getAccessToken(config: FcmConfig): Promise<string> {
  const now = Math.floor(Date.now() / 1000);

  if (cachedAccessToken && now - cachedAccessToken.issuedAt < TOKEN_TTL_SECONDS) {
    return cachedAccessToken.token;
  }

  // Build JWT assertion
  const header = Buffer.from(
    JSON.stringify({ alg: "RS256", typ: "JWT" })
  ).toString("base64url");

  const payload = Buffer.from(
    JSON.stringify({
      iss: config.clientEmail,
      scope: FCM_SCOPE,
      aud: TOKEN_URL,
      iat: now,
      exp: now + 3600 // 1 hour
    })
  ).toString("base64url");

  const sign = crypto.createSign("RSA-SHA256");
  sign.update(`${header}.${payload}`);
  const signature = sign.sign(config.privateKey, "base64url");

  const assertion = `${header}.${payload}.${signature}`;

  // Exchange JWT for access token
  const res = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion
    })
  });

  if (!res.ok) {
    const text = await res.text();
    log("error", `FCM OAuth2 token error: ${res.status} ${text}`);
    throw new Error(`FCM OAuth2 token error: ${res.status}`);
  }

  const data = (await res.json()) as { access_token?: unknown };
  if (typeof data.access_token !== "string" || data.access_token.length === 0) {
    log("error", "FCM OAuth2 token response missing access_token");
    throw new Error("FCM OAuth2 token response missing access_token");
  }

  cachedAccessToken = { token: data.access_token, issuedAt: now };
  return data.access_token;
}

// ---------------------------------------------------------------------------
// Send FCM message
// ---------------------------------------------------------------------------

/**
 * Send a push notification via FCM HTTP v1 API.
 *
 * Returns { success: true } on 200, or { success: false, errorCode } on error.
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
    const accessToken = await getAccessToken(config);
    const url = `https://fcm.googleapis.com/v1/projects/${config.projectId}/messages:send`;

    const message: Record<string, unknown> = {
      token,
      notification,
      data
    };

    if (apnsOverride) {
      message.apns = apnsOverride;
    }

    const authorizationHeader = `Bearer ${accessToken}`;
    const res = await httpsPostJson(
      url,
      {
        Authorization: authorizationHeader,
        "content-type": "application/json"
      },
      { message }
    );

    if (res.status >= 200 && res.status < 300) {
      return { success: true };
    }

    // Parse error response
    const errorBody = res.body;
    const isUnregistered =
      res.status === 404 ||
      errorBody.includes("UNREGISTERED") ||
      errorBody.includes("NOT_FOUND") ||
      errorBody.includes("registration-token-not-registered");

    if (isUnregistered) {
      log("warn", `FCM: token unregistered (status=${res.status})`);
      return { success: false, errorCode: "UNREGISTERED" };
    }

    const authDebug = tokenDebugInfo(accessToken);
    const wwwAuthenticate = res.headers["www-authenticate"];
    const redirectLocation = res.headers.location;
    const tokenInfoDebug = res.status === 401 ? await getTokenInfoDebug(accessToken) : null;
    const wwwAuthenticateText = Array.isArray(wwwAuthenticate)
      ? wwwAuthenticate.join(", ")
      : wwwAuthenticate;
    const redirectLocationText = Array.isArray(redirectLocation)
      ? redirectLocation.join(", ")
      : redirectLocation;
    log(
      "warn",
      [
        `FCM send error: status=${res.status}`,
        `projectId=${config.projectId}`,
        `authHeader=${authorizationHeader.length > "Bearer ".length}`,
        `accessTokenLength=${authDebug.accessTokenLength}`,
        `accessTokenSha256=${authDebug.accessTokenSha256}`,
        tokenInfoDebug,
        redirectLocationText ? `redirectLocation=${redirectLocationText.slice(0, 200)}` : null,
        wwwAuthenticateText ? `wwwAuthenticate=${wwwAuthenticateText.slice(0, 200)}` : null,
        `body=${errorBody.slice(0, 200)}`
      ].filter(Boolean).join(" ")
    );
    return { success: false, errorCode: "UNKNOWN" };
  } catch (error) {
    log(
      "error",
      `FCM send exception: ${error instanceof Error ? error.message : String(error)}`
    );
    return { success: false, errorCode: "UNKNOWN" };
  }
}
