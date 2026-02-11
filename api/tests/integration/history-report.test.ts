import { describe, expect, it, vi, beforeEach } from "vitest";

// ---------------------------------------------------------------------------
// T001: Test fixtures and mock setup for history report integration tests
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
  getPatientRecordForCaregiver: vi.fn(
    async (patientId: string, caregiverUserId: string) => {
      if (patientId === "patient-1" && caregiverUserId === "caregiver-1") {
        return {
          id: "patient-1",
          caregiverId: "caregiver-1",
          displayName: "テスト太郎",
          createdAt: new Date(),
          updatedAt: new Date()
        };
      }
      return null;
    }
  )
}));

// Mock report service (new module — will be created in Phase 2)
const generateReportMock = vi.fn();
vi.mock("../../src/services/reportService", () => ({
  generateReport: generateReportMock
}));

// Mock report validator (new module — will be created in Phase 2)
const validateReportRangeMock = vi.fn();
vi.mock("../../src/validators/reportValidator", () => ({
  validateReportRange: validateReportRangeMock,
  MAX_REPORT_RANGE_DAYS: 90
}));

// Mock InvalidRangeError (new module — will be created in Phase 2)
class MockInvalidRangeError extends Error {
  readonly code = "INVALID_RANGE" as const;
  readonly statusCode = 400 as const;
  constructor(message = "指定された期間が不正です。") {
    super(message);
    this.name = "InvalidRangeError";
  }
}
vi.mock("../../src/errors/invalidRangeError", () => ({
  InvalidRangeError: MockInvalidRangeError
}));

// Mock schedule services (may be imported transitively by report service)
vi.mock("../../src/services/scheduleService", () => ({
  getScheduleWithStatus: vi.fn(async () => []),
  getMonthRange: vi.fn(),
  getDayRange: vi.fn(),
  getLocalDateKey: vi.fn()
}));

vi.mock("../../src/services/scheduleResponse", () => ({
  buildSlotSummary: vi.fn(),
  groupDosesByLocalDate: vi.fn(() => new Map()),
  resolveSlot: vi.fn(() => "morning"),
  parseSlotTimesFromParams: vi.fn()
}));

vi.mock("../../src/services/prnDoseRecordService", () => ({
  listPrnHistoryItemsByRange: vi.fn(async () => ({ items: [], countByDay: {} }))
}));

// Mock retention service
vi.mock("../../src/services/historyRetentionService", () => ({
  getTodayTokyo: vi.fn(() => "2026-02-11"),
  checkRetentionForDay: vi.fn(async () => undefined),
  checkRetentionForMonth: vi.fn(async () => undefined),
  getCutoffDate: vi.fn(() => "2026-01-13"),
  isPremiumForCaregiver: vi.fn(async () => true)
}));

// Mock prisma (entitlement lookup — not directly used but may be transitively imported)
vi.mock("../../src/repositories/prisma", () => ({
  prisma: {
    $connect: vi.fn(),
    $disconnect: vi.fn(),
    caregiverEntitlement: { findFirst: vi.fn() },
    caregiverPatientLink: { findFirst: vi.fn() }
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

/** Shifts a YYYY-MM-DD date string by `days` days. Negative = past. */
function shiftDate(dateStr: string, days: number): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  date.setDate(date.getDate() + days);
  const ry = date.getFullYear();
  const rm = String(date.getMonth() + 1).padStart(2, "0");
  const rd = String(date.getDate()).padStart(2, "0");
  return `${ry}-${rm}-${rd}`;
}

// ---------------------------------------------------------------------------
// Request helpers — T001
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
// Mock report data helper — T001
// ---------------------------------------------------------------------------

function mockReportResponse(from: string, to: string) {
  const fromDate = new Date(from);
  const toDate = new Date(to);
  const dayCount =
    Math.round((toDate.getTime() - fromDate.getTime()) / (1000 * 60 * 60 * 24)) + 1;

  return {
    patient: { id: "patient-1", displayName: "テスト太郎" },
    range: { from, to, timezone: "Asia/Tokyo", days: dayCount },
    days: [
      {
        date: from,
        slots: {
          morning: [
            {
              medicationId: "med-1",
              name: "テスト薬",
              dosageText: "5mg",
              doseCount: 1,
              status: "TAKEN",
              recordedAt: `${from}T08:15:00+09:00`
            }
          ],
          noon: [],
          evening: [],
          bedtime: []
        },
        prn: [
          {
            medicationId: "prn-1",
            name: "頓服薬",
            dosageText: "60mg",
            quantity: 1,
            recordedAt: `${from}T14:30:00+09:00`,
            recordedBy: "PATIENT"
          }
        ]
      }
    ]
  };
}

// ---------------------------------------------------------------------------
// T002: Integration test cases for report endpoint
// ---------------------------------------------------------------------------

describe("History report endpoint — valid ranges", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default: validator passes, report returns mock data
    validateReportRangeMock.mockImplementation(() => undefined);
    generateReportMock.mockImplementation(
      async (_patientId: string, from: string, to: string) =>
        mockReportResponse(from, to)
    );
  });

  it("returns 200 with correct response structure for valid 30-day range", async () => {
    const todayStr = getTodayTokyo();
    const fromStr = shiftDate(todayStr, -29);

    const url = `http://localhost/api/patients/patient-1/history/report?from=${fromStr}&to=${todayStr}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/report/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(res.status).toBe(200);
    const body = await res.json();

    // Patient info
    expect(body.patient).toBeDefined();
    expect(body.patient.id).toBe("patient-1");
    expect(body.patient.displayName).toBe("テスト太郎");

    // Range metadata
    expect(body.range).toBeDefined();
    expect(body.range.from).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(body.range.to).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(body.range.timezone).toBe("Asia/Tokyo");
    expect(typeof body.range.days).toBe("number");
    expect(body.range.days).toBeGreaterThan(0);

    // Days array
    expect(Array.isArray(body.days)).toBe(true);
    expect(body.days.length).toBeGreaterThan(0);

    // Day structure
    const day = body.days[0];
    expect(day.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(day.slots).toBeDefined();
    expect(Array.isArray(day.slots.morning)).toBe(true);
    expect(Array.isArray(day.slots.noon)).toBe(true);
    expect(Array.isArray(day.slots.evening)).toBe(true);
    expect(Array.isArray(day.slots.bedtime)).toBe(true);
    expect(Array.isArray(day.prn)).toBe(true);
  });

  it("returns 200 for valid 90-day range (boundary)", async () => {
    const todayStr = getTodayTokyo();
    const fromStr = shiftDate(todayStr, -89); // 90 days inclusive

    const url = `http://localhost/api/patients/patient-1/history/report?from=${fromStr}&to=${todayStr}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/report/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.patient).toBeDefined();
    expect(body.range).toBeDefined();
    expect(Array.isArray(body.days)).toBe(true);
  });
});

