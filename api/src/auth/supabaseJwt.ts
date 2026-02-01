import { createHmac, timingSafeEqual } from "crypto";

export type CaregiverSession = {
  caregiverUserId: string;
};

type JwtHeader = {
  alg?: string;
  typ?: string;
};

type JwtPayload = {
  sub?: string;
  exp?: number;
};

function base64UrlDecode(input: string) {
  const normalized = input.replace(/-/g, "+").replace(/_/g, "/");
  const padding = normalized.length % 4 === 0 ? "" : "=".repeat(4 - (normalized.length % 4));
  return Buffer.from(`${normalized}${padding}`, "base64");
}

function base64UrlEncode(input: Buffer) {
  return input.toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function verifySignature(token: string, secret: string) {
  const [header, payload, signature] = token.split(".");
  const data = `${header}.${payload}`;
  const expected = createHmac("sha256", secret).update(data).digest();
  const provided = base64UrlDecode(signature);
  if (provided.length !== expected.length || !timingSafeEqual(provided, expected)) {
    throw new Error("Invalid token signature");
  }
}

function parseJson<T>(input: string) {
  const decoded = base64UrlDecode(input).toString("utf8");
  return JSON.parse(decoded) as T;
}

export function verifySupabaseJwt(token: string): CaregiverSession {
  if (!token) {
    throw new Error("Missing token");
  }
  const secret = process.env.SUPABASE_JWT_SECRET;
  if (!secret) {
    throw new Error("Missing SUPABASE_JWT_SECRET");
  }
  const parts = token.split(".");
  if (parts.length !== 3) {
    throw new Error("Invalid token format");
  }
  const header = parseJson<JwtHeader>(parts[0]);
  if (header.alg !== "HS256") {
    throw new Error("Unsupported token algorithm");
  }
  verifySignature(token, secret);
  const payload = parseJson<JwtPayload>(parts[1]);
  if (typeof payload.exp === "number") {
    const now = Math.floor(Date.now() / 1000);
    if (payload.exp <= now) {
      throw new Error("Token expired");
    }
  }
  if (!payload.sub) {
    throw new Error("Missing subject");
  }
  return { caregiverUserId: payload.sub };
}
