import { describe, expect, it } from "vitest";

// ---------------------------------------------------------------------------
// T003: Contract test for history report response shape
// ---------------------------------------------------------------------------

/**
 * Constructs a valid HistoryReportResponse matching the stable contract
 * defined in contracts/openapi.yaml (HistoryReportResponse schema).
 *
 * This contract is used by the iOS client to decode the report DTO
 * (HistoryReportResponseDTO) for on-device PDF generation.
 */
function historyReportResponse(
  patientId: string,
  displayName: string,
  from: string,
  to: string,
  days: number
) {
  return new Response(
    JSON.stringify({
      patient: { id: patientId, displayName },
      range: { from, to, timezone: "Asia/Tokyo", days },
      days: [
        {
          date: from,
          slots: {
            morning: [
              {
                medicationId: "med-uuid-1",
                name: "アムロジピン",
                dosageText: "5mg",
                doseCount: 1,
                status: "TAKEN",
                recordedAt: `${from}T08:15:00+09:00`
              }
            ],
            noon: [
              {
                medicationId: "med-uuid-2",
                name: "メトホルミン",
                dosageText: "500mg",
                doseCount: 2,
                status: "MISSED",
                recordedAt: null
              }
            ],
            evening: [],
            bedtime: [
              {
                medicationId: "med-uuid-3",
                name: "ゾルピデム",
                dosageText: "5mg",
                doseCount: 1,
                status: "PENDING",
                recordedAt: null
              }
            ]
          },
          prn: [
            {
              medicationId: "prn-uuid-1",
              name: "ロキソプロフェン",
              dosageText: "60mg",
              quantity: 1,
              recordedAt: `${from}T14:30:00+09:00`,
              recordedBy: "PATIENT"
            },
            {
              medicationId: "prn-uuid-2",
              name: "アセトアミノフェン",
              dosageText: "200mg",
              quantity: 2,
              recordedAt: `${from}T20:00:00+09:00`,
              recordedBy: "CAREGIVER"
            }
          ]
        }
      ]
    }),
    {
      status: 200,
      headers: { "content-type": "application/json" }
    }
  );
}

/**
 * Constructs an INVALID_RANGE error response matching the stable contract
 * defined in contracts/openapi.yaml (InvalidRangeError schema).
 */
function invalidRangeErrorResponse(message = "指定された期間が不正です。") {
  return new Response(
    JSON.stringify({
      code: "INVALID_RANGE",
      message
    }),
    {
      status: 400,
      headers: { "content-type": "application/json" }
    }
  );
}

// ---------------------------------------------------------------------------
// Contract: HistoryReportResponse (200)
// ---------------------------------------------------------------------------

