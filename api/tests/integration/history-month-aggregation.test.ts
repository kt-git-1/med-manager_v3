import { describe, expect, it } from "vitest";
import { buildSlotSummary } from "../../src/services/scheduleResponse";

describe("history month aggregation integration", () => {
  it("applies MISSED > PENDING > TAKEN precedence per slot", () => {
    const summary = buildSlotSummary(
      [
        { scheduledAt: "2026-02-01T23:00:00.000Z", effectiveStatus: "taken" },
        { scheduledAt: "2026-02-01T23:00:00.000Z", effectiveStatus: "pending" },
        { scheduledAt: "2026-02-01T23:00:00.000Z", effectiveStatus: "missed" }
      ],
      "Asia/Tokyo"
    );

    expect(summary).toEqual({
      morning: "missed",
      noon: "none",
      evening: "none",
      bedtime: "none"
    });
  });

  it("keeps pending when no missed doses exist for a slot", () => {
    const summary = buildSlotSummary(
      [
        { scheduledAt: "2026-02-01T23:00:00.000Z", effectiveStatus: "taken" },
        { scheduledAt: "2026-02-01T23:00:00.000Z", effectiveStatus: "pending" }
      ],
      "Asia/Tokyo"
    );

    expect(summary.morning).toBe("pending");
  });

  it("uses custom slot times when aggregating history slots", () => {
    const summary = buildSlotSummary(
      [{ scheduledAt: "2026-07-06T05:20:00.000Z", effectiveStatus: "missed" }],
      "Asia/Tokyo",
      { noon: "14:20" }
    );

    expect(summary).toEqual({
      morning: "none",
      noon: "missed",
      evening: "none",
      bedtime: "none"
    });
  });
});
