import { expect, test } from "@playwright/test";

let caregiverToken = "";
let patientToken = "";
let patientId = "";
let linkExchangeRequestCount = 0;

const hasSupabaseTestEnv = Boolean(
  process.env.SUPABASE_URL &&
  process.env.SUPABASE_ANON_KEY &&
  process.env.SUPABASE_TEST_EMAIL &&
  process.env.SUPABASE_TEST_PASSWORD
);
const patientNamePrefix =
  process.env.SUPABASE_TEST_ALLOW_DESTRUCTIVE_LINKING === "true"
    ? "E2E Medication Patient"
    : "E2E Shared Patient";

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

async function listPatients(request: Parameters<typeof test>[0]["request"]) {
  const response = await request.get("/api/patients", {
    headers: { authorization: `Bearer ${caregiverToken}` }
  });
  expect(response.status()).toBe(200);
  return (await response.json()).data as { id: string; displayName: string }[];
}

async function getOrCreateSharedPatient(request: Parameters<typeof test>[0]["request"]) {
  const existing = (await listPatients(request)).find((patient) =>
    patient.displayName.startsWith(patientNamePrefix)
  );
  if (existing) {
    return existing.id;
  }

  const response = await request.post("/api/patients", {
    headers: { authorization: `Bearer ${caregiverToken}` },
    data: { displayName: `${patientNamePrefix} ${Date.now()}` }
  });
  if (response.status() === 201) {
    return (await response.json()).data.id as string;
  }

  if (response.status() === 403) {
    const patients = await listPatients(request);
    const fallback = patients[0];
    if (fallback) {
      return fallback.id;
    }
  }

  expect(response.status()).toBe(201);
  throw new Error("Unable to create or reuse E2E patient");
}

const dayOfWeekByIndex = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"] as const;
const dayOfWeekByShortName: Record<string, (typeof dayOfWeekByIndex)[number]> = {
  Sun: "SUN",
  Mon: "MON",
  Tue: "TUE",
  Wed: "WED",
  Thu: "THU",
  Fri: "FRI",
  Sat: "SAT"
};

function futureTokyoScheduleTarget() {
  const target = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000);
  const date = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Tokyo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).format(target);
  const weekdayShortName = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Tokyo",
    weekday: "short"
  }).format(target);
  const from = `${date}T00:00:00+09:00`;
  const to = `${date}T23:59:59+09:00`;
  return { date, from, to, dayOfWeek: dayOfWeekByShortName[weekdayShortName] };
}

