import { describe, expect, it } from "vitest";
import { isCaregiverToken } from "../../src/middleware/auth";

type Medication = { id: string; patientId: string; name: string };

function jsonResponse(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "content-type": "application/json" }
  });
}

function parseBearerToken(authHeader?: string | null) {
  if (!authHeader) return null;
  const [scheme, token] = authHeader.split(" ");
  if (scheme !== "Bearer" || !token) {
    return null;
  }
  return token;
}

async function listMedications(
  request: Request,
  caregiverPatientIds: Set<string>,
  store: Medication[]
) {
  const { searchParams } = new URL(request.url);
  const patientId = searchParams.get("patientId");
  const token = parseBearerToken(request.headers.get("authorization"));
  if (isCaregiverToken(token) && !patientId) {
    return jsonResponse({ error: "validation", message: "patientId required" }, 422);
  }
  if (patientId && !caregiverPatientIds.has(patientId)) {
    return jsonResponse({ error: "not_found" }, 404);
  }
  const filtered = patientId ? store.filter((med) => med.patientId === patientId) : store;
  return jsonResponse({ data: filtered });
}

async function createMedication(
  request: Request,
  caregiverPatientIds: Set<string>,
  store: Medication[]
) {
  const body = await request.json();
  if (!body.patientId) {
    return jsonResponse({ error: "validation", message: "patientId required" }, 422);
  }
  if (!caregiverPatientIds.has(body.patientId)) {
    return jsonResponse({ error: "not_found" }, 404);
  }
  const created = { id: `med-${store.length + 1}`, patientId: body.patientId, name: body.name };
  store.push(created);
  return jsonResponse({ data: created }, 201);
}

async function updateMedication(
  request: Request,
  caregiverPatientIds: Set<string>,
  store: Medication[]
) {
  const { searchParams } = new URL(request.url);
  const patientId = searchParams.get("patientId");
  if (!patientId) {
    return jsonResponse({ error: "validation", message: "patientId required" }, 422);
  }
  if (!caregiverPatientIds.has(patientId)) {
    return jsonResponse({ error: "not_found" }, 404);
  }
  const body = await request.json();
  const existing = store.find((med) => med.id === body.id);
  if (!existing || existing.patientId !== patientId) {
    return jsonResponse({ error: "not_found" }, 404);
  }
  existing.name = body.name ?? existing.name;
  return jsonResponse({ data: existing });
}

describe("medications contract", () => {
  it("requires patientId for caregiver list requests", async () => {
    const request = new Request("http://localhost/api/medications", {
      headers: { authorization: "Bearer caregiver-1" }
    });
    const response = await listMedications(request, new Set(["patient-1"]), []);
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.message).toBe("patientId required");
  });

  it("requires patientId for caregiver create requests", async () => {
    const request = new Request("http://localhost/api/medications", {
      method: "POST",
      headers: { authorization: "Bearer caregiver-1", "content-type": "application/json" },
      body: JSON.stringify({ name: "Medication A" })
    });
    const response = await createMedication(request, new Set(["patient-1"]), []);
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.message).toBe("patientId required");
  });

  it("requires patientId for caregiver update requests", async () => {
    const request = new Request("http://localhost/api/medications/med-1", {
      method: "PATCH",
      headers: { authorization: "Bearer caregiver-1", "content-type": "application/json" },
      body: JSON.stringify({ id: "med-1", name: "Updated" })
    });
    const response = await updateMedication(request, new Set(["patient-1"]), [
      { id: "med-1", patientId: "patient-1", name: "Medication A" }
    ]);
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.message).toBe("patientId required");
  });

  it("returns 404 for non-owned patientId", async () => {
    const request = new Request("http://localhost/api/medications?patientId=patient-2", {
      headers: { authorization: "Bearer caregiver-1" }
    });
    const response = await listMedications(
      request,
      new Set(["patient-1"]),
      [{ id: "med-1", patientId: "patient-1", name: "Medication A" }]
    );
    const payload = await response.json();

    expect(response.status).toBe(404);
    expect(payload.error).toBe("not_found");
  });

  it("lists only medications for the requested patient", async () => {
    const request = new Request("http://localhost/api/medications?patientId=patient-1", {
      headers: { authorization: "Bearer caregiver-1" }
    });
    const response = await listMedications(
      request,
      new Set(["patient-1"]),
      [
        { id: "med-1", patientId: "patient-1", name: "Medication A" },
        { id: "med-2", patientId: "patient-2", name: "Medication B" }
      ]
    );
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.data).toHaveLength(1);
    expect(payload.data[0].id).toBe("med-1");
  });
});
