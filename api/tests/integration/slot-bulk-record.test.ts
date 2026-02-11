import { describe, expect, it, vi, beforeEach, afterAll } from "vitest";

// ---------------------------------------------------------------------------
// T001 – T003: Backend integration tests for POST /api/patient/dose-records/slot
// ---------------------------------------------------------------------------

// -- Types ------------------------------------------------------------------

type DoseRecord = {
  id: string;
  patientId: string;
  medicationId: string;
  scheduledAt: Date;
  takenAt: Date;
  recordedByType: "PATIENT" | "CAREGIVER";
  recordedById: string | null;
  recordingGroupId: string | null;
  createdAt: Date;
  updatedAt: Date;
};

// -- In-memory store --------------------------------------------------------

const store = new Map<string, DoseRecord>();

// -- Mocks ------------------------------------------------------------------

vi.mock("../../src/auth/patientSessionVerifier", () => ({
  patientSessionVerifier: {
    verify: vi.fn(async (token: string) => {
      if (token === "patient-token") {
        return { patientId: "patient-1" };
      }
      throw new Error("Invalid token");
    })
  }
}));

vi.mock("../../src/auth/supabaseJwt", () => ({
  verifySupabaseJwt: vi.fn(async () => ({ caregiverUserId: "caregiver-1" }))
}));

vi.mock("../../src/repositories/doseRecordRepo", () => ({
  upsertDoseRecord: vi.fn(async (input: {
    patientId: string;
    medicationId: string;
    scheduledAt: Date;
    recordedByType: "PATIENT" | "CAREGIVER";
    recordedById?: string | null;
  }) => {
    const key = `${input.patientId}:${input.medicationId}:${input.scheduledAt.toISOString()}`;
    const existing = store.get(key);
    if (existing) {
      return existing;
    }
    const now = new Date();
    const record: DoseRecord = {
      id: `dose-${store.size + 1}`,
      patientId: input.patientId,
      medicationId: input.medicationId,
      scheduledAt: input.scheduledAt,
      takenAt: now,
      recordedByType: input.recordedByType,
      recordedById: input.recordedById ?? null,
      recordingGroupId: null,
      createdAt: now,
      updatedAt: now
    };
    store.set(key, record);
    return record;
  }),
  getDoseRecordByKey: vi.fn(async (key: { patientId: string; medicationId: string; scheduledAt: Date }) => {
    const lookupKey = `${key.patientId}:${key.medicationId}:${key.scheduledAt.toISOString()}`;
    return store.get(lookupKey) ?? null;
  }),
  listDoseRecordsByPatientRange: vi.fn(async (input: {
    patientId: string;
    from: Date;
    to: Date;
  }) => {
    const results: DoseRecord[] = [];
    for (const record of store.values()) {
      if (
        record.patientId === input.patientId &&
        record.scheduledAt >= input.from &&
        record.scheduledAt < input.to
      ) {
        results.push(record);
      }
    }
    return results;
  })
}));

vi.mock("../../src/repositories/patientRepo", () => ({
  getPatientRecordById: vi.fn(async (patientId: string) => ({
    id: patientId,
    caregiverId: "caregiver-1",
    displayName: "Test Patient",
    createdAt: new Date(),
    updatedAt: new Date()
  })),
  getPatientRecordForCaregiver: vi.fn(async () => null)
}));

