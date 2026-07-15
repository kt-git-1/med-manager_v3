import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../src/middleware/error", () => ({
  errorResponse: (error: unknown) =>
    new Response(JSON.stringify({ error: String(error) }), {
      status: 500,
      headers: { "content-type": "application/json" }
    })
}));

vi.mock("../../src/middleware/auth", () => ({
  requirePatient: vi.fn(async () => ({ patientId: "patient-1" }))
}));

vi.mock("../../src/services/historyStreakService", () => ({
  getPatientHistoryStreak: vi.fn(async () => ({
    currentStreakDays: 5,
    isAtLeast: false,
    todayStatus: "inProgress"
  }))
}));

import { GET } from "../../app/api/patient/history/streak/route";
import { requirePatient } from "../../src/middleware/auth";
import { getPatientHistoryStreak } from "../../src/services/historyStreakService";

describe("GET /api/patient/history/streak", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns the authenticated patient's aggregate without history details", async () => {
    const response = await GET(
      new Request("http://localhost/api/patient/history/streak", {
        headers: { authorization: "Bearer patient-token" }
      })
    );

    expect(response.status).toBe(200);
    expect(requirePatient).toHaveBeenCalledWith("Bearer patient-token");
    expect(getPatientHistoryStreak).toHaveBeenCalledWith("patient-1");
    await expect(response.json()).resolves.toEqual({
      currentStreakDays: 5,
      isAtLeast: false,
      todayStatus: "inProgress"
    });
  });
});
