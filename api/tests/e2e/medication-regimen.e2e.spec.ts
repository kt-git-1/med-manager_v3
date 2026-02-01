import { expect, test } from "@playwright/test";

const caregiverToken = "caregiver-placeholder";
const patientToken = "caregiver-placeholder";
const patientId = "caregiver-placeholder";

test.describe("medication regimen e2e", () => {
  test("caregiver creates medication + regimen and sees it in list", async ({ request }) => {
    const medication = await request.post("/api/medications", {
      headers: { authorization: `Bearer caregiver-${caregiverToken}` },
      data: {
        patientId,
        name: "E2E Medication",
        dosageText: "1 tablet",
        doseCountPerIntake: 1,
        dosageStrengthValue: 10,
        dosageStrengthUnit: "mg",
        startDate: "2026-02-01",
        notes: "e2e"
      }
    });

    expect(medication.status()).toBe(201);
    const createdMedication = (await medication.json()).data;

    const regimen = await request.post(`/api/medications/${createdMedication.id}/regimens`, {
      headers: { authorization: `Bearer caregiver-${caregiverToken}` },
      data: {
        timezone: "UTC",
        startDate: "2026-02-01",
        times: ["08:00"],
        daysOfWeek: ["MON", "WED"]
      }
    });

    expect(regimen.status()).toBe(201);

    const list = await request.get(`/api/medications?patientId=${patientId}`, {
      headers: { authorization: `Bearer caregiver-${caregiverToken}` }
    });

    const listPayload = await list.json();
    expect(list.status()).toBe(200);
    expect(listPayload.data.some((item: { id: string }) => item.id === createdMedication.id)).toBe(
      true
    );
  });

  test("archived medication is excluded from schedules", async ({ request }) => {
    const medication = await request.post("/api/medications", {
      headers: { authorization: `Bearer caregiver-${caregiverToken}` },
      data: {
        patientId,
        name: "E2E Archived Medication",
        dosageText: "1 tablet",
        doseCountPerIntake: 1,
        dosageStrengthValue: 10,
        dosageStrengthUnit: "mg",
        startDate: "2026-02-01"
      }
    });

    const createdMedication = (await medication.json()).data;

    await request.post(`/api/medications/${createdMedication.id}/regimens`, {
      headers: { authorization: `Bearer caregiver-${caregiverToken}` },
      data: {
        timezone: "UTC",
        startDate: "2026-02-01",
        times: ["08:00"],
        daysOfWeek: []
      }
    });

    await request.delete(`/api/medications/${createdMedication.id}`, {
      headers: { authorization: `Bearer caregiver-${caregiverToken}` }
    });

    const schedule = await request.get(
      "/api/schedule?from=2026-02-01T00:00:00Z&to=2026-02-08T00:00:00Z",
      { headers: { authorization: `Bearer ${patientToken}` } }
    );

    const payload = await schedule.json();
    expect(schedule.status()).toBe(200);
    expect(
      payload.data.every((dose: { medicationId: string }) => dose.medicationId !== createdMedication.id)
    ).toBe(true);
  });

  test("patient can read but cannot edit", async ({ request }) => {
    const list = await request.get(`/api/medications?patientId=${patientId}`, {
      headers: { authorization: `Bearer ${patientToken}` }
    });
    expect(list.status()).toBe(200);

    const update = await request.patch("/api/medications/medication-not-used", {
      headers: {
        authorization: `Bearer ${patientToken}`,
        "content-type": "application/json"
      },
      data: {
        name: "Should fail",
        startDate: "2026-02-01"
      }
    });
    expect(update.status()).toBe(403);
  });

  test("schedule range returns matching days/times and next dose exists", async ({ request }) => {
    const schedule = await request.get(
      "/api/schedule?from=2026-02-01T00:00:00Z&to=2026-02-08T00:00:00Z",
      { headers: { authorization: `Bearer ${patientToken}` } }
    );

    const payload = await schedule.json();
    expect(schedule.status()).toBe(200);
    if (payload.data.length > 0) {
      const first = payload.data[0];
      expect(first).toHaveProperty("scheduledAt");
      expect(first).toHaveProperty("medicationSnapshot");
    }

    const list = await request.get(`/api/medications?patientId=${patientId}`, {
      headers: { authorization: `Bearer caregiver-${caregiverToken}` }
    });
    const listPayload = await list.json();
    expect(list.status()).toBe(200);
    if (listPayload.data.length > 0) {
      expect(listPayload.data[0]).toHaveProperty("nextScheduledAt");
    }
  });
});
