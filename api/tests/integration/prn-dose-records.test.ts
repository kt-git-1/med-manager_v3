import { describe, expect, it, vi, beforeEach } from "vitest";
import { POST as createPrnDoseRecordRoute } from "../../app/api/patients/[patientId]/prn-dose-records/route";
import { DELETE as deletePrnDoseRecord } from "../../app/api/patients/[patientId]/prn-dose-records/[prnRecordId]/route";
import { createPrnRecord, deletePrnRecord } from "../../src/services/prnDoseRecordService";
import { getMedicationRecordForPatient } from "../../src/repositories/medicationRepo";
import {
  createPrnDoseRecord as createPrnDoseRecordRepo,
  deletePrnDoseRecordById,
  getPrnDoseRecordById
} from "../../src/repositories/prnDoseRecordRepo";

const ownedPatients = new Set<string>();

vi.mock("../../src/auth/supabaseJwt", () => ({
  verifySupabaseJwt: vi.fn(async () => ({ caregiverUserId: "caregiver-1" }))
}));

vi.mock("../../src/auth/patientSessionVerifier", () => ({
  patientSessionVerifier: {
    verify: vi.fn(async () => ({ patientId: "patient-1" }))
  }
}));

vi.mock("../../src/repositories/patientRepo", () => ({
  getPatientRecordForCaregiver: vi.fn(async (patientId: string) => {
    if (ownedPatients.has(patientId)) {
      return {
        id: patientId,
        caregiverId: "caregiver-1",
        displayName: "Care Recipient",
        createdAt: new Date(),
        updatedAt: new Date()
      };
    }
    return null;
  }),
  getPatientRecordById: vi.fn(async (patientId: string) => ({
    id: patientId,
    caregiverId: "caregiver-1",
    displayName: "Care Recipient",
    createdAt: new Date(),
    updatedAt: new Date()
  }))
}));

const applyInventoryDeltaMock = vi.fn();

vi.mock("../../src/services/medicationService", () => ({
  applyInventoryDeltaForDoseRecord: (...args: unknown[]) => applyInventoryDeltaMock(...args)
}));

vi.mock("../../src/repositories/medicationRepo", () => ({
  getMedicationRecordForPatient: vi.fn(async (_patientId: string, medicationId: string) => ({
    id: medicationId,
    patientId: "patient-1",
    name: "PRN Med",
    dosageText: "1 tablet",
    doseCountPerIntake: 2,
    dosageStrengthValue: 10,
    dosageStrengthUnit: "mg",
    notes: null,
    isPrn: true,
    startDate: new Date("2026-02-01T00:00:00.000Z"),
    endDate: null,
    inventoryCount: null,
    inventoryUnit: null,
    inventoryEnabled: true,
    inventoryQuantity: 10,
    inventoryLowThreshold: 2,
    inventoryUpdatedAt: null,
    inventoryLastAlertState: null,
    isActive: true,
    isArchived: false,
    createdAt: new Date(),
    updatedAt: new Date()
  }))
}));

vi.mock("../../src/repositories/prnDoseRecordRepo", () => ({
  createPrnDoseRecord: vi.fn(async () => ({
    id: "prn-1",
    patientId: "patient-1",
    medicationId: "med-1",
    takenAt: new Date("2026-02-02T00:10:00.000Z"),
    quantityTaken: 2,
    actorType: "PATIENT",
    createdAt: new Date("2026-02-02T00:10:00.000Z")
  })),
  getPrnDoseRecordById: vi.fn(async () => ({
    id: "prn-1",
    patientId: "patient-1",
    medicationId: "med-1",
    takenAt: new Date("2026-02-02T00:10:00.000Z"),
    quantityTaken: 2,
    actorType: "CAREGIVER",
    createdAt: new Date("2026-02-02T00:10:00.000Z")
  })),
  deletePrnDoseRecordById: vi.fn(async () => ({
    id: "prn-1",
    patientId: "patient-1",
    medicationId: "med-1",
    takenAt: new Date("2026-02-02T00:10:00.000Z"),
    quantityTaken: 2,
    actorType: "CAREGIVER",
    createdAt: new Date("2026-02-02T00:10:00.000Z")
  }))
}));

vi.mock("../../src/repositories/doseRecordEventRepo", () => ({
  createDoseRecordEvent: vi.fn(async () => ({
    id: "event-1",
    patientId: "patient-1",
    scheduledAt: new Date(),
    takenAt: new Date(),
    withinTime: true,
    displayName: "Care Recipient",
    medicationName: "PRN Med",
    isPrn: true,
    createdAt: new Date()
  }))
}));

