import { describe, expect, it, vi, beforeEach } from "vitest";

// ---------------------------------------------------------------------------
// T001 – T003: Backend integration tests for POST /api/push/register
//              and POST /api/push/unregister
// ---------------------------------------------------------------------------

// -- Types ------------------------------------------------------------------

type PushDevice = {
  id: string;
  ownerType: string;
  ownerId: string;
  token: string;
  platform: string;
  environment: string;
  isEnabled: boolean;
  lastSeenAt: Date;
  createdAt: Date;
  updatedAt: Date;
};

// -- In-memory store --------------------------------------------------------

const store = new Map<string, PushDevice>();

// -- Mocks ------------------------------------------------------------------

vi.mock("../../src/auth/supabaseJwt", () => ({
  verifySupabaseJwt: vi.fn(async () => ({ caregiverUserId: "caregiver-1" }))
}));

vi.mock("../../src/auth/patientSessionVerifier", () => ({
  patientSessionVerifier: {
    verify: vi.fn(async () => {
      throw new Error("Invalid token");
    })
  }
}));

// PushDevice repo mock
const upsertPushDeviceMock = vi.fn();
const disablePushDeviceMock = vi.fn();

vi.mock("../../src/repositories/pushDeviceRepo", () => ({
  upsertPushDevice: (...args: unknown[]) => upsertPushDeviceMock(...args),
  disablePushDevice: (...args: unknown[]) => disablePushDeviceMock(...args)
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

function validRegisterBody() {
  return {
    token: "fcm-token-1",
    platform: "ios",
    environment: "DEV"
  };
}

function validUnregisterBody() {
  return {
    token: "fcm-token-1"
  };
}

// ---------------------------------------------------------------------------
// POST /api/push/register — T001: Upsert + idempotent tests
// ---------------------------------------------------------------------------

describe("POST /api/push/register", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    store.clear();

    upsertPushDeviceMock.mockImplementation(
      async (input: { ownerType: string; ownerId: string; token: string; platform: string; environment: string }) => {
        const key = `${input.ownerType}:${input.ownerId}:${input.token}`;
        const now = new Date();
        const existing = store.get(key);
        if (existing) {
          // Upsert: update lastSeenAt and re-enable
          const updated = { ...existing, isEnabled: true, lastSeenAt: now, updatedAt: now };
          store.set(key, updated);
          return updated;
        }
        const device: PushDevice = {
          id: `push-device-${store.size + 1}`,
          ownerType: input.ownerType,
          ownerId: input.ownerId,
          token: input.token,
          platform: input.platform,
          environment: input.environment,
          isEnabled: true,
          lastSeenAt: now,
          createdAt: now,
          updatedAt: now
        };
        store.set(key, device);
        return device;
      }
    );
  });

  it("registers a new device with isEnabled=true", async () => {
    const { POST } = await import("../../app/api/push/register/route");
    const req = new Request("http://localhost/api/push/register", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify(validRegisterBody())
    });

    const res = await POST(req);
    expect(res.status).toBe(200);

    const body = await res.json();
    expect(body.ok).toBe(true);

    // Verify upsert was called with correct args
    expect(upsertPushDeviceMock).toHaveBeenCalledTimes(1);
    expect(upsertPushDeviceMock).toHaveBeenCalledWith({
      ownerType: "CAREGIVER",
      ownerId: "caregiver-1",
      token: "fcm-token-1",
      platform: "ios",
      environment: "DEV"
    });

    // Verify in-memory store
    const key = "CAREGIVER:caregiver-1:fcm-token-1";
    const device = store.get(key);
    expect(device).toBeDefined();
    expect(device!.isEnabled).toBe(true);
  });

  it("re-registers same token idempotently (lastSeenAt updated)", async () => {
    const { POST } = await import("../../app/api/push/register/route");

    // First registration
    const req1 = new Request("http://localhost/api/push/register", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify(validRegisterBody())
    });
    await POST(req1);

    const key = "CAREGIVER:caregiver-1:fcm-token-1";
    const firstDevice = store.get(key)!;
    const firstLastSeenAt = firstDevice.lastSeenAt;

    // Small delay to ensure timestamp difference
    await new Promise((r) => setTimeout(r, 10));

    // Second registration (same token)
    const req2 = new Request("http://localhost/api/push/register", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify(validRegisterBody())
    });
    const res2 = await POST(req2);

    expect(res2.status).toBe(200);
    expect(upsertPushDeviceMock).toHaveBeenCalledTimes(2);

    // lastSeenAt should be updated
    const updatedDevice = store.get(key)!;
    expect(updatedDevice.lastSeenAt.getTime()).toBeGreaterThanOrEqual(firstLastSeenAt.getTime());
    expect(updatedDevice.isEnabled).toBe(true);
  });

  it("registers a second token for same caregiver", async () => {
    const { POST } = await import("../../app/api/push/register/route");

    // First token
    const req1 = new Request("http://localhost/api/push/register", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify(validRegisterBody())
    });
    await POST(req1);

    // Second token (different device)
    const req2 = new Request("http://localhost/api/push/register", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify({ token: "fcm-token-2", platform: "ios", environment: "DEV" })
    });
    const res2 = await POST(req2);

    expect(res2.status).toBe(200);
    expect(store.size).toBe(2);
  });
});

