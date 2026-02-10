import { describe, expect, it } from "vitest";
import { LINKING_CODE_LENGTH, LINKING_CODE_TTL_MINUTES } from "../../src/services/linkingConstants";
import { validatePatientCreate } from "../../src/validators/patient";

type Patient = { id: string; displayName: string };

function jsonResponse(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "content-type": "application/json" }
  });
}

async function createPatient(request: Request, store: Patient[]) {
  const body = await request.json();
  const { errors, displayName } = validatePatientCreate({ displayName: body.displayName });
  if (errors.length) {
    return jsonResponse({ error: "validation", messages: errors }, 422);
  }
  const created = { id: "patient-1", displayName };
  store.push(created);
  return jsonResponse({ data: created }, 201);
}

async function listPatients(store: Patient[]) {
  return jsonResponse({ data: store });
}

async function issueLinkingCode(patientId: string, store: Patient[]) {
  const patient = store.find((entry) => entry.id === patientId);
  if (!patient) {
    return jsonResponse({ error: "not_found" }, 404);
  }
  const expiresAt = new Date(Date.now() + LINKING_CODE_TTL_MINUTES * 60 * 1000).toISOString();
  const code = "1".repeat(LINKING_CODE_LENGTH);
  return jsonResponse({ data: { code, expiresAt } }, 201);
}

describe("patients contract", () => {
  it("creates patient with displayName", async () => {
    const store: Patient[] = [];
    const request = new Request("http://localhost/api/patients", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ displayName: "Care Recipient" })
    });

    const response = await createPatient(request, store);
    const payload = await response.json();

    expect(response.status).toBe(201);
    expect(payload.data).toEqual({ id: "patient-1", displayName: "Care Recipient" });
  });

  it("rejects blank displayName", async () => {
    const store: Patient[] = [];
    const request = new Request("http://localhost/api/patients", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ displayName: "   " })
    });

    const response = await createPatient(request, store);
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.error).toBe("validation");
    expect(payload.messages).toContain("displayName is required");
  });

  it("lists patients for caregiver", async () => {
    const store: Patient[] = [{ id: "patient-1", displayName: "Care Recipient" }];
    const response = await listPatients(store);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.data).toHaveLength(1);
    expect(payload.data[0]).toEqual(store[0]);
  });

  it("issues linking code for known patient", async () => {
    const store: Patient[] = [{ id: "patient-1", displayName: "Care Recipient" }];
    const response = await issueLinkingCode("patient-1", store);
    const payload = await response.json();

    expect(response.status).toBe(201);
    expect(payload.data.code).toHaveLength(LINKING_CODE_LENGTH);
    expect(new Date(payload.data.expiresAt).getTime()).toBeGreaterThan(Date.now());
  });

  it("returns 404 when patient not found", async () => {
    const store: Patient[] = [];
    const response = await issueLinkingCode("missing", store);
    const payload = await response.json();

    expect(response.status).toBe(404);
    expect(payload.error).toBe("not_found");
  });
});

// ---------------------------------------------------------------------------
// PATIENT_LIMIT_EXCEEDED error contract (009-free-limit-gates)
// ---------------------------------------------------------------------------

describe("PATIENT_LIMIT_EXCEEDED contract", () => {
  function patientLimitExceededResponse(limit: number, current: number) {
    return jsonResponse(
      {
        code: "PATIENT_LIMIT_EXCEEDED",
        message: "Patient limit reached. Upgrade to premium for unlimited patients.",
        limit,
        current
      },
      403
    );
  }

  it("returns HTTP 403 status", async () => {
    const response = patientLimitExceededResponse(1, 1);
    expect(response.status).toBe(403);
  });

  it("response body contains code: PATIENT_LIMIT_EXCEEDED", async () => {
    const response = patientLimitExceededResponse(1, 1);
    const payload = await response.json();
    expect(payload.code).toBe("PATIENT_LIMIT_EXCEEDED");
  });

  it("response body contains limit as integer", async () => {
    const response = patientLimitExceededResponse(1, 2);
    const payload = await response.json();
    expect(payload.limit).toBe(1);
    expect(typeof payload.limit).toBe("number");
    expect(Number.isInteger(payload.limit)).toBe(true);
  });

  it("response body contains current as integer", async () => {
    const response = patientLimitExceededResponse(1, 3);
    const payload = await response.json();
    expect(payload.current).toBe(3);
    expect(typeof payload.current).toBe("number");
    expect(Number.isInteger(payload.current)).toBe(true);
  });

  it("response body contains message as string", async () => {
    const response = patientLimitExceededResponse(1, 1);
    const payload = await response.json();
    expect(typeof payload.message).toBe("string");
    expect(payload.message.length).toBeGreaterThan(0);
  });
});