describe("prn dose records integration", () => {
  beforeEach(() => {
    ownedPatients.clear();
    applyInventoryDeltaMock.mockReset();
  });

  it("allows patient to create PRN record but denies delete", async () => {
    (createPrnDoseRecordRepo as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      id: "prn-1",
      patientId: "patient-1",
      medicationId: "med-1",
      takenAt: new Date("2026-02-02T00:10:00.000Z"),
      quantityTaken: 2,
      actorType: "PATIENT",
      createdAt: new Date("2026-02-02T00:10:00.000Z")
    });
    const request = new Request("http://localhost/api/patients/patient-1/prn-dose-records", {
      method: "POST",
      headers: { authorization: "Bearer patient-token", "content-type": "application/json" },
      body: JSON.stringify({ medicationId: "med-1" })
    });
    const response = await createPrnDoseRecordRoute(request, {
      params: Promise.resolve({ patientId: "patient-1" })
    });
    const payload = await response.json();
    expect(response.status).toBe(200);
    expect(payload.record.medicationId).toBe("med-1");

    const deleteRequest = new Request(
      "http://localhost/api/patients/patient-1/prn-dose-records/prn-1",
      {
        method: "DELETE",
        headers: { authorization: "Bearer patient-token" }
      }
    );
    const deleteResponse = await deletePrnDoseRecord(deleteRequest, {
      params: Promise.resolve({ patientId: "patient-1", prnRecordId: "prn-1" })
    });
    expect(deleteResponse.status).toBe(403);
  });

  it("allows linked caregiver create/delete and conceals non-owned", async () => {
    ownedPatients.add("patient-1");
    (createPrnDoseRecordRepo as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      id: "prn-1",
      patientId: "patient-1",
      medicationId: "med-1",
      takenAt: new Date("2026-02-02T00:10:00.000Z"),
      quantityTaken: 2,
      actorType: "CAREGIVER",
      createdAt: new Date("2026-02-02T00:10:00.000Z")
    });
    (deletePrnDoseRecordById as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      id: "prn-1",
      patientId: "patient-1",
      medicationId: "med-1",
      takenAt: new Date("2026-02-02T00:10:00.000Z"),
      quantityTaken: 2,
      actorType: "CAREGIVER",
      createdAt: new Date("2026-02-02T00:10:00.000Z")
    });

    const createRequest = new Request("http://localhost/api/patients/patient-1/prn-dose-records", {
      method: "POST",
      headers: { authorization: "Bearer caregiver-valid", "content-type": "application/json" },
      body: JSON.stringify({ medicationId: "med-1" })
    });
    const createResponse = await createPrnDoseRecordRoute(createRequest, {
      params: Promise.resolve({ patientId: "patient-1" })
    });
    expect(createResponse.status).toBe(200);

    const deleteRequest = new Request(
      "http://localhost/api/patients/patient-1/prn-dose-records/prn-1",
      {
        method: "DELETE",
        headers: { authorization: "Bearer caregiver-valid" }
      }
    );
    const deleteResponse = await deletePrnDoseRecord(deleteRequest, {
      params: Promise.resolve({ patientId: "patient-1", prnRecordId: "prn-1" })
    });
    expect(deleteResponse.status).toBe(204);

    const concealedRequest = new Request(
      "http://localhost/api/patients/patient-999/prn-dose-records/prn-1",
      {
        method: "DELETE",
        headers: { authorization: "Bearer caregiver-valid" }
      }
    );
    const concealedResponse = await deletePrnDoseRecord(concealedRequest, {
      params: Promise.resolve({ patientId: "patient-999", prnRecordId: "prn-1" })
    });
    expect(concealedResponse.status).toBe(404);
  });

  it("adjusts inventory on create/delete when enabled", async () => {
    applyInventoryDeltaMock.mockClear();
    const created = await createPrnRecord({
      patientId: "patient-1",
      medicationId: "med-1",
      actorType: "PATIENT"
    });
    expect(created && "record" in created).toBe(true);
    expect(applyInventoryDeltaMock).toHaveBeenCalledWith(
      expect.objectContaining({ delta: -2, reason: "TAKEN_CREATE" })
    );

    await deletePrnRecord({ patientId: "patient-1", prnRecordId: "prn-1" });
    expect(applyInventoryDeltaMock).toHaveBeenCalledWith(
      expect.objectContaining({ delta: 2, reason: "TAKEN_DELETE" })
    );
  });

  it("rejects create when medication is not PRN", async () => {
    (getMedicationRecordForPatient as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      id: "med-1",
      patientId: "patient-1",
      name: "Regular Med",
      dosageText: "1 tablet",
      doseCountPerIntake: 1,
      dosageStrengthValue: 10,
      dosageStrengthUnit: "mg",
      notes: null,
      isPrn: false,
      startDate: new Date("2026-02-01T00:00:00.000Z"),
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
    });
    const request = new Request("http://localhost/api/patients/patient-1/prn-dose-records", {
      method: "POST",
      headers: { authorization: "Bearer patient-token", "content-type": "application/json" },
      body: JSON.stringify({ medicationId: "med-1" })
    });
    const response = await createPrnDoseRecordRoute(request, {
      params: Promise.resolve({ patientId: "patient-1" })
    });
    const payload = await response.json();
    expect(response.status).toBe(422);
    expect(payload.messages).toContain("medication must be PRN");
  });
});
