import { describe, expect, it } from "vitest";
import { buildSlotProgress, buildSlotSummary } from "../../src/services/scheduleResponse";

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

  it("preserves medication counts for a partially recorded slot", () => {
    const progress = buildSlotProgress(
      [
        { scheduledAt: "2026-02-01T23:00:00.000Z", effectiveStatus: "taken" },
        { scheduledAt: "2026-02-01T23:00:00.000Z", effectiveStatus: "pending" }
      ],
      "Asia/Tokyo"
    );

    expect(progress.morning).toEqual({
      scheduledCount: 2,
      takenCount: 1,
      pendingCount: 1,
      missedCount: 0
    });
    expect(progress.noon).toEqual({
      scheduledCount: 0,
      takenCount: 0,
      pendingCount: 0,
      missedCount: 0
    });
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
