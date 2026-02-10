import { describe, expect, it, vi, beforeEach } from "vitest";

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
  findEntitlementsByCaregiverId: (...args: unknown[]) =>
    findEntitlementsByCaregiverIdMock(...args)
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

function validClaimBody() {
  // signedTransactionInfo is a JWS string; for tests we use a dummy three-part
  // dot-separated value that the MVP decoder can parse the payload section from.
  const payload = Buffer.from(
    JSON.stringify({
      originalTransactionId: "orig-txn-001",
      transactionId: "txn-001",
      purchaseDate: Date.now(),
      productId: VALID_PRODUCT_ID,
      environment: "Sandbox"
    })
  ).toString("base64url");
  const fakeJws = `header.${payload}.signature`;

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