const medications: Record<string, {
  id: string;
  patientId: string;
  name: string;
  dosageText: string;
  doseCountPerIntake: number;
  dosageStrengthValue: number;
  dosageStrengthUnit: string;
  notes: null;
  isPrn: boolean;
  startDate: Date;
  endDate: null;
  inventoryCount: null;
  inventoryUnit: null;
  inventoryEnabled: boolean;
  inventoryQuantity: number;
  inventoryLowThreshold: number;
  inventoryUpdatedAt: null;
  inventoryLastAlertState: null;
  isActive: boolean;
  isArchived: boolean;
  createdAt: Date;
  updatedAt: Date;
}> = {
  "med-1": {
    id: "med-1",
    patientId: "patient-1",
    name: "アムロジピン",
    dosageText: "5mg",
    doseCountPerIntake: 2,
    dosageStrengthValue: 5,
    dosageStrengthUnit: "mg",
    notes: null,
    isPrn: false,
    startDate: new Date("2026-01-01"),
    endDate: null,
    inventoryCount: null,
    inventoryUnit: null,
    inventoryEnabled: false,
    inventoryQuantity: 0,
    inventoryLowThreshold: 0,
    inventoryUpdatedAt: null,
    inventoryLastAlertState: null,
    isActive: true,
    isArchived: false,
    createdAt: new Date(),
    updatedAt: new Date()
  },
  "med-2": {
    id: "med-2",
    patientId: "patient-1",
    name: "ロサルタン",
    dosageText: "50mg",
    doseCountPerIntake: 1,
    dosageStrengthValue: 50,
    dosageStrengthUnit: "mg",
    notes: null,
    isPrn: false,
    startDate: new Date("2026-01-01"),
    endDate: null,
    inventoryCount: null,
    inventoryUnit: null,
    inventoryEnabled: false,
    inventoryQuantity: 0,
    inventoryLowThreshold: 0,
    inventoryUpdatedAt: null,
    inventoryLastAlertState: null,
    isActive: true,
    isArchived: false,
    createdAt: new Date(),
    updatedAt: new Date()
  },
  "med-3": {
    id: "med-3",
    patientId: "patient-1",
    name: "メトホルミン",
    dosageText: "500mg",
    doseCountPerIntake: 3,
    dosageStrengthValue: 500,
    dosageStrengthUnit: "mg",
    notes: null,
    isPrn: false,
    startDate: new Date("2026-01-01"),
    endDate: null,
    inventoryCount: null,
    inventoryUnit: null,
    inventoryEnabled: false,
    inventoryQuantity: 0,
    inventoryLowThreshold: 0,
    inventoryUpdatedAt: null,
    inventoryLastAlertState: null,
    isActive: true,
    isArchived: false,
    createdAt: new Date(),
    updatedAt: new Date()
  }
};

vi.mock("../../src/repositories/medicationRepo", () => ({
  getMedicationRecordForPatient: vi.fn(async (_patientId: string, medicationId: string) => {
    return medications[medicationId] ?? null;
  })
}));

vi.mock("../../src/repositories/doseRecordEventRepo", () => ({
  createDoseRecordEvent: vi.fn(async (input: {
    patientId: string;
    scheduledAt: Date;
    takenAt: Date;
    withinTime: boolean;
    displayName: string;
    medicationName?: string;
    isPrn: boolean;
  }) => ({
    id: `event-${Date.now()}`,
    ...input,
    createdAt: new Date()
  }))
}));

vi.mock("../../src/services/pushNotificationService", () => ({
  notifyCaregiversOfDoseRecord: vi.fn(async () => {})
}));

vi.mock("../../src/services/medicationService", () => ({
  applyInventoryDeltaForDoseRecord: vi.fn(async () => {})
}));

// -- Helpers ----------------------------------------------------------------

function patientHeaders() {
  return {
    authorization: "Bearer patient-token",
    "content-type": "application/json"
  };
}

function caregiverHeaders() {
  return {
    authorization: "Bearer caregiver-valid-token",
    "content-type": "application/json"
  };
}

function makePostRequest(body: Record<string, unknown>, queryString = "") {
  const url = `http://localhost/api/patient/dose-records/slot${queryString}`;
  return new Request(url, {
    method: "POST",
    headers: patientHeaders(),
    body: JSON.stringify(body)
  });
}

function makeCaregiverPostRequest(body: Record<string, unknown>) {
  const url = "http://localhost/api/patient/dose-records/slot";
  return new Request(url, {
    method: "POST",
    headers: caregiverHeaders(),
    body: JSON.stringify(body)
  });
}

function makeUnauthPostRequest(body: Record<string, unknown>) {
  const url = "http://localhost/api/patient/dose-records/slot";
  return new Request(url, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body)
  });
}

// Morning = 07:30 JST = 22:30 UTC previous day
// 2026-02-11T07:30:00+09:00 = 2026-02-10T22:30:00.000Z
const MORNING_SCHEDULED_AT_1 = "2026-02-10T22:30:00.000Z";
const MORNING_SCHEDULED_AT_2 = "2026-02-10T22:30:00.000Z";
const MORNING_SCHEDULED_AT_3 = "2026-02-10T22:30:00.000Z";

