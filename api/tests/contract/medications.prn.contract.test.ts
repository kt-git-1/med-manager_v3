import { describe, expect, it } from "vitest";

type Medication = {
  id: string;
  patientId: string;
  name: string;
  isPrn: boolean;
  prnInstructions: string | null;
};

function jsonResponse(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "content-type": "application/json" }
  });
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
  const created: Medication = {
    id: `med-${store.length + 1}`,
    patientId: body.patientId,
    name: body.name,
    isPrn: Boolean(body.isPrn),
    prnInstructions: body.prnInstructions ?? null
  };
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
  if (body.isPrn !== undefined) {
    existing.isPrn = body.isPrn;
  }
  if (body.prnInstructions !== undefined) {
    existing.prnInstructions = body.prnInstructions;
  }
  return jsonResponse({ data: existing });
}

describe("medications PRN contract", () => {
  it("returns PRN fields on create", async () => {
    const request = new Request("http://localhost/api/medications", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        patientId: "patient-1",
        name: "Medication A",
        isPrn: true,
        prnInstructions: "as needed"
      })
    });
    const response = await createMedication(request, new Set(["patient-1"]), []);
    const payload = await response.json();

    expect(response.status).toBe(201);
    expect(payload.data).toMatchObject({
      patientId: "patient-1",
      name: "Medication A",
      isPrn: true,
      prnInstructions: "as needed"
    });
  });

  it("returns PRN fields on update", async () => {
    const request = new Request("http://localhost/api/medications/med-1?patientId=patient-1", {
      method: "PATCH",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ id: "med-1", isPrn: false, prnInstructions: null })
    });
    const response = await updateMedication(request, new Set(["patient-1"]), [
      {
        id: "med-1",
        patientId: "patient-1",
        name: "Medication A",
        isPrn: true,
        prnInstructions: "as needed"
      }
    ]);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.data).toMatchObject({
      id: "med-1",
      isPrn: false,
      prnInstructions: null
    });
  });
});
