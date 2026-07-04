import { expect, test } from "@playwright/test";
import { LINKING_CODE_MAX_ATTEMPTS } from "../../src/services/linkingConstants";

let caregiverToken = "";
let sharedPatientId = "";
let linkExchangeRequestCount = 0;

const hasSupabaseTestEnv = Boolean(
  process.env.SUPABASE_URL &&
  process.env.SUPABASE_ANON_KEY &&
  process.env.SUPABASE_TEST_EMAIL &&
  process.env.SUPABASE_TEST_PASSWORD
);
const patientNamePrefix =
  process.env.SUPABASE_TEST_ALLOW_DESTRUCTIVE_LINKING === "true"
    ? "E2E Family Patient"
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

async function issueLinkingCode(request: Parameters<typeof test>[0]["request"], patientId: string) {
  const response = await request.post(`/api/patients/${patientId}/linking-codes`, {
    headers: { authorization: `Bearer ${caregiverToken}` }
  });
  expect(response.status()).toBe(201);
  return (await response.json()).data.code as string;
}

async function exchangeCode(request: Parameters<typeof test>[0]["request"], code: string) {
  linkExchangeRequestCount += 1;
  return request.post("/api/patient/link", {
    headers: { "x-forwarded-for": `10.20.30.${linkExchangeRequestCount}` },
    data: { code }
  });
}

async function refreshPatientToken(request: Parameters<typeof test>[0]["request"], token: string) {
  return request.post("/api/patient/session/refresh", {
    headers: { authorization: `Bearer ${token}` }
  });
}

test.describe("family linking e2e", () => {
  test.describe.configure({ mode: "serial" });

  test.skip(
    !hasSupabaseTestEnv,
    "Set SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_TEST_EMAIL, and SUPABASE_TEST_PASSWORD to run Supabase-backed E2E tests."
  );

  test.beforeAll(async ({ request }) => {
    const jwt = await fetchCaregiverJwt();
    caregiverToken = `caregiver-${jwt}`;
    sharedPatientId = await getOrCreateSharedPatient(request);
  });

  test("caregiver creates patient and sees list entry", async ({ request }) => {
    const list = await request.get("/api/patients", {
      headers: { authorization: `Bearer ${caregiverToken}` }
    });
    expect(list.status()).toBe(200);
    const payload = await list.json();
    expect(payload.data.some((patient: { id: string }) => patient.id === sharedPatientId)).toBe(
      true
    );
  });

  test("issue code and patient can read medications", async ({ request }) => {
    const code = await issueLinkingCode(request, sharedPatientId);
    const session = await exchangeCode(request, code);
    expect(session.status()).toBe(200);
    const token = (await session.json()).data.patientSessionToken as string;
    const list = await request.get("/api/medications", {
      headers: { authorization: `Bearer ${token}` }
    });
    expect(list.status()).toBe(200);
  });

  test("refresh rotates token and old token is invalid", async ({ request }) => {
    const code = await issueLinkingCode(request, sharedPatientId);
    const session = await exchangeCode(request, code);
    expect(session.status()).toBe(200);
    const token = (await session.json()).data.patientSessionToken as string;
    const refreshed = await refreshPatientToken(request, token);
    expect(refreshed.status()).toBe(200);
    const nextToken = (await refreshed.json()).data.patientSessionToken as string;
    expect(nextToken).not.toBe(token);

    const oldRefresh = await refreshPatientToken(request, token);
    expect(oldRefresh.status()).toBe(401);
  });

  test("revoke invalidates token for read and refresh", async ({ request }) => {
    test.skip(
      process.env.SUPABASE_TEST_ALLOW_DESTRUCTIVE_LINKING !== "true",
      "Set SUPABASE_TEST_ALLOW_DESTRUCTIVE_LINKING=true when the test account can safely lose its active patient link."
    );

    const code = await issueLinkingCode(request, sharedPatientId);
    const session = await exchangeCode(request, code);
    expect(session.status()).toBe(200);
    const token = (await session.json()).data.patientSessionToken as string;

    const revoke = await request.post(`/api/patients/${sharedPatientId}/revoke`, {
      headers: { authorization: `Bearer ${caregiverToken}` }
    });
    expect(revoke.status()).toBe(200);

    const list = await request.get("/api/medications", {
      headers: { authorization: `Bearer ${token}` }
    });
    expect(list.status()).toBe(401);

    const refresh = await refreshPatientToken(request, token);
    expect(refresh.status()).toBe(401);
  });

  test("used or invalid codes return 404 and lockout triggers", async ({ request }) => {
    const code = await issueLinkingCode(request, sharedPatientId);
    const session = await exchangeCode(request, code);
    expect(session.status()).toBe(200);

    const invalid = await exchangeCode(request, "123456");
    expect(invalid.status()).toBe(404);

    for (let attempt = 0; attempt < LINKING_CODE_MAX_ATTEMPTS - 1; attempt += 1) {
      const reuse = await exchangeCode(request, code);
      expect(reuse.status()).toBe(404);
    }

    const lockout = await exchangeCode(request, code);
    expect(lockout.status()).toBe(429);
  });
});
