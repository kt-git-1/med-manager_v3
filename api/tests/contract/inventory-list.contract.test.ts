import { describe, expect, it } from "vitest";
import { isCaregiverToken } from "../../src/middleware/auth";

type InventoryItem = {
  patientId: string;
  medicationId: string;
  name: string;
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

function extractPatientId(request: Request) {
  const { pathname } = new URL(request.url);
  const match = pathname.match(/\/api\/patients\/([^/]+)\/inventory/);
  return match?.[1] ?? null;
}

async function listInventory(
  request: Request,
  caregiverPatientIds: Set<string>,
  store: InventoryItem[]
) {
  const patientId = extractPatientId(request);
  const token = parseBearerToken(request.headers.get("authorization"));
  if (!token) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }
  if (isCaregiverToken(token) && !patientId) {
    return jsonResponse({ error: "validation", message: "patientId required" }, 422);
  }
  if (!patientId || !caregiverPatientIds.has(patientId)) {
    return jsonResponse({ error: "not_found" }, 404);
  }
  const medications = store
    .filter((item) => item.patientId === patientId)
    .map(({ patientId: _, ...payload }) => payload);
  return jsonResponse({ data: { patientId, medications } });
}

describe("inventory list contract", () => {
  it("requires patientId for caregiver list requests", async () => {
    const request = new Request("http://localhost/api/patients/inventory", {
      headers: { authorization: "Bearer caregiver-1" }
    });
    const response = await listInventory(request, new Set(["patient-1"]), []);
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.message).toBe("patientId required");
  });

  it("returns 404 for non-owned patientId", async () => {
    const request = new Request("http://localhost/api/patients/patient-2/inventory", {
      headers: { authorization: "Bearer caregiver-1" }
    });
    const response = await listInventory(request, new Set(["patient-1"]), []);
    const payload = await response.json();

    expect(response.status).toBe(404);
    expect(payload.error).toBe("not_found");
  });

  it("returns medication inventory for the requested patient", async () => {
    const request = new Request("http://localhost/api/patients/patient-1/inventory", {
      headers: { authorization: "Bearer caregiver-1" }
    });
    const response = await listInventory(request, new Set(["patient-1"]), [
      {
        patientId: "patient-1",
        medicationId: "med-1",
        name: "Medication A",
        inventoryEnabled: true,
        inventoryQuantity: 8,
        inventoryLowThreshold: 5,
        low: false,
        out: false
      }
    ]);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.data.patientId).toBe("patient-1");
    expect(payload.data.medications).toHaveLength(1);
    expect(payload.data.medications[0]).toEqual({
      medicationId: "med-1",
      name: "Medication A",
      inventoryEnabled: true,
      inventoryQuantity: 8,
      inventoryLowThreshold: 5,
      low: false,
      out: false
    });
  });
});