// For tests needing 3 distinct medications at the same morning slot time
function makeMorningDoses(status: "pending" | "missed" = "pending") {
  return [
    {
      patientId: "patient-1",
      medicationId: "med-1",
      scheduledAt: MORNING_SCHEDULED_AT_1,
      effectiveStatus: status,
      medicationSnapshot: {
        name: "アムロジピン",
        dosageText: "5mg",
        doseCountPerIntake: 2,
        dosageStrengthValue: 5,
        dosageStrengthUnit: "mg"
      }
    },
    {
      patientId: "patient-1",
      medicationId: "med-2",
      scheduledAt: MORNING_SCHEDULED_AT_1,
      effectiveStatus: status,
      medicationSnapshot: {
        name: "ロサルタン",
        dosageText: "50mg",
        doseCountPerIntake: 1,
        dosageStrengthValue: 50,
        dosageStrengthUnit: "mg"
      }
    },
    {
      patientId: "patient-1",
      medicationId: "med-3",
      scheduledAt: MORNING_SCHEDULED_AT_1,
      effectiveStatus: status,
      medicationSnapshot: {
        name: "メトホルミン",
        dosageText: "500mg",
        doseCountPerIntake: 3,
        dosageStrengthValue: 500,
        dosageStrengthUnit: "mg"
      }
    }
  ];
}

// -- Import route handler (will be created in Phase 2) ----------------------
// This import will fail until T009 creates the route file.
// Tests are expected to fail in Phase 1 and pass after Phase 2.

let POST: (request: Request) => Promise<Response>;

try {
  const mod = await import("../../app/api/patient/dose-records/slot/route");
  POST = mod.POST;
} catch {
  // Route not yet implemented — all tests will fail with a clear message.
  POST = async () => new Response("Not implemented", { status: 501 });
}

// -- Mock schedule service to return controlled doses -----------------------

let mockScheduleDoses: ReturnType<typeof makeMorningDoses> = [];

vi.mock("../../src/services/scheduleService", async (importOriginal) => {
  const original = await importOriginal() as Record<string, unknown>;
  return {
    ...original,
    getScheduleWithStatus: vi.fn(async () => mockScheduleDoses),
    generateScheduleForPatientWithStatus: vi.fn(async () => mockScheduleDoses)
  };
});

// -- Mock prisma.$transaction for bulk upserts ------------------------------

vi.mock("../../src/repositories/prisma", () => ({
  prisma: {
    doseRecord: {
      upsert: vi.fn(async (args: {
        where: { patientId_medicationId_scheduledAt: { patientId: string; medicationId: string; scheduledAt: Date } };
        create: {
          patientId: string;
          medicationId: string;
          scheduledAt: Date;
          takenAt: Date;
          recordedByType: string;
          recordedById: string | null;
          recordingGroupId: string;
        };
        update: Record<string, never>;
      }) => {
        const key = `${args.where.patientId_medicationId_scheduledAt.patientId}:${args.where.patientId_medicationId_scheduledAt.medicationId}:${args.where.patientId_medicationId_scheduledAt.scheduledAt.toISOString()}`;
        const existing = store.get(key);
        if (existing) {
          return existing;
        }
        const record: DoseRecord = {
          id: `dose-${store.size + 1}`,
          patientId: args.create.patientId,
          medicationId: args.create.medicationId,
          scheduledAt: args.create.scheduledAt,
          takenAt: args.create.takenAt,
          recordedByType: args.create.recordedByType as "PATIENT" | "CAREGIVER",
          recordedById: args.create.recordedById,
          recordingGroupId: args.create.recordingGroupId,
          createdAt: new Date(),
          updatedAt: new Date()
        };
        store.set(key, record);
        return record;
      })
    },
    $transaction: vi.fn(async (promises: Promise<DoseRecord>[]) => {
      return Promise.all(promises);
    })
  }
}));

// ---------------------------------------------------------------------------
// T001: PENDING bulk -> TAKEN (core happy path)
// ---------------------------------------------------------------------------

// Fix "now" to be within the recording window of the morning slot (07:30 JST = 22:30 UTC)
vi.useFakeTimers();
vi.setSystemTime(new Date("2026-02-10T22:30:00.000Z"));

