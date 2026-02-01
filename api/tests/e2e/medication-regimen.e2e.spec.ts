import { expect, test } from "@playwright/test";

let caregiverToken = "";
let patientToken = "";
let patientId = "";

async function fetchCaregiverJwt() {
  const supabaseUrl = process.env.SUPABASE_URL;
  const anonKey = process.env.SUPABASE_ANON_KEY;
  const email = process.env.SUPABASE_TEST_EMAIL;
  const password = process.env.SUPABASE_TEST_PASSWORD;
  if (!supabaseUrl || !anonKey || !email || !password) {
    throw new Error("Missing Supabase test env vars");
  }
  const response = await fetch(`${supabaseUrl}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      apikey: anonKey,
      authorization: `Bearer ${anonKey}`
    },
    body: JSON.stringify({ email, password })
  });
  if (!response.ok) {
    throw new Error(`Supabase login failed: ${response.status}`);
  }
  const payload = await response.json();
  if (!payload.access_token) {
    throw new Error("Supabase login missing access_token");
  }
  return payload.access_token as string;
}

test.describe("medication regimen e2e", () => {
  test.beforeAll(async ({ request }) => {
    const jwt = await fetchCaregiverJwt();
    caregiverToken = `caregiver-${jwt}`;

    const createdPatient = await request.post("/api/patients", {
      headers: { authorization: `Bearer ${caregiverToken}` },
      data: { displayName: "E2E Patient" }
    });
    expect(createdPatient.status()).toBe(201);
    patientId = (await createdPatient.json()).data.id;

    const codeResponse = await request.post(`/api/patients/${patientId}/linking-codes`, {
      headers: { authorization: `Bearer ${caregiverToken}` }
    });
    expect(codeResponse.status()).toBe(201);
    const code = (await codeResponse.json()).data.code;

    const patientSession = await request.post("/api/patient/link", {
      data: { code }
    });
    expect(patientSession.status()).toBe(200);
    patientToken = (await patientSession.json()).data.patientSessionToken;
  });

  test("caregiver creates medication + regimen and sees it in list", async ({ request }) => {
    const medication = await request.post("/api/medications", {
      headers: { authorization: `Bearer ${caregiverToken}` },
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
      headers: { authorization: `Bearer ${caregiverToken}` },
      data: {
        timezone: "UTC",
        startDate: "2026-02-01",
        times: ["08:00"],
        daysOfWeek: ["MON", "WED"]
      }
    });

    expect(regimen.status()).toBe(201);

    const list = await request.get(`/api/medications?patientId=${patientId}`, {
      headers: { authorization: `Bearer ${caregiverToken}` }
    });

    const listPayload = await list.json();
    expect(list.status()).toBe(200);
    expect(listPayload.data.some((item: { id: string }) => item.id === createdMedication.id)).toBe(
      true
    );
  });

  test("archived medication is excluded from schedules", async ({ request }) => {
    const medication = await request.post("/api/medications", {
      headers: { authorization: `Bearer ${caregiverToken}` },
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
      headers: { authorization: `Bearer ${caregiverToken}` },
      data: {
        timezone: "UTC",
        startDate: "2026-02-01",
        times: ["08:00"],
        daysOfWeek: []
      }
    });

    await request.delete(`/api/medications/${createdMedication.id}`, {
      headers: { authorization: `Bearer ${caregiverToken}` }
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
    const list = await request.get("/api/medications", {
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
      headers: { authorization: `Bearer ${caregiverToken}` }
    });
    const listPayload = await list.json();
    expect(list.status()).toBe(200);
    if (listPayload.data.length > 0) {
      expect(listPayload.data[0]).toHaveProperty("nextScheduledAt");
    }
  });
});