describe("History report endpoint — invalid ranges", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default: validator throws (invalid range scenario)
    validateReportRangeMock.mockImplementation(() => {
      throw new MockInvalidRangeError();
    });
  });

  it("returns 400 INVALID_RANGE when from param is missing", async () => {
    const todayStr = getTodayTokyo();
    const url = `http://localhost/api/patients/patient-1/history/report?to=${todayStr}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/report/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe("INVALID_RANGE");
    expect(typeof body.message).toBe("string");
  });

  it("returns 400 INVALID_RANGE when to param is missing", async () => {
    const fromStr = shiftDate(getTodayTokyo(), -10);
    const url = `http://localhost/api/patients/patient-1/history/report?from=${fromStr}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/report/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe("INVALID_RANGE");
  });

  it("returns 400 INVALID_RANGE when to is in the future", async () => {
    const futureStr = shiftDate(getTodayTokyo(), 5);
    const fromStr = getTodayTokyo();
    const url = `http://localhost/api/patients/patient-1/history/report?from=${fromStr}&to=${futureStr}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/report/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe("INVALID_RANGE");
  });

  it("returns 400 INVALID_RANGE when from is after to", async () => {
    const todayStr = getTodayTokyo();
    const yesterdayStr = shiftDate(todayStr, -1);
    const url = `http://localhost/api/patients/patient-1/history/report?from=${todayStr}&to=${yesterdayStr}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/report/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe("INVALID_RANGE");
  });

  it("returns 400 INVALID_RANGE when range exceeds 90 days (91 days)", async () => {
    const todayStr = getTodayTokyo();
    const fromStr = shiftDate(todayStr, -90); // 91 days inclusive
    const url = `http://localhost/api/patients/patient-1/history/report?from=${fromStr}&to=${todayStr}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/report/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe("INVALID_RANGE");
  });
});

describe("History report endpoint — auth enforcement", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    validateReportRangeMock.mockImplementation(() => undefined);
    generateReportMock.mockImplementation(
      async (_patientId: string, from: string, to: string) =>
        mockReportResponse(from, to)
    );
  });

  it("returns 403 for patient session (caregiver-only endpoint)", async () => {
    const todayStr = getTodayTokyo();
    const fromStr = shiftDate(todayStr, -10);
    const url = `http://localhost/api/patients/patient-1/history/report?from=${fromStr}&to=${todayStr}`;
    const req = new Request(url, { method: "GET", headers: patientHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/report/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    // Patient sessions should be rejected with 403 (Forbidden)
    expect(res.status).toBe(403);
  });

  it("returns 404 for caregiver requesting unlinked patient (concealment)", async () => {
    const todayStr = getTodayTokyo();
    const fromStr = shiftDate(todayStr, -10);
    const url = `http://localhost/api/patients/patient-unknown/history/report?from=${fromStr}&to=${todayStr}`;
    const req = new Request(url, { method: "GET", headers: caregiverHeaders() });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/report/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-unknown" })
    });

    // Concealment: unlinked patient returns 404, not 403
    expect(res.status).toBe(404);
  });

  it("returns 401 for unauthenticated request", async () => {
    const todayStr = getTodayTokyo();
    const fromStr = shiftDate(todayStr, -10);
    const url = `http://localhost/api/patients/patient-1/history/report?from=${fromStr}&to=${todayStr}`;
    const req = new Request(url, { method: "GET" });

    const { GET } = await import(
      "../../app/api/patients/[patientId]/history/report/route"
    );
    const res = await GET(req, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    // Missing auth header → 401 or 403 (depending on auth flow)
    expect([401, 403]).toContain(res.status);
  });
});
