import { createHmac, createPublicKey, timingSafeEqual, verify } from "crypto";

export type CaregiverSession = {
  caregiverUserId: string;
};

type JwtHeader = {
  alg?: string;
  typ?: string;
  kid?: string;
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

function verifyHmacSignature(token: string, secret: string) {
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

type JwkKey = {
  kty: string;
  crv?: string;
  x?: string;
  y?: string;
  kid?: string;
  alg?: string;
  use?: string;
};

let cachedJwks: { fetchedAt: number; keys: Map<string, JwkKey> } | null = null;
const JWKS_CACHE_TTL_MS = 5 * 60 * 1000;

function getJwksUrl() {
  const explicit = process.env.SUPABASE_JWT_JWKS_URL;
  if (explicit) {
    return explicit;
  }
  const supabaseUrl = process.env.SUPABASE_URL;
  if (!supabaseUrl) {
    throw new Error("Missing SUPABASE_URL");
  }
  return `${supabaseUrl.replace(/\/$/, "")}/auth/v1/keys`;
}

async function fetchJwks(): Promise<Map<string, JwkKey>> {
  if (cachedJwks && Date.now() - cachedJwks.fetchedAt < JWKS_CACHE_TTL_MS) {
    return cachedJwks.keys;
  }
  const response = await fetch(getJwksUrl());
  if (!response.ok) {
    throw new Error("Failed to fetch JWKS");
  }
  const data = (await response.json()) as { keys?: JwkKey[] };
  const keys = new Map<string, JwkKey>();
  for (const key of data.keys ?? []) {
    if (key.kid) {
      keys.set(key.kid, key);
    }
  }
  cachedJwks = { fetchedAt: Date.now(), keys };
  return keys;
}

function normalizeDerInteger(input: Buffer) {
  let offset = 0;
  while (offset < input.length - 1 && input[offset] === 0) {
    offset += 1;
  }
  let normalized = input.slice(offset);
  if (normalized[0] & 0x80) {
    normalized = Buffer.concat([Buffer.from([0]), normalized]);
  }
  return normalized;
}

function rawSignatureToDer(signature: Buffer) {
  if (signature.length !== 64) {
    throw new Error("Invalid ES256 signature");
  }
  const r = normalizeDerInteger(signature.subarray(0, 32));
  const s = normalizeDerInteger(signature.subarray(32));
  const rPart = Buffer.concat([Buffer.from([0x02, r.length]), r]);
  const sPart = Buffer.concat([Buffer.from([0x02, s.length]), s]);
  const totalLength = rPart.length + sPart.length;
  return Buffer.concat([Buffer.from([0x30, totalLength]), rPart, sPart]);
}

async function resolveEs256Key(header: JwtHeader) {
  const publicKey = process.env.SUPABASE_JWT_PUBLIC_KEY;
  if (publicKey) {
    return createPublicKey(publicKey);
  }
  if (!header.kid) {
    throw new Error("Missing key id");
  }
  const jwks = await fetchJwks();
  const jwk = jwks.get(header.kid);
  if (!jwk) {
    throw new Error("Unknown key id");
  }
  return createPublicKey({ key: jwk as JsonWebKey, format: "jwk" });
}

export async function verifySupabaseJwt(token: string): Promise<CaregiverSession> {
  if (!token) {
    throw new Error("Missing token");
  }
  const parts = token.split(".");
  if (parts.length !== 3) {
    throw new Error("Invalid token format");
  }
  const header = parseJson<JwtHeader>(parts[0]);
  if (header.alg === "HS256") {
    const secret = process.env.SUPABASE_JWT_SECRET;
    if (!secret) {
      throw new Error("Missing SUPABASE_JWT_SECRET");
    }
    verifyHmacSignature(token, secret);
  } else if (header.alg === "ES256") {
    const key = await resolveEs256Key(header);
    const signature = rawSignatureToDer(base64UrlDecode(parts[2]));
    const verified = verify("sha256", Buffer.from(`${parts[0]}.${parts[1]}`), key, signature);
    if (!verified) {
      throw new Error("Invalid token signature");
    }
  } else {
    throw new Error("Unsupported token algorithm");
  }
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
