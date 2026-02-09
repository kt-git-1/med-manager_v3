import { describe, expect, it, vi } from "vitest";
import { GET as listMedications, POST as createMedication } from "../../app/api/medications/route";

vi.mock("../../src/services/medicationService", () => ({
  listMedications: vi.fn(async () => [
    { id: "med-1", patientId: "patient-1", name: "Medication A", startDate: new Date() }
  ]),
  listMedicationInventory: vi.fn(async () => []),
  listActiveRegimens: vi.fn(async () => []),
  createMedication: vi.fn(async () => ({ id: "med-1" }))
}));

vi.mock("../../src/services/scheduleService", () => ({
  generateScheduleForPatient: vi.fn(async () => [])
}));

vi.mock("../../src/auth/patientSessionVerifier", () => ({
  patientSessionVerifier: {
    verify: vi.fn(async () => ({ patientId: "patient-1" }))
  }
}));

describe("patient read-only contract", () => {
  it("allows patient to read medications list", async () => {
    const request = new Request("http://localhost/api/medications?patientId=patient-1", {
      headers: { authorization: "Bearer patient-1" }
    });

    const response = await listMedications(request);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.data).toHaveLength(1);
  });

  it("rejects patient write operations", async () => {
    const request = new Request("http://localhost/api/medications", {
      method: "POST",
      headers: {
        authorization: "Bearer patient-1",
        "content-type": "application/json"
      },
      body: JSON.stringify({
        patientId: "patient-1",
        name: "Medication A",
        startDate: "2026-02-01"
      })
    });

    const response = await createMedication(request);

    expect(response.status).toBe(403);
  });
});
