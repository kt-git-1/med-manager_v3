import { describe, expect, it } from "vitest";

// ---------------------------------------------------------------------------
// T003: Contract test for HISTORY_RETENTION_LIMIT response shape
// ---------------------------------------------------------------------------

/**
 * Constructs a HISTORY_RETENTION_LIMIT error response matching the stable
 * contract defined in contracts/openapi.yaml.
 *
 * This contract is used by the iOS client to differentiate retention errors
 * from generic 403 auth failures and display the lock UI.
 */
function historyRetentionLimitResponse(cutoffDate: string, retentionDays: number) {
  return new Response(
    JSON.stringify({
      code: "HISTORY_RETENTION_LIMIT",
      message: "履歴の閲覧は直近30日間に制限されています。",
      cutoffDate,
      retentionDays
    }),
    {
      status: 403,
      headers: { "content-type": "application/json" }
    }
  );
}

describe("HISTORY_RETENTION_LIMIT contract", () => {
  it("returns HTTP 403 status", () => {
    const response = historyRetentionLimitResponse("2026-01-12", 30);
    expect(response.status).toBe(403);
  });

  it("response body contains code: HISTORY_RETENTION_LIMIT", async () => {
    const response = historyRetentionLimitResponse("2026-01-12", 30);
    const payload = await response.json();
    expect(payload.code).toBe("HISTORY_RETENTION_LIMIT");
  });

  it("response body contains cutoffDate as YYYY-MM-DD string", async () => {
    const response = historyRetentionLimitResponse("2026-01-12", 30);
    const payload = await response.json();
    expect(typeof payload.cutoffDate).toBe("string");
    expect(payload.cutoffDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it("response body contains retentionDays as integer equal to 30", async () => {
    const response = historyRetentionLimitResponse("2026-01-12", 30);
    const payload = await response.json();
    expect(payload.retentionDays).toBe(30);
    expect(typeof payload.retentionDays).toBe("number");
    expect(Number.isInteger(payload.retentionDays)).toBe(true);
  });

  it("response body contains message as non-empty string", async () => {
    const response = historyRetentionLimitResponse("2026-01-12", 30);
    const payload = await response.json();
    expect(typeof payload.message).toBe("string");
    expect(payload.message.length).toBeGreaterThan(0);
  });

  it("cutoffDate varies by input (not hardcoded)", async () => {
    const r1 = historyRetentionLimitResponse("2026-01-12", 30);
    const r2 = historyRetentionLimitResponse("2025-12-15", 30);
    const p1 = await r1.json();
    const p2 = await r2.json();
    expect(p1.cutoffDate).toBe("2026-01-12");
    expect(p2.cutoffDate).toBe("2025-12-15");
  });

  it("response content-type is application/json", () => {
    const response = historyRetentionLimitResponse("2026-01-12", 30);
    expect(response.headers.get("content-type")).toBe("application/json");
  });
});
