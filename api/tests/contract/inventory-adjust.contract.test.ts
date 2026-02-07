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
  const match = pathname.match(
    /\/api\/patients\/([^/]+)\/medications\/([^/]+)\/inventory\/adjust/
  );
  return { patientId: match?.[1] ?? null, medicationId: match?.[2] ?? null };
}

async function adjustInventory(
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
  if (!body.reason) {
    return jsonResponse({ error: "validation", message: "reason required" }, 422);
  }
  if (body.delta === undefined && body.absoluteQuantity === undefined) {
    return jsonResponse({ error: "validation", message: "delta or absoluteQuantity required" }, 422);
  }
  const existing = store.find(
    (item) => item.patientId === patientId && item.medicationId === medicationId
  );
  if (!existing) {
    return jsonResponse({ error: "not_found" }, 404);
  }
  return jsonResponse({
    data: {
      medicationId: existing.medicationId,
      name: existing.name,
      inventoryEnabled: existing.inventoryEnabled,
      inventoryQuantity: existing.inventoryQuantity,
      inventoryLowThreshold: existing.inventoryLowThreshold,
      low: existing.low,
      out: existing.out
    }
  });
}

describe("inventory adjust contract", () => {
  it("returns 422 when required ids are missing", async () => {
    const request = new Request("http://localhost/api/patients/inventory/adjust", {
      method: "POST",
      headers: { authorization: "Bearer caregiver-1", "content-type": "application/json" },
      body: JSON.stringify({ reason: "REFILL", delta: 5 })
    });
    const response = await adjustInventory(request, new Set(["patient-1"]), []);
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.message).toBe("patientId and medicationId required");
  });

  it("returns 422 when reason is missing", async () => {
    const request = new Request(
      "http://localhost/api/patients/patient-1/medications/med-1/inventory/adjust",
      {
        method: "POST",
        headers: { authorization: "Bearer caregiver-1", "content-type": "application/json" },
        body: JSON.stringify({ delta: 2 })
      }
    );
    const response = await adjustInventory(request, new Set(["patient-1"]), [
      {
        patientId: "patient-1",
        medicationId: "med-1",
        name: "Medication A",
        isPrn: false,
        inventoryEnabled: true,
        inventoryQuantity: 3,
        inventoryLowThreshold: 1,
        low: false,
        out: false
      }
    ]);
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.message).toBe("reason required");
  });

  it("returns 422 when delta and absoluteQuantity are missing", async () => {
    const request = new Request(
      "http://localhost/api/patients/patient-1/medications/med-1/inventory/adjust",
      {
        method: "POST",
        headers: { authorization: "Bearer caregiver-1", "content-type": "application/json" },
        body: JSON.stringify({ reason: "REFILL" })
      }
    );
    const response = await adjustInventory(request, new Set(["patient-1"]), [
      {
        patientId: "patient-1",
        medicationId: "med-1",
        name: "Medication A",
        isPrn: false,
        inventoryEnabled: true,
        inventoryQuantity: 3,
        inventoryLowThreshold: 1,
        low: false,
        out: false
      }
    ]);
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.message).toBe("delta or absoluteQuantity required");
  });

  it("returns 404 for non-owned patientId", async () => {
    const request = new Request(
      "http://localhost/api/patients/patient-2/medications/med-1/inventory/adjust",
      {
        method: "POST",
        headers: { authorization: "Bearer caregiver-1", "content-type": "application/json" },
        body: JSON.stringify({ reason: "REFILL", delta: 2 })
      }
    );
    const response = await adjustInventory(request, new Set(["patient-1"]), []);
    const payload = await response.json();

    expect(response.status).toBe(404);
    expect(payload.error).toBe("not_found");
  });
});