describe("slot bulk record integration", () => {
  beforeEach(() => {
    store.clear();
    mockScheduleDoses = [];
  });

  afterAll(() => {
    vi.useRealTimers();
  });

  describe("T001: PENDING bulk -> TAKEN", () => {
    it("records 3 PENDING morning doses as TAKEN in a single bulk operation", async () => {
      mockScheduleDoses = makeMorningDoses("pending");

      const request = makePostRequest({ date: "2026-02-11", slot: "morning" });
      const response = await POST(request);
      const body = await response.json();

      expect(response.status).toBe(200);
      expect(body.updatedCount).toBe(3);
      expect(body.remainingCount).toBe(0);
      expect(body.recordingGroupId).toBeDefined();
      expect(typeof body.recordingGroupId).toBe("string");
    });
  });

  // ---------------------------------------------------------------------------
  // T002: MISSED->TAKEN, idempotent, auth, and validation
  // ---------------------------------------------------------------------------

  describe("T002: MISSED->TAKEN, idempotent, auth, validation", () => {
    it("records 2 MISSED morning doses as TAKEN with withinTime=false", async () => {
      // Schedule doses as missed (takenAt will be well after scheduledAt + 60m)
      mockScheduleDoses = [
        {
          patientId: "patient-1",
          medicationId: "med-1",
          scheduledAt: MORNING_SCHEDULED_AT_1,
          effectiveStatus: "missed",
          medicationSnapshot: {
            name: "アムロジピン",
            dosageText: "5mg",
            doseCountPerIntake: 2,
            dosageStrengthValue: 5,
            dosageStrengthUnit: "mg"
          }
        },
        {
          patientId: "patient-1",
          medicationId: "med-2",
          scheduledAt: MORNING_SCHEDULED_AT_1,
          effectiveStatus: "missed",
          medicationSnapshot: {
            name: "ロサルタン",
            dosageText: "50mg",
            doseCountPerIntake: 1,
            dosageStrengthValue: 50,
            dosageStrengthUnit: "mg"
          }
        }
      ];

      const request = makePostRequest({ date: "2026-02-11", slot: "morning" });
      const response = await POST(request);
      const body = await response.json();

      expect(response.status).toBe(200);
      expect(body.updatedCount).toBe(2);
    });

    it("is idempotent: second call returns updatedCount=0", async () => {
      mockScheduleDoses = makeMorningDoses("pending");

      // First call
      const request1 = makePostRequest({ date: "2026-02-11", slot: "morning" });
      const response1 = await POST(request1);
      const body1 = await response1.json();
      expect(response1.status).toBe(200);
      expect(body1.updatedCount).toBe(3);

      // After first call, the doses are now taken — update mock to reflect
      mockScheduleDoses = makeMorningDoses("pending").map((d) => ({
        ...d,
        effectiveStatus: "taken" as const
      }));

      // Second call — same slot, same day
      const request2 = makePostRequest({ date: "2026-02-11", slot: "morning" });
      const response2 = await POST(request2);
      const body2 = await response2.json();

      expect(response2.status).toBe(200);
      expect(body2.updatedCount).toBe(0);
    });

    it("returns 401 when no auth header is provided", async () => {
      const request = makeUnauthPostRequest({ date: "2026-02-11", slot: "morning" });
      const response = await POST(request);

      expect(response.status).toBe(401);
    });

    it("returns 403 when a caregiver token is used", async () => {
      const request = makeCaregiverPostRequest({ date: "2026-02-11", slot: "morning" });
      const response = await POST(request);

      expect(response.status).toBe(403);
    });

    it("returns 422 when date field is missing", async () => {
      const request = makePostRequest({ slot: "morning" });
      const response = await POST(request);
      const body = await response.json();

      expect(response.status).toBe(422);
      expect(body.messages).toBeDefined();
    });

    it("returns 422 when slot field is missing", async () => {
      const request = makePostRequest({ date: "2026-02-11" });
      const response = await POST(request);
      const body = await response.json();

      expect(response.status).toBe(422);
      expect(body.messages).toBeDefined();
    });

    it("returns 422 for invalid slot value 'lunch'", async () => {
      const request = makePostRequest({ date: "2026-02-11", slot: "lunch" });
      const response = await POST(request);
      const body = await response.json();

      expect(response.status).toBe(422);
      expect(body.messages).toBeDefined();
    });

    it("returns 422 for invalid date format '2026/02/11'", async () => {
      const request = makePostRequest({ date: "2026/02/11", slot: "morning" });
      const response = await POST(request);
      const body = await response.json();

      expect(response.status).toBe(422);
      expect(body.messages).toBeDefined();
    });
  });

  // ---------------------------------------------------------------------------
  // T003: totalPills/medCount/slotTime correctness
  // ---------------------------------------------------------------------------

  describe("T003: totalPills/medCount/slotTime response fields", () => {
    it("returns correct totalPills and medCount for 3 medications", async () => {
      // med-1: doseCountPerIntake=2, med-2: 1, med-3: 3 → totalPills=6, medCount=3
      mockScheduleDoses = makeMorningDoses("pending");

      const request = makePostRequest({ date: "2026-02-11", slot: "morning" });
      const response = await POST(request);
      const body = await response.json();

      expect(response.status).toBe(200);
      expect(body.totalPills).toBe(6);
      expect(body.medCount).toBe(3);
    });

    it("returns slotTime derived from first dose scheduledAt", async () => {
      // scheduledAt at 07:30 Tokyo → slotTime="07:30"
      mockScheduleDoses = makeMorningDoses("pending");

      const request = makePostRequest({ date: "2026-02-11", slot: "morning" });
      const response = await POST(request);
      const body = await response.json();

      expect(response.status).toBe(200);
      expect(body.slotTime).toBeDefined();
      expect(typeof body.slotTime).toBe("string");
    });

    it("respects custom slot times via query params", async () => {
      mockScheduleDoses = makeMorningDoses("pending");

      const request = makePostRequest(
        { date: "2026-02-11", slot: "morning" },
        "?morningTime=07:30"
      );
      const response = await POST(request);
      const body = await response.json();

      expect(response.status).toBe(200);
      expect(body.updatedCount).toBe(3);
    });

    it("returns slotSummary with correct status for all 4 slots", async () => {
      mockScheduleDoses = makeMorningDoses("pending");

      const request = makePostRequest({ date: "2026-02-11", slot: "morning" });
      const response = await POST(request);
      const body = await response.json();

      expect(response.status).toBe(200);
      expect(body.slotSummary).toBeDefined();
      expect(typeof body.slotSummary).toBe("object");
      // After recording morning, morning should be "taken", others "none"
      expect(body.slotSummary.morning).toBeDefined();
      expect(body.slotSummary.noon).toBeDefined();
      expect(body.slotSummary.evening).toBeDefined();
      expect(body.slotSummary.bedtime).toBeDefined();
    });

    it("returns a valid UUID recordingGroupId", async () => {
      mockScheduleDoses = makeMorningDoses("pending");

      const request = makePostRequest({ date: "2026-02-11", slot: "morning" });
      const response = await POST(request);
      const body = await response.json();

      expect(response.status).toBe(200);
      expect(body.recordingGroupId).toBeDefined();
      // Validate UUID format: 8-4-4-4-12
      const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
      expect(body.recordingGroupId).toMatch(uuidRegex);
    });

    it("handles mixed slot: 2 PENDING + 1 TAKEN → updatedCount=2", async () => {
      mockScheduleDoses = [
        {
          patientId: "patient-1",
          medicationId: "med-1",
          scheduledAt: MORNING_SCHEDULED_AT_1,
          effectiveStatus: "pending",
          medicationSnapshot: {
            name: "アムロジピン",
            dosageText: "5mg",
            doseCountPerIntake: 2,
            dosageStrengthValue: 5,
            dosageStrengthUnit: "mg"
          }
        },
        {
          patientId: "patient-1",
          medicationId: "med-2",
          scheduledAt: MORNING_SCHEDULED_AT_1,
          effectiveStatus: "taken",
          medicationSnapshot: {
            name: "ロサルタン",
            dosageText: "50mg",
            doseCountPerIntake: 1,
            dosageStrengthValue: 50,
            dosageStrengthUnit: "mg"
          }
        },
        {
          patientId: "patient-1",
          medicationId: "med-3",
          scheduledAt: MORNING_SCHEDULED_AT_1,
          effectiveStatus: "pending",
          medicationSnapshot: {
            name: "メトホルミン",
            dosageText: "500mg",
            doseCountPerIntake: 3,
            dosageStrengthValue: 500,
            dosageStrengthUnit: "mg"
          }
        }
      ];

      const request = makePostRequest({ date: "2026-02-11", slot: "morning" });
      const response = await POST(request);
      const body = await response.json();

      expect(response.status).toBe(200);
      expect(body.updatedCount).toBe(2);
      expect(body.remainingCount).toBe(0);
      // totalPills and medCount include ALL medications in the slot (including taken)
      expect(body.totalPills).toBe(6);
      expect(body.medCount).toBe(3);
    });
  });
});
