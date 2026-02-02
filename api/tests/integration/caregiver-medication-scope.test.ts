import { beforeEach, describe, expect, it, vi } from "vitest";
import { GET as listMedications, POST as createMedication } from "../../app/api/medications/route";

const ownedPatients = new Set<string>();
const medicationsByPatient = new Map<string, any[]>();
let medicationCount = 0;

vi.mock("../../src/auth/supabaseJwt", () => ({
  verifySupabaseJwt: vi.fn(async () => ({ caregiverUserId: "caregiver-1" }))
}));

vi.mock("../../src/repositories/patientRepo", () => ({
  getPatientRecordForCaregiver: vi.fn(async (patientId: string, caregiverId: string) => {
    if (caregiverId === "caregiver-1" && ownedPatients.has(patientId)) {
      return { id: patientId, caregiverId, displayName: "Care Recipient", createdAt: new Date(), updatedAt: new Date() };
    }
    return null;
  })
}));

vi.mock("../../src/services/medicationService", () => ({
  createMedication: vi.fn(async (input: any) => {
    const id = `med-${++medicationCount}`;
    const record = {
      id,
      patientId: input.patientId,
      name: input.name,
      dosageText: input.dosageText,
      doseCountPerIntake: input.doseCountPerIntake,
      dosageStrengthValue: input.dosageStrengthValue,
      dosageStrengthUnit: input.dosageStrengthUnit,
      notes: input.notes ?? null,
      startDate: input.startDate,
      endDate: input.endDate ?? null,
      inventoryCount: input.inventoryCount ?? null,
      inventoryUnit: input.inventoryUnit ?? null,
      isActive: true,
      isArchived: false
    };
    const bucket = medicationsByPatient.get(input.patientId) ?? [];
    bucket.unshift(record);
    medicationsByPatient.set(input.patientId, bucket);
    return record;
  }),
  listMedications: vi.fn(async (patientId: string) => medicationsByPatient.get(patientId) ?? [])
}));

vi.mock("../../src/services/scheduleService", () => ({
  generateScheduleForPatient: vi.fn(async () => [])
}));

function buildMedicationPayload(patientId: string, name: string) {
  return {
    patientId,
    name,
    dosageText: "1 tablet",
    doseCountPerIntake: 1,
    dosageStrengthValue: 10,
    dosageStrengthUnit: "mg",
    startDate: "2026-02-01",
    endDate: null,
    notes: null,
    inventoryCount: null,
    inventoryUnit: null
  };
}

describe("caregiver medication scope integration", () => {
  beforeEach(() => {
    ownedPatients.clear();
    medicationsByPatient.clear();
    medicationCount = 0;
  });

  it("scopes caregiver list by patientId", async () => {
    ownedPatients.add("patient-1");
    ownedPatients.add("patient-2");

    const createRequestA = new Request("http://localhost/api/medications", {
      method: "POST",
      headers: { authorization: "Bearer caregiver-valid", "content-type": "application/json" },
      body: JSON.stringify(buildMedicationPayload("patient-1", "Medication A"))
    });
    await createMedication(createRequestA);

    const createRequestB = new Request("http://localhost/api/medications", {
      method: "POST",
      headers: { authorization: "Bearer caregiver-valid", "content-type": "application/json" },
      body: JSON.stringify(buildMedicationPayload("patient-2", "Medication B"))
    });
    await createMedication(createRequestB);

    const listRequest = new Request("http://localhost/api/medications?patientId=patient-1", {
      headers: { authorization: "Bearer caregiver-valid" }
    });
    const listResponse = await listMedications(listRequest);
    const listPayload = await listResponse.json();

    expect(listResponse.status).toBe(200);
    expect(listPayload.data).toHaveLength(1);
    expect(listPayload.data[0].name).toBe("Medication A");
    expect(listPayload.data[0].patientId).toBe("patient-1");
  });

  it("returns 422 for missing patientId and 404 for non-owned patientId", async () => {
    ownedPatients.add("patient-1");

    const missingRequest = new Request("http://localhost/api/medications", {
      headers: { authorization: "Bearer caregiver-valid" }
    });
    const missingResponse = await listMedications(missingRequest);
    const missingPayload = await missingResponse.json();

    expect(missingResponse.status).toBe(422);
    expect(missingPayload.message).toBe("patientId required");

    const notOwnedRequest = new Request("http://localhost/api/medications?patientId=patient-999", {
      headers: { authorization: "Bearer caregiver-valid" }
    });
    const notOwnedResponse = await listMedications(notOwnedRequest);
    const notOwnedPayload = await notOwnedResponse.json();

    expect(notOwnedResponse.status).toBe(404);
    expect(notOwnedPayload.error).toBe("not_found");
  });
});