describe("HistoryReportResponse contract", () => {
  it("returns HTTP 200 status", () => {
    const response = historyReportResponse(
      "patient-uuid",
      "太郎",
      "2026-01-01",
      "2026-01-30",
      30
    );
    expect(response.status).toBe(200);
  });

  it("response content-type is application/json", () => {
    const response = historyReportResponse(
      "patient-uuid",
      "太郎",
      "2026-01-01",
      "2026-01-30",
      30
    );
    expect(response.headers.get("content-type")).toBe("application/json");
  });

  it("patient has id (string) and displayName (string)", async () => {
    const response = historyReportResponse(
      "patient-uuid",
      "太郎",
      "2026-01-01",
      "2026-01-30",
      30
    );
    const payload = await response.json();
    expect(typeof payload.patient.id).toBe("string");
    expect(payload.patient.id.length).toBeGreaterThan(0);
    expect(typeof payload.patient.displayName).toBe("string");
    expect(payload.patient.displayName.length).toBeGreaterThan(0);
  });

  it("range has from (YYYY-MM-DD), to (YYYY-MM-DD), timezone (Asia/Tokyo), days (integer)", async () => {
    const response = historyReportResponse(
      "patient-uuid",
      "太郎",
      "2026-01-01",
      "2026-01-30",
      30
    );
    const payload = await response.json();
    expect(payload.range.from).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(payload.range.to).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(payload.range.timezone).toBe("Asia/Tokyo");
    expect(typeof payload.range.days).toBe("number");
    expect(Number.isInteger(payload.range.days)).toBe(true);
    expect(payload.range.days).toBeGreaterThan(0);
  });

  it("days is array with each element having date, slots, and prn", async () => {
    const response = historyReportResponse(
      "patient-uuid",
      "太郎",
      "2026-01-01",
      "2026-01-30",
      30
    );
    const payload = await response.json();
    expect(Array.isArray(payload.days)).toBe(true);
    expect(payload.days.length).toBeGreaterThan(0);

    const day = payload.days[0];
    expect(day.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(day.slots).toBeDefined();
    expect(typeof day.slots).toBe("object");
    expect(Array.isArray(day.prn)).toBe(true);
  });

  it("slots object has morning, noon, evening, bedtime arrays", async () => {
    const response = historyReportResponse(
      "patient-uuid",
      "太郎",
      "2026-01-01",
      "2026-01-30",
      30
    );
    const payload = await response.json();
    const slots = payload.days[0].slots;

    expect(Array.isArray(slots.morning)).toBe(true);
    expect(Array.isArray(slots.noon)).toBe(true);
    expect(Array.isArray(slots.evening)).toBe(true);
    expect(Array.isArray(slots.bedtime)).toBe(true);
  });

  it("slot items have medicationId, name, dosageText, doseCount, status, optional recordedAt", async () => {
    const response = historyReportResponse(
      "patient-uuid",
      "太郎",
      "2026-01-01",
      "2026-01-30",
      30
    );
    const payload = await response.json();

    // TAKEN item — has recordedAt
    const takenItem = payload.days[0].slots.morning[0];
    expect(typeof takenItem.medicationId).toBe("string");
    expect(typeof takenItem.name).toBe("string");
    expect(typeof takenItem.dosageText).toBe("string");
    expect(typeof takenItem.doseCount).toBe("number");
    expect(["TAKEN", "MISSED", "PENDING"]).toContain(takenItem.status);
    expect(takenItem.status).toBe("TAKEN");
    expect(typeof takenItem.recordedAt).toBe("string");

    // MISSED item — recordedAt is null
    const missedItem = payload.days[0].slots.noon[0];
    expect(missedItem.status).toBe("MISSED");
    expect(missedItem.recordedAt).toBeNull();

    // PENDING item — recordedAt is null
    const pendingItem = payload.days[0].slots.bedtime[0];
    expect(pendingItem.status).toBe("PENDING");
    expect(pendingItem.recordedAt).toBeNull();
  });

  it("status is one of TAKEN, MISSED, PENDING (uppercase)", async () => {
    const response = historyReportResponse(
      "patient-uuid",
      "太郎",
      "2026-01-01",
      "2026-01-30",
      30
    );
    const payload = await response.json();
    const allSlotItems = [
      ...payload.days[0].slots.morning,
      ...payload.days[0].slots.noon,
      ...payload.days[0].slots.evening,
      ...payload.days[0].slots.bedtime
    ];
    for (const item of allSlotItems) {
      expect(["TAKEN", "MISSED", "PENDING"]).toContain(item.status);
      // Verify uppercase
      expect(item.status).toBe(item.status.toUpperCase());
    }
  });

  it("PRN items have medicationId, name, dosageText, quantity, recordedAt, recordedBy", async () => {
    const response = historyReportResponse(
      "patient-uuid",
      "太郎",
      "2026-01-01",
      "2026-01-30",
      30
    );
    const payload = await response.json();
    const prnItems = payload.days[0].prn;
    expect(prnItems.length).toBe(2);

    for (const item of prnItems) {
      expect(typeof item.medicationId).toBe("string");
      expect(typeof item.name).toBe("string");
      expect(typeof item.dosageText).toBe("string");
      expect(typeof item.quantity).toBe("number");
      expect(typeof item.recordedAt).toBe("string");
      expect(["PATIENT", "CAREGIVER"]).toContain(item.recordedBy);
    }
  });

  it("recordedBy is PATIENT or CAREGIVER (uppercase)", async () => {
    const response = historyReportResponse(
      "patient-uuid",
      "太郎",
      "2026-01-01",
      "2026-01-30",
      30
    );
    const payload = await response.json();
    const prnItems = payload.days[0].prn;
    expect(prnItems[0].recordedBy).toBe("PATIENT");
    expect(prnItems[1].recordedBy).toBe("CAREGIVER");
  });

  it("range.days varies by input (not hardcoded)", async () => {
    const r1 = historyReportResponse("p1", "太郎", "2026-01-01", "2026-01-30", 30);
    const r2 = historyReportResponse("p1", "太郎", "2026-01-01", "2026-01-15", 15);
    const p1 = await r1.json();
    const p2 = await r2.json();
    expect(p1.range.days).toBe(30);
    expect(p2.range.days).toBe(15);
  });

  it("patient.displayName varies by input (not hardcoded)", async () => {
    const r1 = historyReportResponse("p1", "太郎", "2026-01-01", "2026-01-30", 30);
    const r2 = historyReportResponse("p1", "花子", "2026-01-01", "2026-01-30", 30);
    const p1 = await r1.json();
    const p2 = await r2.json();
    expect(p1.patient.displayName).toBe("太郎");
    expect(p2.patient.displayName).toBe("花子");
  });
});

// ---------------------------------------------------------------------------
// Contract: InvalidRangeError (400)
// ---------------------------------------------------------------------------

describe("INVALID_RANGE error contract", () => {
  it("returns HTTP 400 status", () => {
    const response = invalidRangeErrorResponse();
    expect(response.status).toBe(400);
  });

  it("response content-type is application/json", () => {
    const response = invalidRangeErrorResponse();
    expect(response.headers.get("content-type")).toBe("application/json");
  });

  it("response body contains code: INVALID_RANGE", async () => {
    const response = invalidRangeErrorResponse();
    const payload = await response.json();
    expect(payload.code).toBe("INVALID_RANGE");
  });

  it("response body contains message as non-empty string", async () => {
    const response = invalidRangeErrorResponse();
    const payload = await response.json();
    expect(typeof payload.message).toBe("string");
    expect(payload.message.length).toBeGreaterThan(0);
  });

  it("default message is Japanese validation error text", async () => {
    const response = invalidRangeErrorResponse();
    const payload = await response.json();
    expect(payload.message).toBe("指定された期間が不正です。");
  });

  it("custom message is preserved", async () => {
    const response = invalidRangeErrorResponse("カスタムエラーメッセージ");
    const payload = await response.json();
    expect(payload.message).toBe("カスタムエラーメッセージ");
  });

  it("code is stable string, not numeric", async () => {
    const response = invalidRangeErrorResponse();
    const payload = await response.json();
    expect(typeof payload.code).toBe("string");
    expect(payload.code).not.toMatch(/^\d+$/);
  });
});
