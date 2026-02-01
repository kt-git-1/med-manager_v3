import { describe, expect, it, afterEach } from "vitest";
import { createHmac } from "crypto";
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

afterEach(() => {
  if (ORIGINAL_SECRET === undefined) {
    delete process.env.SUPABASE_JWT_SECRET;
  } else {
    process.env.SUPABASE_JWT_SECRET = ORIGINAL_SECRET;
  }
});

describe("supabase jwt verification", () => {
  it("verifies valid token and returns caregiver user id", () => {
    process.env.SUPABASE_JWT_SECRET = "secret";
    const token = signToken(
      { sub: "caregiver-1", exp: Math.floor(Date.now() / 1000) + 60 },
      "secret"
    );
    const session = verifySupabaseJwt(token);
    expect(session.caregiverUserId).toBe("caregiver-1");
  });

  it("rejects expired token", () => {
    process.env.SUPABASE_JWT_SECRET = "secret";
    const token = signToken(
      { sub: "caregiver-1", exp: Math.floor(Date.now() / 1000) - 1 },
      "secret"
    );
    expect(() => verifySupabaseJwt(token)).toThrow("Token expired");
  });

  it("rejects invalid signature", () => {
    process.env.SUPABASE_JWT_SECRET = "secret";
    const token = signToken(
      { sub: "caregiver-1", exp: Math.floor(Date.now() / 1000) + 60 },
      "other"
    );
    expect(() => verifySupabaseJwt(token)).toThrow("Invalid token signature");
  });

  it("rejects missing subject", () => {
    process.env.SUPABASE_JWT_SECRET = "secret";
    const token = signToken(
      { exp: Math.floor(Date.now() / 1000) + 60 },
      "secret"
    );
    expect(() => verifySupabaseJwt(token)).toThrow("Missing subject");
  });
});
