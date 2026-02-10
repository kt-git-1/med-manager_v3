import { describe, expect, it, vi, beforeEach } from "vitest";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("../../src/auth/supabaseJwt", () => ({
  verifySupabaseJwt: vi.fn(async () => ({ caregiverUserId: "caregiver-limit-1" }))
}));

vi.mock("../../src/auth/patientSessionVerifier", () => ({
  patientSessionVerifier: {
    verify: vi.fn(async () => ({ patientId: "patient-1" }))
  }
}));

// Mock prisma for transaction control
const countMock = vi.fn();
const findFirstMock = vi.fn();
const patientCreateMock = vi.fn();
const linkCreateMock = vi.fn();
const patientFindManyMock = vi.fn();
const patientFindFirstMock = vi.fn();
const patientFindUniqueMock = vi.fn();

const txClient = {
  caregiverPatientLink: { count: countMock, create: linkCreateMock },
  caregiverEntitlement: { findFirst: findFirstMock },
  patient: { create: patientCreateMock }
};

vi.mock("../../src/repositories/prisma", () => ({
  prisma: {
    $connect: vi.fn(),
    $disconnect: vi.fn(),
    $transaction: vi.fn(async (callback: (tx: typeof txClient) => Promise<unknown>) => {
      return callback(txClient);
    }),
    caregiverPatientLink: {
      count: countMock,
      create: linkCreateMock
    },
    caregiverEntitlement: {
      findFirst: findFirstMock
    },
    patient: {
      create: patientCreateMock,
      findMany: patientFindManyMock,
      findFirst: patientFindFirstMock,
      findUnique: patientFindUniqueMock
    }
  }
}));

// ---------------------------------------------------------------------------
// Imports (after mocks)
// ---------------------------------------------------------------------------

import { CAREGIVER_TOKEN_PREFIX } from "../../src/constants";

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

function createPatientRequest(displayName = "New Patient") {
  return new Request("http://localhost/api/patients", {
    method: "POST",
    headers: caregiverHeaders(),
    body: JSON.stringify({ displayName })
  });
}

// ---------------------------------------------------------------------------
// POST /api/patients — Patient Limit Enforcement
// ---------------------------------------------------------------------------

describe("POST /api/patients — patient limit enforcement", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default: patient creation succeeds, returning the passed-in data
    patientCreateMock.mockImplementation(async (args: { data: { caregiverId: string; displayName: string } }) => ({
      id: "new-patient-1",
      caregiverId: args.data.caregiverId,
      displayName: args.data.displayName
    }));
    linkCreateMock.mockResolvedValue({
      id: "link-1",
      caregiverId: "caregiver-limit-1",
      patientId: "new-patient-1",
      status: "ACTIVE"
    });
  });

  it("allows free caregiver with 0 patients to create a patient (201)", async () => {
    // Free caregiver, no existing patients
    countMock.mockResolvedValue(0);
    findFirstMock.mockResolvedValue(null); // no entitlement

    const { POST } = await import("../../app/api/patients/route");
    const res = await POST(createPatientRequest("First Patient"));

    expect(res.status).toBe(201);
    const body = await res.json();
    expect(body.data).toBeDefined();
    expect(body.data.id).toBeDefined();
    expect(body.data.displayName).toBe("First Patient");
  });

  it("rejects free caregiver with 1 ACTIVE patient (403 PATIENT_LIMIT_EXCEEDED)", async () => {
    // Free caregiver, 1 existing active patient
    countMock.mockResolvedValue(1);
    findFirstMock.mockResolvedValue(null); // no entitlement (free)

    const { POST } = await import("../../app/api/patients/route");
    const res = await POST(createPatientRequest("Second Patient"));

    expect(res.status).toBe(403);
    const body = await res.json();
    expect(body.code).toBe("PATIENT_LIMIT_EXCEEDED");
    expect(body.limit).toBe(1);
    expect(body.current).toBe(1);
    expect(typeof body.message).toBe("string");
  });

  it("allows premium caregiver with 1 patient to create another (201)", async () => {
    // Premium caregiver, 1 existing active patient
    countMock.mockResolvedValue(1);
    findFirstMock.mockResolvedValue({
      id: "ent-1",
      caregiverId: "caregiver-limit-1",
      status: "ACTIVE"
    });

    const { POST } = await import("../../app/api/patients/route");
    const res = await POST(createPatientRequest("Second Patient"));

    expect(res.status).toBe(201);
    const body = await res.json();
    expect(body.data).toBeDefined();
  });

  it("rejects free caregiver with 3 pre-existing patients — grandfather (403)", async () => {
    // Free caregiver, 3 existing active patients (grandfather scenario)
    countMock.mockResolvedValue(3);
    findFirstMock.mockResolvedValue(null); // no entitlement (free)

    const { POST } = await import("../../app/api/patients/route");
    const res = await POST(createPatientRequest("Fourth Patient"));

    expect(res.status).toBe(403);
    const body = await res.json();
    expect(body.code).toBe("PATIENT_LIMIT_EXCEEDED");
    expect(body.limit).toBe(1);
    expect(body.current).toBe(3);
  });

  it("allows free caregiver with 1 REVOKED patient and 0 ACTIVE to create (201)", async () => {
    // Free caregiver, 0 active patients (1 revoked, not counted)
    countMock.mockResolvedValue(0); // only ACTIVE links counted
    findFirstMock.mockResolvedValue(null); // no entitlement

    const { POST } = await import("../../app/api/patients/route");
    const res = await POST(createPatientRequest("After Revoke Patient"));

    expect(res.status).toBe(201);
    const body = await res.json();
    expect(body.data).toBeDefined();
  });

  it("returns 403 when authorization header is missing", async () => {
    const { POST } = await import("../../app/api/patients/route");
    const req = new Request("http://localhost/api/patients", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ displayName: "Unauthorized Patient" })
    });

    const res = await POST(req);
    expect(res.status).toBe(403);
  });

  it("returns 403 when patient token is used", async () => {
    const { POST } = await import("../../app/api/patients/route");
    const req = new Request("http://localhost/api/patients", {
      method: "POST",
      headers: patientHeaders(),
      body: JSON.stringify({ displayName: "Wrong Token Patient" })
    });

    const res = await POST(req);
    expect(res.status).toBe(403);
  });
});

// ---------------------------------------------------------------------------
// GET /api/patients — Grandfather Viewing (no limit on listing)
// ---------------------------------------------------------------------------

describe("GET /api/patients — grandfather viewing", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns all patients for free caregiver with >1 linked patients (grandfather)", async () => {
    // This tests that listing is NOT gated — free caregivers see all their patients.
    // Grandfather rule: free caregiver with >1 patients can VIEW all, just can't ADD.
    patientFindManyMock.mockResolvedValue([
      { id: "p-1", caregiverId: "caregiver-limit-1", displayName: "Patient 1", createdAt: new Date(), updatedAt: new Date() },
      { id: "p-2", caregiverId: "caregiver-limit-1", displayName: "Patient 2", createdAt: new Date(), updatedAt: new Date() },
      { id: "p-3", caregiverId: "caregiver-limit-1", displayName: "Patient 3", createdAt: new Date(), updatedAt: new Date() }
    ]);

    const { GET } = await import("../../app/api/patients/route");
    const req = new Request("http://localhost/api/patients", {
      method: "GET",
      headers: caregiverHeaders()
    });

    // GET handler should succeed (no limit gate on list)
    const res = await GET(req);
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.data).toHaveLength(3);
  });
});
