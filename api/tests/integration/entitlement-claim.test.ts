import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { createSign, generateKeyPairSync, type KeyObject } from "crypto";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("../../src/auth/supabaseJwt", () => ({
  verifySupabaseJwt: vi.fn(async () => ({ caregiverUserId: "caregiver-1" }))
}));

vi.mock("../../src/auth/patientSessionVerifier", () => ({
  patientSessionVerifier: {
    verify: vi.fn(async () => ({ patientId: "patient-1" }))
  }
}));

// Entitlement repo mock
const upsertEntitlementMock = vi.fn();
const findEntitlementsByCaregiverIdMock = vi.fn();

vi.mock("../../src/repositories/entitlementRepo", () => ({
  upsertEntitlement: (...args: unknown[]) => upsertEntitlementMock(...args),
  findEntitlementsByCaregiverId: (...args: unknown[]) => findEntitlementsByCaregiverIdMock(...args)
}));

// ---------------------------------------------------------------------------
// Imports (after mocks)
// ---------------------------------------------------------------------------

import { CAREGIVER_TOKEN_PREFIX } from "../../src/constants";

// Route handlers will be imported lazily inside each describe so mock
// registrations take effect before module evaluation.

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function caregiverHeaders(): HeadersInit {
  return {
    authorization: `Bearer ${CAREGIVER_TOKEN_PREFIX}valid-jwt`,
    "content-type": "application/json"
  };
}

function patientHeaders(): HeadersInit {
  return {
    authorization: "Bearer patient-session-token",
    "content-type": "application/json"
  };
}

const VALID_PRODUCT_ID = "com.yourcompany.medicationapp.premium_unlock";
const ORIGINAL_STOREKIT_PUBLIC_KEY = process.env.STOREKIT_JWS_PUBLIC_KEY_PEM;
const storeKitKeys = generateKeyPairSync("ec", { namedCurve: "P-256" });

function validClaimBody() {
  const fakeJws = signStoreKitJws(
    {
      originalTransactionId: "orig-txn-001",
      transactionId: "txn-001",
      purchaseDate: Date.now(),
      productId: VALID_PRODUCT_ID,
      environment: "Sandbox"
    },
    storeKitKeys.privateKey
  );

  return {
    productId: VALID_PRODUCT_ID,
    signedTransactionInfo: fakeJws,
    environment: "Sandbox"
  };
}

// ---------------------------------------------------------------------------
// POST /api/iap/claim
// ---------------------------------------------------------------------------

describe("POST /api/iap/claim", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    process.env.STOREKIT_JWS_PUBLIC_KEY_PEM = storeKitKeys.publicKey
      .export({ type: "spki", format: "pem" })
      .toString();
    upsertEntitlementMock.mockResolvedValue({
      id: "ent-1",
      caregiverId: "caregiver-1",
      productId: VALID_PRODUCT_ID,
      status: "ACTIVE",
      originalTransactionId: "orig-txn-001",
      transactionId: "txn-001",
      purchasedAt: new Date(),
      environment: "Sandbox",
      createdAt: new Date(),
      updatedAt: new Date()
    });
  });

  it("creates ACTIVE entitlement for valid caregiver claim", async () => {
    const { POST } = await import("../../app/api/iap/claim/route");
    const req = new Request("http://localhost/api/iap/claim", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify(validClaimBody())
    });

    const res = await POST(req);
    expect(res.status).toBe(200);

    const body = await res.json();
    expect(body.data.premium).toBe(true);
    expect(body.data.productId).toBe(VALID_PRODUCT_ID);
    expect(body.data.status).toBe("ACTIVE");
    expect(body.data.updatedAt).toBeDefined();
    expect(upsertEntitlementMock).toHaveBeenCalledTimes(1);
  });

  it("upserts idempotently for duplicate originalTransactionId", async () => {
    const { POST } = await import("../../app/api/iap/claim/route");

    // First claim
    const req1 = new Request("http://localhost/api/iap/claim", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify(validClaimBody())
    });
    await POST(req1);

    // Second claim (same originalTransactionId)
    const req2 = new Request("http://localhost/api/iap/claim", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify(validClaimBody())
    });
    const res2 = await POST(req2);

    expect(res2.status).toBe(200);
    // upsert called twice but never creates a second record (upsert behaviour)
    expect(upsertEntitlementMock).toHaveBeenCalledTimes(2);
  });

  it("returns 422 for invalid productId", async () => {
    const { POST } = await import("../../app/api/iap/claim/route");
    const req = new Request("http://localhost/api/iap/claim", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify({
        ...validClaimBody(),
        productId: "com.wrong.product"
      })
    });

    const res = await POST(req);
    expect(res.status).toBe(422);
  });

  it("returns 422 when signedTransactionInfo is missing", async () => {
    const { POST } = await import("../../app/api/iap/claim/route");
    const req = new Request("http://localhost/api/iap/claim", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify({ productId: VALID_PRODUCT_ID })
    });

    const res = await POST(req);
    expect(res.status).toBe(422);
  });

  it("returns 422 for forged signedTransactionInfo", async () => {
    const { POST } = await import("../../app/api/iap/claim/route");
    const req = new Request("http://localhost/api/iap/claim", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify({
        productId: VALID_PRODUCT_ID,
        signedTransactionInfo: "header.payload.signature",
        environment: "Sandbox"
      })
    });

    const res = await POST(req);
    expect(res.status).toBe(422);
    expect(upsertEntitlementMock).not.toHaveBeenCalled();
  });

  it("returns 422 when request product does not match signed transaction product", async () => {
    const { POST } = await import("../../app/api/iap/claim/route");
    const signedTransactionInfo = signStoreKitJws(
      {
        originalTransactionId: "orig-txn-002",
        transactionId: "txn-002",
        purchaseDate: Date.now(),
        productId: "com.other.product"
      },
      storeKitKeys.privateKey
    );
    const req = new Request("http://localhost/api/iap/claim", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify({
        productId: VALID_PRODUCT_ID,
        signedTransactionInfo,
        environment: "Sandbox"
      })
    });

    const res = await POST(req);
    expect(res.status).toBe(422);
    expect(upsertEntitlementMock).not.toHaveBeenCalled();
  });

  it("returns 401 when authorization header is missing", async () => {
    const { POST } = await import("../../app/api/iap/claim/route");
    const req = new Request("http://localhost/api/iap/claim", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(validClaimBody())
    });

    const res = await POST(req);
    expect(res.status).toBe(401);
  });

  it("returns 401 when patient token is used", async () => {
    const { POST } = await import("../../app/api/iap/claim/route");
    const req = new Request("http://localhost/api/iap/claim", {
      method: "POST",
      headers: patientHeaders(),
      body: JSON.stringify(validClaimBody())
    });

    const res = await POST(req);
    expect(res.status).toBe(401);
  });
});

