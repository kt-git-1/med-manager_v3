import { describe, expect, it, vi, beforeEach } from "vitest";

// ---------------------------------------------------------------------------
// Mocks – T001: Test fixtures and mock setup
// ---------------------------------------------------------------------------

vi.mock("../../src/auth/supabaseJwt", () => ({
  verifySupabaseJwt: vi.fn(async () => ({ caregiverUserId: "caregiver-1" }))
}));

vi.mock("../../src/auth/patientSessionVerifier", () => ({
  patientSessionVerifier: {
    verify: vi.fn(async () => ({ patientId: "patient-1" }))
  }
}));

vi.mock("../../src/repositories/patientRepo", () => ({
  getPatientRecordForCaregiver: vi.fn(async (patientId: string, caregiverUserId: string) => {
    if (patientId === "patient-1" && caregiverUserId === "caregiver-1") {
      return {
        id: "patient-1",
        caregiverId: "caregiver-1",
        displayName: "Test Patient",
        createdAt: new Date(),
        updatedAt: new Date()
      };
    }
    return null;
  })
}));

// Mock schedule/dose services to return minimal valid data so routes
// can succeed when retention check passes.
vi.mock("../../src/services/scheduleService", () => ({
  getScheduleWithStatus: vi.fn(async () => []),
  getMonthRange: vi.fn((year: number, month: number) => {
    // Return a 1-day range to keep iteration fast in tests
    const from = new Date(Date.UTC(year, month - 1, 1));
    const to = new Date(Date.UTC(year, month - 1, 2));
    return { from, to };
  }),
  getDayRange: vi.fn((date: Date) => {
    const from = new Date(date);
    from.setUTCHours(0, 0, 0, 0);
    const to = new Date(from);
    to.setUTCDate(to.getUTCDate() + 1);
    return { from, to };
  }),
  getLocalDateKey: vi.fn((_date: Date, _tz: string) => "2026-01-01")
}));

vi.mock("../../src/services/scheduleResponse", () => ({
  buildSlotSummary: vi.fn(() => ({
    morning: "none",
    noon: "none",
    evening: "none",
    bedtime: "none"
  })),
  groupDosesByLocalDate: vi.fn(() => new Map()),
  parseSlotTimesFromParams: vi.fn(() => ({ slotTimes: undefined, errors: [] }))
}));

vi.mock("../../src/services/prnDoseRecordService", () => ({
  listPrnHistoryItemsByRange: vi.fn(async () => ({ items: [], countByDay: {} }))
}));

// Mock prisma — retention service will use these for premium resolution
const entitlementFindFirstMock = vi.fn();
const linkFindFirstMock = vi.fn();

vi.mock("../../src/repositories/prisma", () => ({
  prisma: {
    $connect: vi.fn(),
    $disconnect: vi.fn(),
    caregiverEntitlement: {
      findFirst: entitlementFindFirstMock
    },
    caregiverPatientLink: {
      findFirst: linkFindFirstMock
    }
  }
}));

// ---------------------------------------------------------------------------
// Imports (after mocks)
// ---------------------------------------------------------------------------

import { CAREGIVER_TOKEN_PREFIX } from "../../src/constants";

// ---------------------------------------------------------------------------
// Date helpers
// ---------------------------------------------------------------------------

/** Returns today's date in YYYY-MM-DD using Asia/Tokyo timezone. */
function getTodayTokyo(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Tokyo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).format(new Date());
}

/** Returns the cutoff date (todayTokyo - 29 days) as YYYY-MM-DD. */
function getCutoffDate(): string {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Tokyo",
    year: "numeric",
    month: "numeric",
    day: "numeric"
  }).formatToParts(new Date());
  const year = Number(parts.find((p) => p.type === "year")!.value);
  const month = Number(parts.find((p) => p.type === "month")!.value);
  const day = Number(parts.find((p) => p.type === "day")!.value);
  const date = new Date(year, month - 1, day);
  date.setDate(date.getDate() - 29);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