// ---------------------------------------------------------------------------
// POST /api/push/unregister — T002: Disable tests
// ---------------------------------------------------------------------------

describe("POST /api/push/unregister", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    store.clear();

    // Setup: pre-populate store for unregister tests
    upsertPushDeviceMock.mockImplementation(
      async (input: { ownerType: string; ownerId: string; token: string; platform: string; environment: string }) => {
        const key = `${input.ownerType}:${input.ownerId}:${input.token}`;
        const now = new Date();
        const existing = store.get(key);
        if (existing) {
          const updated = { ...existing, isEnabled: true, lastSeenAt: now, updatedAt: now };
          store.set(key, updated);
          return updated;
        }
        const device: PushDevice = {
          id: `push-device-${store.size + 1}`,
          ownerType: input.ownerType,
          ownerId: input.ownerId,
          token: input.token,
          platform: input.platform,
          environment: input.environment,
          isEnabled: true,
          lastSeenAt: now,
          createdAt: now,
          updatedAt: now
        };
        store.set(key, device);
        return device;
      }
    );

    disablePushDeviceMock.mockImplementation(
      async (input: { ownerType: string; ownerId: string; token: string }) => {
        const key = `${input.ownerType}:${input.ownerId}:${input.token}`;
        const existing = store.get(key);
        if (existing) {
          const updated = { ...existing, isEnabled: false, updatedAt: new Date() };
          store.set(key, updated);
        }
        // Idempotent: no error even if not found
      }
    );
  });

  it("disables a registered device (isEnabled=false)", async () => {
    // First register
    const { POST: registerPost } = await import("../../app/api/push/register/route");
    const regReq = new Request("http://localhost/api/push/register", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify(validRegisterBody())
    });
    await registerPost(regReq);

    const key = "CAREGIVER:caregiver-1:fcm-token-1";
    expect(store.get(key)!.isEnabled).toBe(true);

    // Now unregister
    const { POST: unregisterPost } = await import("../../app/api/push/unregister/route");
    const unregReq = new Request("http://localhost/api/push/unregister", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify(validUnregisterBody())
    });
    const res = await unregisterPost(unregReq);

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.ok).toBe(true);

    // Verify disabled
    expect(disablePushDeviceMock).toHaveBeenCalledWith({
      ownerType: "CAREGIVER",
      ownerId: "caregiver-1",
      token: "fcm-token-1"
    });
    expect(store.get(key)!.isEnabled).toBe(false);
  });

  it("returns 200 for unregistering non-existent token (idempotent)", async () => {
    const { POST } = await import("../../app/api/push/unregister/route");
    const req = new Request("http://localhost/api/push/unregister", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify({ token: "non-existent-token" })
    });

    const res = await POST(req);
    expect(res.status).toBe(200);

    const body = await res.json();
    expect(body.ok).toBe(true);
  });

  it("re-register after unregister restores isEnabled=true", async () => {
    const { POST: registerPost } = await import("../../app/api/push/register/route");
    const { POST: unregisterPost } = await import("../../app/api/push/unregister/route");

    // Register
    await registerPost(
      new Request("http://localhost/api/push/register", {
        method: "POST",
        headers: caregiverHeaders(),
        body: JSON.stringify(validRegisterBody())
      })
    );

    const key = "CAREGIVER:caregiver-1:fcm-token-1";
    expect(store.get(key)!.isEnabled).toBe(true);

    // Unregister
    await unregisterPost(
      new Request("http://localhost/api/push/unregister", {
        method: "POST",
        headers: caregiverHeaders(),
        body: JSON.stringify(validUnregisterBody())
      })
    );
    expect(store.get(key)!.isEnabled).toBe(false);

    // Re-register
    const res = await registerPost(
      new Request("http://localhost/api/push/register", {
        method: "POST",
        headers: caregiverHeaders(),
        body: JSON.stringify(validRegisterBody())
      })
    );
    expect(res.status).toBe(200);
    expect(store.get(key)!.isEnabled).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// Auth + Validation — T003: Error tests
// ---------------------------------------------------------------------------

describe("POST /api/push/register — auth & validation errors", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns 401 when authorization header is missing", async () => {
    const { POST } = await import("../../app/api/push/register/route");
    const req = new Request("http://localhost/api/push/register", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(validRegisterBody())
    });

    const res = await POST(req);
    expect(res.status).toBe(401);
  });

  it("returns 401 when patient token is used", async () => {
    const { POST } = await import("../../app/api/push/register/route");
    const req = new Request("http://localhost/api/push/register", {
      method: "POST",
      headers: patientHeaders(),
      body: JSON.stringify(validRegisterBody())
    });

    const res = await POST(req);
    expect(res.status).toBe(401);
  });

  it("returns 422 when token field is missing", async () => {
    const { POST } = await import("../../app/api/push/register/route");
    const req = new Request("http://localhost/api/push/register", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify({ platform: "ios", environment: "DEV" })
    });

    const res = await POST(req);
    expect(res.status).toBe(422);
  });

  it("returns 422 for invalid platform value", async () => {
    const { POST } = await import("../../app/api/push/register/route");
    const req = new Request("http://localhost/api/push/register", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify({ token: "fcm-token-1", platform: "android", environment: "DEV" })
    });

    const res = await POST(req);
    expect(res.status).toBe(422);
  });

  it("returns 422 when environment is missing", async () => {
    const { POST } = await import("../../app/api/push/register/route");
    const req = new Request("http://localhost/api/push/register", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify({ token: "fcm-token-1", platform: "ios" })
    });

    const res = await POST(req);
    expect(res.status).toBe(422);
  });
});

describe("POST /api/push/unregister — auth & validation errors", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns 401 when authorization header is missing", async () => {
    const { POST } = await import("../../app/api/push/unregister/route");
    const req = new Request("http://localhost/api/push/unregister", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(validUnregisterBody())
    });

    const res = await POST(req);
    expect(res.status).toBe(401);
  });

  it("returns 422 when token field is missing", async () => {
    const { POST } = await import("../../app/api/push/unregister/route");
    const req = new Request("http://localhost/api/push/unregister", {
      method: "POST",
      headers: caregiverHeaders(),
      body: JSON.stringify({})
    });

    const res = await POST(req);
    expect(res.status).toBe(422);
  });
});
