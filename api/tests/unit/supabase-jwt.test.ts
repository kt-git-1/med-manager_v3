import { describe, expect, it, afterEach } from "vitest";
import { createHmac, createSign, generateKeyPairSync } from "crypto";
import { verifySupabaseJwt } from "../../src/auth/supabaseJwt";

function base64UrlEncode(input: Buffer) {
  return input.toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function signToken(payload: Record<string, unknown>, secret: string) {
  const header = { alg: "HS256", typ: "JWT" };
  const encodedHeader = base64UrlEncode(Buffer.from(JSON.stringify(header)));
  const encodedPayload = base64UrlEncode(Buffer.from(JSON.stringify(payload)));
  const data = `${encodedHeader}.${encodedPayload}`;
  const signature = createHmac("sha256", secret).update(data).digest();
  const encodedSignature = base64UrlEncode(signature);
  return `${data}.${encodedSignature}`;
}

const ORIGINAL_SECRET = process.env.SUPABASE_JWT_SECRET;
const ORIGINAL_PUBLIC_KEY = process.env.SUPABASE_JWT_PUBLIC_KEY;

afterEach(() => {
  if (ORIGINAL_SECRET === undefined) {
    delete process.env.SUPABASE_JWT_SECRET;
  } else {
    process.env.SUPABASE_JWT_SECRET = ORIGINAL_SECRET;
  }
  if (ORIGINAL_PUBLIC_KEY === undefined) {
    delete process.env.SUPABASE_JWT_PUBLIC_KEY;
  } else {
    process.env.SUPABASE_JWT_PUBLIC_KEY = ORIGINAL_PUBLIC_KEY;
  }
});

describe("supabase jwt verification", () => {
  it("verifies valid token and returns caregiver user id", async () => {
    process.env.SUPABASE_JWT_SECRET = "secret";
    const token = signToken(
      { sub: "caregiver-1", exp: Math.floor(Date.now() / 1000) + 60 },
      "secret"
    );
    const session = await verifySupabaseJwt(token);
    expect(session.caregiverUserId).toBe("caregiver-1");
  });

  it("rejects expired token", async () => {
    process.env.SUPABASE_JWT_SECRET = "secret";
    const token = signToken(
      { sub: "caregiver-1", exp: Math.floor(Date.now() / 1000) - 1 },
      "secret"
    );
    await expect(verifySupabaseJwt(token)).rejects.toThrow("Token expired");
  });

  it("rejects invalid signature", async () => {
    process.env.SUPABASE_JWT_SECRET = "secret";
    const token = signToken(
      { sub: "caregiver-1", exp: Math.floor(Date.now() / 1000) + 60 },
      "other"
    );
    await expect(verifySupabaseJwt(token)).rejects.toThrow("Invalid token signature");
  });

  it("rejects missing subject", async () => {
    process.env.SUPABASE_JWT_SECRET = "secret";
    const token = signToken(
      { exp: Math.floor(Date.now() / 1000) + 60 },
      "secret"
    );
    await expect(verifySupabaseJwt(token)).rejects.toThrow("Missing subject");
  });

  it("verifies ES256 token using public key", async () => {
    const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });
    process.env.SUPABASE_JWT_PUBLIC_KEY = publicKey.export({ type: "spki", format: "pem" }).toString();
    const header = { alg: "ES256", typ: "JWT" };
    const payload = { sub: "caregiver-1", exp: Math.floor(Date.now() / 1000) + 60 };
    const encodedHeader = base64UrlEncode(Buffer.from(JSON.stringify(header)));
    const encodedPayload = base64UrlEncode(Buffer.from(JSON.stringify(payload)));
    const data = `${encodedHeader}.${encodedPayload}`;
    const derSignature = createSign("sha256").update(data).end().sign(privateKey);
    const rawSignature = derSignatureToRaw(derSignature);
    const token = `${data}.${base64UrlEncode(rawSignature)}`;
    const session = await verifySupabaseJwt(token);
    expect(session.caregiverUserId).toBe("caregiver-1");
  });
});

function derSignatureToRaw(signature: Buffer) {
  let offset = 0;
  if (signature[offset++] !== 0x30) {
    throw new Error("Invalid DER signature");
  }
  const length = signature[offset++];
  const end = offset + length;
  if (signature[offset++] !== 0x02) {
    throw new Error("Invalid DER signature");
  }
  const rLength = signature[offset++];
  let r = signature.subarray(offset, offset + rLength);
  offset += rLength;
  if (signature[offset++] !== 0x02) {
    throw new Error("Invalid DER signature");
  }
  const sLength = signature[offset++];
  let s = signature.subarray(offset, offset + sLength);
  offset += sLength;
  if (offset !== end) {
    throw new Error("Invalid DER signature");
  }
  r = trimDerInteger(r, 32);
  s = trimDerInteger(s, 32);
  return Buffer.concat([r, s]);
}

function trimDerInteger(value: Buffer, size: number) {
  let normalized = value;
  while (normalized.length > 0 && normalized[0] === 0) {
    normalized = normalized.subarray(1);
  }
  if (normalized.length > size) {
    throw new Error("Invalid DER signature");
  }
  if (normalized.length === size) {
    return normalized;
  }
  const padded = Buffer.alloc(size);
  normalized.copy(padded, size - normalized.length);
  return padded;
}