afterEach(() => {
  if (ORIGINAL_STOREKIT_PUBLIC_KEY === undefined) {
    delete process.env.STOREKIT_JWS_PUBLIC_KEY_PEM;
  } else {
    process.env.STOREKIT_JWS_PUBLIC_KEY_PEM = ORIGINAL_STOREKIT_PUBLIC_KEY;
  }
});

function base64UrlEncode(input: Buffer) {
  return input.toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function signStoreKitJws(payload: Record<string, unknown>, privateKey: KeyObject) {
  const header = { alg: "ES256", typ: "JWT" };
  const encodedHeader = base64UrlEncode(Buffer.from(JSON.stringify(header)));
  const encodedPayload = base64UrlEncode(Buffer.from(JSON.stringify(payload)));
  const data = `${encodedHeader}.${encodedPayload}`;
  const derSignature = createSign("sha256").update(data).end().sign(privateKey);
  return `${data}.${base64UrlEncode(derSignatureToRaw(derSignature))}`;
}

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

// ---------------------------------------------------------------------------
// GET /api/me/entitlements
// ---------------------------------------------------------------------------

describe("GET /api/me/entitlements", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns premium: true when caregiver has ACTIVE entitlement", async () => {
    findEntitlementsByCaregiverIdMock.mockResolvedValue([
      {
        id: "ent-1",
        caregiverId: "caregiver-1",
        productId: VALID_PRODUCT_ID,
        status: "ACTIVE",
        originalTransactionId: "orig-txn-001",
        transactionId: "txn-001",
        purchasedAt: new Date("2026-02-10T00:00:00Z"),
        environment: "Sandbox",
        createdAt: new Date(),
        updatedAt: new Date()
      }
    ]);

    const { GET } = await import("../../app/api/me/entitlements/route");
    const req = new Request("http://localhost/api/me/entitlements", {
      method: "GET",
      headers: caregiverHeaders()
    });

    const res = await GET(req);
    expect(res.status).toBe(200);

    const body = await res.json();
    expect(body.data.premium).toBe(true);
    expect(body.data.entitlements).toHaveLength(1);
    expect(body.data.entitlements[0].productId).toBe(VALID_PRODUCT_ID);
    expect(body.data.entitlements[0].status).toBe("ACTIVE");
    expect(body.data.entitlements[0].originalTransactionId).toBe("orig-txn-001");
  });

  it("returns premium: false when caregiver has no entitlements", async () => {
    findEntitlementsByCaregiverIdMock.mockResolvedValue([]);

    const { GET } = await import("../../app/api/me/entitlements/route");
    const req = new Request("http://localhost/api/me/entitlements", {
      method: "GET",
      headers: caregiverHeaders()
    });

    const res = await GET(req);
    expect(res.status).toBe(200);

    const body = await res.json();
    expect(body.data.premium).toBe(false);
    expect(body.data.entitlements).toEqual([]);
  });

  it("returns 401 when authorization header is missing", async () => {
    const { GET } = await import("../../app/api/me/entitlements/route");
    const req = new Request("http://localhost/api/me/entitlements", {
      method: "GET",
      headers: {}
    });

    const res = await GET(req);
    expect(res.status).toBe(401);
  });
});
