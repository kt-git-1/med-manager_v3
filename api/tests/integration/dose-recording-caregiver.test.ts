import { describe, expect, it, vi } from "vitest";
import { createDoseRecordIdempotent, deleteDoseRecord } from "../../src/services/doseRecordService";

type DoseRecord = {
  id: string;
  patientId: string;
  medicationId: string;
  scheduledAt: Date;
  takenAt: Date;
  recordedByType: "PATIENT" | "CAREGIVER";
  recordedById: string | null;
  createdAt: Date;
  updatedAt: Date;
};

const store = new Map<string, DoseRecord>();

function buildKey(input: { patientId: string; medicationId: string; scheduledAt: Date }) {
  return `${input.patientId}:${input.medicationId}:${input.scheduledAt.toISOString()}`;
}

vi.mock("../../src/repositories/doseRecordRepo", () => ({
  upsertDoseRecord: async (input: {
    patientId: string;
    medicationId: string;
    scheduledAt: Date;
    recordedByType: "PATIENT" | "CAREGIVER";
    recordedById?: string | null;
  }) => {
    const key = buildKey(input);
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
      createdAt: now,
      updatedAt: now
    };
    store.set(key, record);
    return record;
  },
  getDoseRecordByKey: async (key: { patientId: string; medicationId: string; scheduledAt: Date }) => {
    return store.get(buildKey(key)) ?? null;
  },
  deleteDoseRecordByKey: async (key: { patientId: string; medicationId: string; scheduledAt: Date }) => {
    const record = store.get(buildKey(key));
    if (!record) {
      throw new Error("Dose record not found");
    }
    store.delete(buildKey(key));
    return record;
  }
}));

vi.mock("../../src/repositories/patientRepo", () => ({
  getPatientRecordById: async (patientId: string) => ({
    id: patientId,
    caregiverId: "caregiver-1",
    displayName: "Test Patient",
    createdAt: new Date(),
    updatedAt: new Date()
  })
}));

vi.mock("../../src/repositories/doseRecordEventRepo", () => ({
  createDoseRecordEvent: async () => ({
    id: "event-1",
    patientId: "patient-1",
    scheduledAt: new Date(),
    takenAt: new Date(),
    withinTime: true,
    displayName: "Test Patient",
    createdAt: new Date()
  })
}));

vi.mock("../../src/repositories/medicationRepo", () => ({
  getMedicationRecordForPatient: async () => ({
    id: "med-1",
    patientId: "patient-1",
    name: "Medication A",
    dosageText: "1 tablet",
    doseCountPerIntake: 1,
    dosageStrengthValue: 10,
    dosageStrengthUnit: "mg",
    notes: null,
    startDate: new Date(),
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
  })
}));

describe("dose recording caregiver integration", () => {
  it("creates caregiver dose record with recordedById", async () => {
    store.clear();
    const scheduledAt = new Date("2026-02-02T08:00:00.000Z");
    const record = await createDoseRecordIdempotent({
      patientId: "patient-1",
      medicationId: "med-1",
      scheduledAt,
      recordedByType: "CAREGIVER",
      recordedById: "caregiver-1"
    });

    expect(record.recordedByType).toBe("CAREGIVER");
    expect(record.recordedById).toBe("caregiver-1");
    expect(store.size).toBe(1);
  });

  it("deletes caregiver dose record when present and returns null when missing", async () => {
    store.clear();
    const scheduledAt = new Date("2026-02-02T08:00:00.000Z");
    await createDoseRecordIdempotent({
      patientId: "patient-1",
      medicationId: "med-1",
      scheduledAt,
      recordedByType: "CAREGIVER",
      recordedById: "caregiver-1"
    });

    const deleted = await deleteDoseRecord({
      patientId: "patient-1",
      medicationId: "med-1",
      scheduledAt
    });
    expect(deleted?.patientId).toBe("patient-1");
    expect(store.size).toBe(0);

    const missing = await deleteDoseRecord({
      patientId: "patient-1",
      medicationId: "med-1",
      scheduledAt
    });
    expect(missing).toBeNull();
  });
});
