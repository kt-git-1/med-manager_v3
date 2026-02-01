import { describe, expect, it, vi } from "vitest";
import { GET } from "../../app/api/schedule/route";

vi.mock("../../src/services/scheduleService", () => ({
  generateScheduleForPatient: () => [
    {
      key: "patient-1:med-1:2026-02-02T08:00:00.000Z",
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

describe("schedule range integration", () => {
  it("returns 401 when authorization is missing", async () => {
    const request = new Request("http://localhost/api/schedule?from=2026-02-01&to=2026-02-02");
    const response = await GET(request);

    expect(response.status).toBe(401);
  });

  it("returns 422 when from/to are missing", async () => {
    const request = new Request("http://localhost/api/schedule", {
      headers: { authorization: "Bearer patient-1" }
    });
    const response = await GET(request);

    expect(response.status).toBe(422);
  });

  it("returns scheduled doses for valid range", async () => {
    const request = new Request(
      "http://localhost/api/schedule?from=2026-02-01T00:00:00Z&to=2026-02-03T00:00:00Z",
      { headers: { authorization: "Bearer patient-1" } }
    );
    const response = await GET(request);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload).toEqual({
      data: [
        {
          key: "patient-1:med-1:2026-02-02T08:00:00.000Z",
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
    });
  });
});