test.describe("medication regimen e2e", () => {
  test.skip(
    !hasSupabaseTestEnv,
    "Set SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_TEST_EMAIL, and SUPABASE_TEST_PASSWORD to run Supabase-backed E2E tests."
  );

  test.beforeAll(async ({ request }) => {
    const jwt = await fetchCaregiverJwt();
    caregiverToken = `caregiver-${jwt}`;

    patientId = await getOrCreateSharedPatient(request);

    const codeResponse = await request.post(`/api/patients/${patientId}/linking-codes`, {
      headers: { authorization: `Bearer ${caregiverToken}` }
    });
    expect(codeResponse.status()).toBe(201);
    const code = (await codeResponse.json()).data.code;

    linkExchangeRequestCount += 1;
    const patientSession = await request.post("/api/patient/link", {
      headers: { "x-forwarded-for": `10.30.40.${linkExchangeRequestCount}` },
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
        timezone: "Asia/Tokyo",
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
    const target = futureTokyoScheduleTarget();
    const activeMedication = await request.post("/api/medications", {
      headers: { authorization: `Bearer ${caregiverToken}` },
      data: {
        patientId,
        name: `E2E Active Schedule Medication ${Date.now()}`,
        dosageText: "1 tablet",
        doseCountPerIntake: 1,
        dosageStrengthValue: 10,
        dosageStrengthUnit: "mg",
        startDate: target.date
      }
    });
    expect(activeMedication.status()).toBe(201);
    const createdActiveMedication = (await activeMedication.json()).data;

    const medication = await request.post("/api/medications", {
      headers: { authorization: `Bearer ${caregiverToken}` },
      data: {
        patientId,
        name: `E2E Archived Medication ${Date.now()}`,
        dosageText: "1 tablet",
        doseCountPerIntake: 1,
        dosageStrengthValue: 10,
        dosageStrengthUnit: "mg",
        startDate: target.date
      }
    });
    expect(medication.status()).toBe(201);

    const createdMedication = (await medication.json()).data;

    const activeRegimen = await request.post(
      `/api/medications/${createdActiveMedication.id}/regimens`,
      {
        headers: { authorization: `Bearer ${caregiverToken}` },
        data: {
          timezone: "Asia/Tokyo",
          startDate: target.date,
          times: ["23:50"],
          daysOfWeek: [target.dayOfWeek]
        }
      }
    );
    expect(activeRegimen.status()).toBe(201);

    await request.post(`/api/medications/${createdMedication.id}/regimens`, {
      headers: { authorization: `Bearer ${caregiverToken}` },
      data: {
        timezone: "Asia/Tokyo",
        startDate: target.date,
        times: ["23:50"],
        daysOfWeek: [target.dayOfWeek]
      }
    });

    const archive = await request.delete(
      `/api/medications/${createdMedication.id}?patientId=${patientId}`,
      {
        headers: { authorization: `Bearer ${caregiverToken}` }
      }
    );
    expect(archive.status()).toBe(204);

    const schedule = await request.get(
      `/api/schedule?from=${encodeURIComponent(target.from)}&to=${encodeURIComponent(target.to)}`,
      { headers: { authorization: `Bearer ${patientToken}` } }
    );

    const payload = await schedule.json();
    expect(schedule.status()).toBe(200);
    expect(
      payload.data.some(
        (dose: { medicationId: string }) => dose.medicationId === createdActiveMedication.id
      )
    ).toBe(true);
    expect(
      payload.data.every(
        (dose: { medicationId: string }) => dose.medicationId !== createdMedication.id
      )
    ).toBe(true);

    const archivedMedication = await request.get(`/api/medications/${createdMedication.id}`, {
      headers: { authorization: `Bearer ${caregiverToken}` }
    });
    expect(archivedMedication.status()).toBe(200);
    expect((await archivedMedication.json()).data.isArchived).toBe(true);
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
    const target = futureTokyoScheduleTarget();
    const medication = await request.post("/api/medications", {
      headers: { authorization: `Bearer ${caregiverToken}` },
      data: {
        patientId,
        name: `E2E Scheduled Medication ${Date.now()}`,
        dosageText: "1 tablet",
        doseCountPerIntake: 1,
        dosageStrengthValue: 10,
        dosageStrengthUnit: "mg",
        startDate: target.date
      }
    });
    expect(medication.status()).toBe(201);
    const createdMedication = (await medication.json()).data;

    const regimen = await request.post(`/api/medications/${createdMedication.id}/regimens`, {
      headers: { authorization: `Bearer ${caregiverToken}` },
      data: {
        timezone: "Asia/Tokyo",
        startDate: target.date,
        times: ["23:50"],
        daysOfWeek: [target.dayOfWeek]
      }
    });
    expect(regimen.status()).toBe(201);

    const schedule = await request.get(
      `/api/schedule?from=${encodeURIComponent(target.from)}&to=${encodeURIComponent(target.to)}`,
      { headers: { authorization: `Bearer ${patientToken}` } }
    );

    const payload = await schedule.json();
    expect(schedule.status()).toBe(200);
    expect(payload.data.length).toBeGreaterThan(0);
    const createdDose = payload.data.find(
      (dose: { medicationId: string }) => dose.medicationId === createdMedication.id
    );
    expect(createdDose).toBeTruthy();
    expect(createdDose).toHaveProperty("scheduledAt");
    expect(createdDose).toHaveProperty("medicationSnapshot");

    const list = await request.get(`/api/medications?patientId=${patientId}`, {
      headers: { authorization: `Bearer ${caregiverToken}` }
    });
    const listPayload = await list.json();
    expect(list.status()).toBe(200);
    const listedMedication = listPayload.data.find(
      (item: { id: string }) => item.id === createdMedication.id
    );
    expect(listedMedication).toBeTruthy();
    expect(listedMedication.nextScheduledAt).toBeTruthy();
  });
});
