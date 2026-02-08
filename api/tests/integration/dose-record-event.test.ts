import { describe, expect, it, vi } from "vitest";
import { createDoseRecordIdempotent } from "../../src/services/doseRecordService";

const store = new Map<string, any>();
const createDoseRecordEventMock = vi.fn();

vi.mock("../../src/repositories/doseRecordRepo", () => ({
  upsertDoseRecord: async (input: {
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
    const now = new Date("2026-02-02T08:10:00.000Z");
    const record = {
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
    const lookupKey = `${key.patientId}:${key.medicationId}:${key.scheduledAt.toISOString()}`;
    return store.get(lookupKey) ?? null;
  }
}));

vi.mock("../../src/repositories/patientRepo", () => ({
  getPatientRecordById: async () => ({
    id: "patient-1",
    caregiverId: "caregiver-1",
    displayName: "Test Patient",
    createdAt: new Date(),
    updatedAt: new Date()
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
    isPrn: false,
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

vi.mock("../../src/repositories/doseRecordEventRepo", () => ({
  createDoseRecordEvent: (...args: unknown[]) => createDoseRecordEventMock(...args)
}));

vi.mock("../../src/services/medicationService", () => ({
  applyInventoryDeltaForDoseRecord: vi.fn(async () => {})
}));

describe("dose record event emission", () => {
  it("creates an event when a TAKEN record is created", async () => {
    store.clear();
    createDoseRecordEventMock.mockReset();
    const scheduledAt = new Date("2026-02-02T08:00:00.000Z");

    await createDoseRecordIdempotent({
      patientId: "patient-1",
      medicationId: "med-1",
      scheduledAt,
      recordedByType: "PATIENT",
      recordedById: null
    });

    expect(createDoseRecordEventMock).toHaveBeenCalledTimes(1);
    const [payload] = createDoseRecordEventMock.mock.calls[0];
    expect(payload).toEqual(
      expect.objectContaining({
        patientId: "patient-1",
        scheduledAt,
        withinTime: true,
        displayName: "Test Patient",
        medicationName: "Medication A",
        isPrn: false
      })
    );
  });
});
