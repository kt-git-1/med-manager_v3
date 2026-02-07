import { describe, expect, it } from "vitest";

type PrnRecord = {
  id: string;
  patientId: string;
  medicationId: string;
  takenAt: string;
  quantityTaken: number;
  actorType: "PATIENT" | "CAREGIVER";
  createdAt: string;
};

function jsonResponse(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "content-type": "application/json" }
  });
}

function extractPatientId(request: Request) {
  const { pathname } = new URL(request.url);
  const match = pathname.match(/\/api\/patients\/([^/]+)\/prn-dose-records/);
  return match?.[1] ?? null;
}

async function createPrnRecord(request: Request) {
  const patientId = extractPatientId(request);
  if (!patientId) {
    return jsonResponse({ error: "validation", message: "patientId required" }, 422);
  }
  const body = await request.json();
  if (!body.medicationId) {
    return jsonResponse({ error: "validation", message: "medicationId required" }, 422);
  }
  const now = new Date().toISOString();
  const record: PrnRecord = {
    id: "prn-1",
    patientId,
    medicationId: body.medicationId,
    takenAt: now,
    quantityTaken: body.quantityTaken ?? 1,
    actorType: body.actorType ?? "PATIENT",
    createdAt: now
  };
  return jsonResponse({ record, medicationInventory: null });
}

describe("prn dose records contract", () => {
  it("requires medicationId", async () => {
    const request = new Request("http://localhost/api/patients/patient-1/prn-dose-records", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({})
    });
    const response = await createPrnRecord(request);
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.message).toBe("medicationId required");
  });

  it("returns PRN record response shape", async () => {
    const request = new Request("http://localhost/api/patients/patient-1/prn-dose-records", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ medicationId: "med-1", quantityTaken: 2, actorType: "PATIENT" })
    });
    const response = await createPrnRecord(request);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.record).toMatchObject({
      patientId: "patient-1",
      medicationId: "med-1",
      quantityTaken: 2,
      actorType: "PATIENT"
    });
    expect(payload.record.id).toBeTruthy();
    expect(payload.record.takenAt).toBeTruthy();
  });
});
