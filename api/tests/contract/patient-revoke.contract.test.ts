import { describe, expect, it } from "vitest";

type RevokeResponse = {
  data?: { revoked: true };
  error?: string;
};

function jsonResponse(payload: RevokeResponse, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "content-type": "application/json" }
  });
}

async function revokePatient(patientId: string, knownPatientIds: Set<string>) {
  if (!knownPatientIds.has(patientId)) {
    return jsonResponse({ error: "not_found" }, 404);
  }
  return jsonResponse({ data: { revoked: true } }, 200);
}

describe("patient revoke contract", () => {
  it("returns 200 when patient exists", async () => {
    const response = await revokePatient("patient-1", new Set(["patient-1"]));
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.data).toEqual({ revoked: true });
  });

  it("returns 404 when patient does not exist", async () => {
    const response = await revokePatient("missing", new Set());
    const payload = await response.json();

    expect(response.status).toBe(404);
    expect(payload.error).toBe("not_found");
  });
});