/** Returns a date string 3 months before today (clearly before cutoff). */
function getDateBeforeCutoff(): string {
  const date = new Date();
  date.setMonth(date.getMonth() - 3);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

/** Returns year/month for 3 months ago (fully before cutoff). */
function getYearMonthBeforeCutoff(): { year: number; month: number } {
  const date = new Date();
  date.setMonth(date.getMonth() - 3);
  return { year: date.getFullYear(), month: date.getMonth() + 1 };
}

/** Returns year/month for a straddling month (cutoff falls within the month). */
function getStraddlingYearMonth(): { year: number; month: number } {
  const cutoff = getCutoffDate();
  const [yearStr, monthStr] = cutoff.split("-");
  return { year: Number(yearStr), month: Number(monthStr) };
}

// ---------------------------------------------------------------------------
// Request helpers
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

// ---------------------------------------------------------------------------
// Fixture helpers — T001
// ---------------------------------------------------------------------------

function setFreeCaregiver() {
  entitlementFindFirstMock.mockResolvedValue(null);
}

function setPremiumCaregiver() {
  entitlementFindFirstMock.mockResolvedValue({
    id: "ent-1",
    caregiverId: "caregiver-1",
    status: "ACTIVE",
    productId: "premium_unlock"
  });
}

function setPatientLinkedToFree() {
  linkFindFirstMock.mockResolvedValue({
    id: "link-1",
    caregiverId: "caregiver-free",
    patientId: "patient-1",
    status: "ACTIVE"
  });
  entitlementFindFirstMock.mockResolvedValue(null);
}

function setPatientLinkedToPremium() {
  linkFindFirstMock.mockResolvedValue({
    id: "link-1",
    caregiverId: "caregiver-premium",
    patientId: "patient-1",
    status: "ACTIVE"
  });
  entitlementFindFirstMock.mockResolvedValue({
    id: "ent-2",
    caregiverId: "caregiver-premium",
    status: "ACTIVE",
    productId: "premium_unlock"
  });
}

// ---------------------------------------------------------------------------
// T002: Integration tests for retention enforcement
// ---------------------------------------------------------------------------

describe("History retention enforcement — caregiver month", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns 403 HISTORY_RETENTION_LIMIT for free caregiver — month entirely before cutoff", async () => {
    setFreeCaregiver();
    const { year, month } = getYearMonthBeforeCutoff();
    const url = `http://localhost/api/patients/patient-1/history/month?year=${year}&month=${month}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/month/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(res.status).toBe(403);
    const body = await res.json();
    expect(body.code).toBe("HISTORY_RETENTION_LIMIT");
    expect(body.cutoffDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(body.retentionDays).toBe(30);
    expect(typeof body.message).toBe("string");
  });

  it("returns 403 HISTORY_RETENTION_LIMIT for free caregiver — straddling month", async () => {
    setFreeCaregiver();
    const { year, month } = getStraddlingYearMonth();
    const url = `http://localhost/api/patients/patient-1/history/month?year=${year}&month=${month}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/month/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(res.status).toBe(403);
    const body = await res.json();
    expect(body.code).toBe("HISTORY_RETENTION_LIMIT");
  });

  it("returns 200 for premium caregiver — month before cutoff", async () => {
    setPremiumCaregiver();
    const { year, month } = getYearMonthBeforeCutoff();
    const url = `http://localhost/api/patients/patient-1/history/month?year=${year}&month=${month}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/month/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(res.status).toBe(200);
  });
});

describe("History retention enforcement — caregiver day", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns 403 HISTORY_RETENTION_LIMIT for free caregiver — day before cutoff", async () => {
    setFreeCaregiver();
    const dateStr = getDateBeforeCutoff();
    const url = `http://localhost/api/patients/patient-1/history/day?date=${dateStr}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/day/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(res.status).toBe(403);
    const body = await res.json();
    expect(body.code).toBe("HISTORY_RETENTION_LIMIT");
    expect(body.cutoffDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(body.retentionDays).toBe(30);
  });

  it("returns 200 for premium caregiver — day before cutoff", async () => {
    setPremiumCaregiver();
    const dateStr = getDateBeforeCutoff();
    const url = `http://localhost/api/patients/patient-1/history/day?date=${dateStr}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/day/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(res.status).toBe(200);
  });

  it("returns 200 for free caregiver — day within retention range", async () => {
    setFreeCaregiver();
    const todayStr = getTodayTokyo();
    const url = `http://localhost/api/patients/patient-1/history/day?date=${todayStr}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/day/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(res.status).toBe(200);
  });
});

describe("History retention enforcement — patient month", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns 403 for free patient (linked to free caregiver) — month before cutoff", async () => {
    setPatientLinkedToFree();
    const { year, month } = getYearMonthBeforeCutoff();
    const url = `http://localhost/api/patient/history/month?year=${year}&month=${month}`;
    const req = new Request(url, { method: "GET", headers: patientHeaders() });

    const { GET } = await import("../../app/api/patient/history/month/route");
    const res = await GET(req);

    expect(res.status).toBe(403);
    const body = await res.json();
    expect(body.code).toBe("HISTORY_RETENTION_LIMIT");
  });

  it("returns 200 for patient linked to premium caregiver — month before cutoff", async () => {
    setPatientLinkedToPremium();
    const { year, month } = getYearMonthBeforeCutoff();
    const url = `http://localhost/api/patient/history/month?year=${year}&month=${month}`;
    const req = new Request(url, { method: "GET", headers: patientHeaders() });

    const { GET } = await import("../../app/api/patient/history/month/route");
    const res = await GET(req);

    expect(res.status).toBe(200);
  });
});

describe("History retention enforcement — patient day", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns 403 for free patient — day before cutoff", async () => {
    setPatientLinkedToFree();
    const dateStr = getDateBeforeCutoff();
    const url = `http://localhost/api/patient/history/day?date=${dateStr}`;
    const req = new Request(url, { method: "GET", headers: patientHeaders() });

    const { GET } = await import("../../app/api/patient/history/day/route");
    const res = await GET(req);

    expect(res.status).toBe(403);
    const body = await res.json();
    expect(body.code).toBe("HISTORY_RETENTION_LIMIT");
  });

  it("returns 200 for patient linked to premium caregiver — day before cutoff", async () => {
    setPatientLinkedToPremium();
    const dateStr = getDateBeforeCutoff();
    const url = `http://localhost/api/patient/history/day?date=${dateStr}`;
    const req = new Request(url, { method: "GET", headers: patientHeaders() });

    const { GET } = await import("../../app/api/patient/history/day/route");
    const res = await GET(req);

    expect(res.status).toBe(200);
  });
});

describe("History retention — auth preserved", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns 401 for unauthenticated request to caregiver month endpoint", async () => {
    const url = "http://localhost/api/patients/patient-1/history/month?year=2026&month=1";
    const req = new Request(url, { method: "GET" });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/month/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    // Existing auth behavior: missing token → 401 or 403
    expect([401, 403]).toContain(res.status);
  });
});
