import { describe, expect, it } from "vitest";
import { isCaregiverToken } from "../../src/middleware/auth";

type InventoryItem = {
  patientId: string;
  medicationId: string;
  name: string;
  isPrn: boolean;
  inventoryEnabled: boolean;
  inventoryQuantity: number;
  inventoryLowThreshold: number;
  low: boolean;
  out: boolean;
};

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

function extractIds(request: Request) {
  const { pathname } = new URL(request.url);
  const match = pathname.match(/\/api\/patients\/([^/]+)\/medications\/([^/]+)\/inventory/);
  return { patientId: match?.[1] ?? null, medicationId: match?.[2] ?? null };
}

async function updateInventory(
  request: Request,
  caregiverPatientIds: Set<string>,
  store: InventoryItem[]
) {
  const token = parseBearerToken(request.headers.get("authorization"));
  if (!token) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }
  const { patientId, medicationId } = extractIds(request);
  if (isCaregiverToken(token) && (!patientId || !medicationId)) {
    return jsonResponse({ error: "validation", message: "patientId and medicationId required" }, 422);
  }
  if (!patientId || !medicationId || !caregiverPatientIds.has(patientId)) {
    return jsonResponse({ error: "not_found" }, 404);
  }
  const body = await request.json();
  if (
    body.inventoryEnabled === undefined &&
    body.inventoryQuantity === undefined &&
    body.inventoryLowThreshold === undefined
  ) {
    return jsonResponse({ error: "validation", message: "no fields provided" }, 422);
  }
  const existing = store.find(
    (item) => item.patientId === patientId && item.medicationId === medicationId
  );
  if (!existing) {
    return jsonResponse({ error: "not_found" }, 404);
  }
  const updated: InventoryItem = {
    ...existing,
    inventoryEnabled: body.inventoryEnabled ?? existing.inventoryEnabled,
    inventoryQuantity: body.inventoryQuantity ?? existing.inventoryQuantity,
    inventoryLowThreshold: body.inventoryLowThreshold ?? existing.inventoryLowThreshold
  };
  return jsonResponse({
    data: {
      medicationId: updated.medicationId,
      name: updated.name,
      isPrn: updated.isPrn,
      inventoryEnabled: updated.inventoryEnabled,
      inventoryQuantity: updated.inventoryQuantity,
      inventoryLowThreshold: updated.inventoryLowThreshold,
      low: updated.low,
      out: updated.out
    }
  });
}

describe("inventory update contract", () => {
  it("returns 422 when required ids are missing", async () => {
    const request = new Request("http://localhost/api/patients/inventory", {
      method: "PATCH",
      headers: { authorization: "Bearer caregiver-1", "content-type": "application/json" },
      body: JSON.stringify({ inventoryEnabled: true })
    });
    const response = await updateInventory(request, new Set(["patient-1"]), []);
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.message).toBe("patientId and medicationId required");
  });

  it("returns 404 for non-owned patientId", async () => {
    const request = new Request(
      "http://localhost/api/patients/patient-2/medications/med-1/inventory",
      {
        method: "PATCH",
        headers: { authorization: "Bearer caregiver-1", "content-type": "application/json" },
        body: JSON.stringify({ inventoryEnabled: true })
      }
    );
    const response = await updateInventory(request, new Set(["patient-1"]), []);
    const payload = await response.json();

    expect(response.status).toBe(404);
    expect(payload.error).toBe("not_found");
  });

  it("returns 422 when no fields are provided", async () => {
    const request = new Request(
      "http://localhost/api/patients/patient-1/medications/med-1/inventory",
      {
        method: "PATCH",
        headers: { authorization: "Bearer caregiver-1", "content-type": "application/json" },
        body: JSON.stringify({})
      }
    );
    const response = await updateInventory(request, new Set(["patient-1"]), [
      {
        patientId: "patient-1",
        medicationId: "med-1",
        name: "Medication A",
        isPrn: false,
        inventoryEnabled: false,
        inventoryQuantity: 0,
        inventoryLowThreshold: 0,
        low: false,
        out: false
      }
    ]);
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.message).toBe("no fields provided");
  });

  it("returns updated inventory fields", async () => {
    const request = new Request(
      "http://localhost/api/patients/patient-1/medications/med-1/inventory",
      {
        method: "PATCH",
        headers: { authorization: "Bearer caregiver-1", "content-type": "application/json" },
        body: JSON.stringify({ inventoryEnabled: true, inventoryQuantity: 12 })
      }
    );
    const response = await updateInventory(request, new Set(["patient-1"]), [
      {
        patientId: "patient-1",
        medicationId: "med-1",
        name: "Medication A",
        isPrn: false,
        inventoryEnabled: false,
        inventoryQuantity: 5,
        inventoryLowThreshold: 2,
        low: false,
        out: false
      }
    ]);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.data).toEqual({
      medicationId: "med-1",
      name: "Medication A",
      isPrn: false,
      inventoryEnabled: true,
      inventoryQuantity: 12,
      inventoryLowThreshold: 2,
      low: false,
      out: false
    });
  });
});
