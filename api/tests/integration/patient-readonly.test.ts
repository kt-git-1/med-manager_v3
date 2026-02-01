import { describe, expect, it, vi } from "vitest";
import { GET as getSchedule } from "../../app/api/schedule/route";
import { PATCH as updateMedication } from "../../app/api/medications/[id]/route";

vi.mock("../../src/services/scheduleService", () => ({
  generateScheduleForPatient: () => [
    {
      patientId: "patient-1",
      medicationId: "med-1",
      scheduledAt: "2026-02-02T08:00:00.000Z",
      medicationSnapshot: {
        name: "Medication A",
        dosageText: "1 tablet",
        doseCountPerIntake: 1,
        dosageStrengthValue: 10,
        dosageStrengthUnit: "mg"
      }
    }
  ]
}));

vi.mock("../../src/services/medicationService", () => ({
  getMedication: vi.fn(async () => ({
    id: "med-1",
    patientId: "patient-1",
    name: "Medication A",
    startDate: new Date()
  })),
  updateMedication: vi.fn(async () => ({ id: "med-1" }))
}));

vi.mock("../../src/auth/patientSessionVerifier", () => ({
  patientSessionVerifier: {
    verify: vi.fn(async () => ({ patientId: "patient-1" }))
  }
}));

describe("patient read-only integration", () => {
  it("returns schedule for patient token", async () => {
    const request = new Request(
      "http://localhost/api/schedule?from=2026-02-01T00:00:00Z&to=2026-02-03T00:00:00Z",
      { headers: { authorization: "Bearer patient-1" } }
    );

    const response = await getSchedule(request);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.data).toHaveLength(1);
  });

  it("rejects patient updates", async () => {
    const request = new Request("http://localhost/api/medications/med-1", {
      method: "PATCH",
      headers: {
        authorization: "Bearer patient-1",
        "content-type": "application/json"
      },
      body: JSON.stringify({
        name: "Updated",
        startDate: "2026-02-01"
      })
    });

    const response = await updateMedication(request, { params: { id: "med-1" } });

    expect(response.status).toBe(403);
  });
});
