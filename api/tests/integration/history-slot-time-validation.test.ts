import { describe, expect, it, vi } from "vitest";
import { GET as patientDayRoute } from "../../app/api/patient/history/day/route";
import { GET as patientMonthRoute } from "../../app/api/patient/history/month/route";
import { GET as caregiverDayRoute } from "../../app/api/patients/[patientId]/history/day/route";
import { GET as caregiverMonthRoute } from "../../app/api/patients/[patientId]/history/month/route";

vi.mock("../../src/middleware/error", () => ({
  errorResponse: (error: unknown) =>
    new Response(JSON.stringify({ error: String(error) }), {
      status: 500,
      headers: { "content-type": "application/json" }
    })
}));

vi.mock("../../src/middleware/auth", () => ({
  requirePatient: vi.fn(async () => ({ patientId: "patient-1" })),
  requireCaregiver: vi.fn(async () => ({ caregiverUserId: "caregiver-1" })),
  getBearerToken: vi.fn(() => "caregiver-valid"),
  isCaregiverToken: vi.fn(() => true),
  assertCaregiverPatientScope: vi.fn(async () => undefined),
  AuthError: class AuthError extends Error {
    constructor(
      message: string,
      public statusCode = 403
    ) {
      super(message);
    }
  }
}));

vi.mock("../../src/services/scheduleService", () => ({
  getDayRange: vi.fn(() => ({
    from: new Date("2026-02-01T00:00:00.000Z"),
    to: new Date("2026-02-02T00:00:00.000Z")
  })),
  getMonthRange: vi.fn(() => ({
    from: new Date("2026-02-01T00:00:00.000Z"),
    to: new Date("2026-03-01T00:00:00.000Z")
  })),
  getLocalDateKey: vi.fn(() => "2026-02-01"),
  getScheduleWithStatus: vi.fn(async () => [])
}));

vi.mock("../../src/services/prnDoseRecordService", () => ({
  listPrnHistoryItemsByRange: vi.fn(async () => ({ items: [], countByDay: {} }))
}));

vi.mock("../../src/validators/schedule", () => ({
  validateDateString: vi.fn(() => []),
  validateYearMonth: vi.fn(() => [])
}));

describe("history slot time validation", () => {
  it("returns 422 for invalid morningTime=99:99", async () => {
    const request = new Request(
      "http://localhost/api/patient/history/day?date=2026-02-01&morningTime=99:99",
      {
        headers: { authorization: "Bearer patient-token" }
      }
    );

    const response = await patientDayRoute(request);
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.error).toBe("validation");
    expect(payload.messages).toContain("morningTime must be HH:MM between 00:00 and 23:59");
  });

  it("returns 422 for invalid noonTime=24:00 on caregiver month route", async () => {
    const request = new Request(
      "http://localhost/api/patients/patient-1/history/month?year=2026&month=2&noonTime=24:00",
      {
        headers: { authorization: "Bearer caregiver-valid" }
      }
    );

    const response = await caregiverMonthRoute(request, {
      params: Promise.resolve({ patientId: "patient-1" })
    });
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.error).toBe("validation");
    expect(payload.messages).toContain("noonTime must be HH:MM between 00:00 and 23:59");
  });

  it("accepts valid slot time 08:30", async () => {
    const patientMonthRequest = new Request(
      "http://localhost/api/patient/history/month?year=2026&month=2&morningTime=08:30",
      {
        headers: { authorization: "Bearer patient-token" }
      }
    );
    const caregiverDayRequest = new Request(
      "http://localhost/api/patients/patient-1/history/day?date=2026-02-01&morningTime=08:30",
      {
        headers: { authorization: "Bearer caregiver-valid" }
      }
    );

    const patientMonthResponse = await patientMonthRoute(patientMonthRequest);
    const caregiverDayResponse = await caregiverDayRoute(caregiverDayRequest, {
      params: Promise.resolve({ patientId: "patient-1" })
    });

    expect(patientMonthResponse.status).toBe(200);
    expect(caregiverDayResponse.status).toBe(200);
  });
});
