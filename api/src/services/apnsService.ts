// ---------------------------------------------------------------------------
// APNs (Apple Push Notification service) client
//
// Uses the APNs provider API over HTTP/2 with token-based authentication.
// No external dependencies – relies on Node.js built-in `http2` and `crypto`.
// ---------------------------------------------------------------------------

import http2 from "node:http2";
import crypto from "node:crypto";
import { log } from "../logging/logger";

// ---------------------------------------------------------------------------
// Configuration (from environment variables)
// ---------------------------------------------------------------------------

interface ApnsConfig {
  /** The .p8 private key content (PEM or raw base64). */
  key: string;
  /** Key ID from Apple Developer portal. */
  keyId: string;
  /** Apple Developer Team ID. */
  teamId: string;
  /** App bundle identifier (e.g. com.example.medicationapp). */
  bundleId: string;
  /** "production" | "development" */
  environment: "production" | "development";
}

function getConfig(): ApnsConfig | null {
  const key = process.env.APNS_KEY;
  const keyId = process.env.APNS_KEY_ID;
  const teamId = process.env.APNS_TEAM_ID;
  const bundleId = process.env.APNS_BUNDLE_ID;
  const environment = process.env.APNS_ENVIRONMENT === "production" ? "production" : "development";

  if (!key || !keyId || !teamId || !bundleId) {
    return null;
  }

  return { key, keyId, teamId, bundleId, environment };
}

// ---------------------------------------------------------------------------
// JWT token generation for APNs
// ---------------------------------------------------------------------------

let cachedToken: { jwt: string; issuedAt: number } | null = null;

/** APNs tokens are valid for up to 1 hour. We refresh every 50 minutes. */
const TOKEN_TTL_SECONDS = 50 * 60;

function generateApnsJwt(config: ApnsConfig): string {
  const now = Math.floor(Date.now() / 1000);

  if (cachedToken && now - cachedToken.issuedAt < TOKEN_TTL_SECONDS) {
    return cachedToken.jwt;
  }

  const header = Buffer.from(
    JSON.stringify({ alg: "ES256", kid: config.keyId })
  ).toString("base64url");

  const payload = Buffer.from(
    JSON.stringify({ iss: config.teamId, iat: now })
  ).toString("base64url");

  // Decode the .p8 key – it may come as raw base64 (no PEM headers) or full PEM.
  let pemKey = config.key;
  if (!pemKey.includes("-----BEGIN")) {
    pemKey = `-----BEGIN PRIVATE KEY-----\n${pemKey}\n-----END PRIVATE KEY-----`;
  }

  const sign = crypto.createSign("SHA256");
  sign.update(`${header}.${payload}`);
  // APNs uses ES256 (P-256 / prime256v1). Node returns DER by default;
  // we need the raw IEEE P1363 format (r||s, 64 bytes).
  const derSig = sign.sign({ key: pemKey, dsaEncoding: "ieee-p1363" });
  const signature = derSig.toString("base64url");

  const jwt = `${header}.${payload}.${signature}`;
  cachedToken = { jwt, issuedAt: now };
  return jwt;
}

// ---------------------------------------------------------------------------
// Send push notification
// ---------------------------------------------------------------------------

export interface ApnsPayload {
  aps: {
    alert: {
      title?: string;
      subtitle?: string;
      body: string;
    };
    sound?: string;
    badge?: number;
    "mutable-content"?: number;
    "thread-id"?: string;
  };
  [key: string]: unknown;
}

export interface ApnsSendResult {
  token: string;
  success: boolean;
  statusCode?: number;
  reason?: string;
}

function apnsHost(environment: "production" | "development"): string {
  return environment === "production"
    ? "api.push.apple.com"
    : "api.sandbox.push.apple.com";
}

/**
 * Send a push notification to a single APNs device token.
 */
export function sendPushNotification(
  deviceToken: string,
  payload: ApnsPayload,
  options?: { collapseId?: string; expiration?: number }
): Promise<ApnsSendResult> {
  const config = getConfig();
  if (!config) {
    log("warn", "APNs not configured – skipping push notification");
    return Promise.resolve({ token: deviceToken, success: false, reason: "not_configured" });
  }

  const jwt = generateApnsJwt(config);
  const host = apnsHost(config.environment);
  const path = `/3/device/${deviceToken}`;
  const body = JSON.stringify(payload);

  return new Promise((resolve) => {
    const client = http2.connect(`https://${host}`);

    client.on("error", (err) => {
      log("error", `APNs connection error: ${err.message}`);
      client.close();
      resolve({ token: deviceToken, success: false, reason: "connection_error" });
    });

    const headers: http2.OutgoingHttpHeaders = {
      ":method": "POST",
      ":path": path,
      authorization: `bearer ${jwt}`,
      "apns-topic": config.bundleId,
      "apns-push-type": "alert",
      "apns-priority": "10",
    };

    if (options?.collapseId) {
      headers["apns-collapse-id"] = options.collapseId;
    }
    if (options?.expiration !== undefined) {
      headers["apns-expiration"] = String(options.expiration);
    }

    const req = client.request(headers);

    let responseData = "";
    let statusCode = 0;

    req.on("response", (resHeaders) => {
      statusCode = resHeaders[":status"] as number;
    });

    req.on("data", (chunk: Buffer) => {
      responseData += chunk.toString();
    });

    req.on("end", () => {
      client.close();
      if (statusCode === 200) {
        resolve({ token: deviceToken, success: true, statusCode });
      } else {
        let reason = "unknown";
        try {
          const parsed = JSON.parse(responseData);
          reason = parsed.reason ?? "unknown";
        } catch {
          // ignore parse error
        }
        log("warn", `APNs send failed: status=${statusCode} reason=${reason} token=${deviceToken.slice(0, 8)}...`);
        resolve({ token: deviceToken, success: false, statusCode, reason });
      }
    });

    req.on("error", (err) => {
      client.close();
      log("error", `APNs request error: ${err.message}`);
      resolve({ token: deviceToken, success: false, reason: "request_error" });
    });

    req.end(body);
  });
}

/**
 * Send a push notification to multiple device tokens.
 * Returns results for each token (fire-and-forget friendly).
 */
export async function sendPushNotifications(
  deviceTokens: string[],
  payload: ApnsPayload,
  options?: { collapseId?: string; expiration?: number }
): Promise<ApnsSendResult[]> {
  if (deviceTokens.length === 0) return [];

  const config = getConfig();
  if (!config) {
    log("warn", "APNs not configured – skipping push notifications");
    return deviceTokens.map((token) => ({ token, success: false, reason: "not_configured" }));
  }

  return Promise.all(
    deviceTokens.map((token) => sendPushNotification(token, payload, options))
  );
}

/**
 * Returns true if APNs is configured (environment variables are set).
 */
export function isApnsConfigured(): boolean {
  return getConfig() !== null;
}
