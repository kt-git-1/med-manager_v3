import { describe, expect, it } from "vitest";
import {
  calculateHistoryStreak,
  type HistoryStreakDose
} from "../../src/services/historyStreakService";

const now = new Date("2026-07-15T03:00:00.000Z");

function dose(date: string, status: HistoryStreakDose["effectiveStatus"]): HistoryStreakDose {
  return {
    scheduledAt: `${date}T00:00:00.000Z`,
    effectiveStatus: status
  };
}

describe("calculateHistoryStreak", () => {
  it("counts today after all of today's scheduled doses are recorded", () => {
    const result = calculateHistoryStreak(
      [
        dose("2026-07-15", "taken"),
        dose("2026-07-15", "taken"),
        dose("2026-07-14", "taken"),
        dose("2026-07-13", "taken")
      ],
      now
    );

    expect(result).toEqual({
      currentStreakDays: 3,
      isAtLeast: false,
      todayStatus: "complete"
    });
  });

  it("keeps yesterday's streak while today is still in progress", () => {
    const result = calculateHistoryStreak(
      [
        dose("2026-07-15", "taken"),
        dose("2026-07-15", "pending"),
        dose("2026-07-14", "taken"),
        dose("2026-07-13", "taken")
      ],
      now
    );

    expect(result).toEqual({
      currentStreakDays: 2,
      isAtLeast: false,
      todayStatus: "inProgress"
    });
  });

  it("starts again after a missed dose is confirmed today", () => {
    const result = calculateHistoryStreak(
      [dose("2026-07-15", "missed"), dose("2026-07-14", "taken")],
      now
    );

    expect(result).toEqual({
      currentStreakDays: 0,
      isAtLeast: false,
      todayStatus: "missed"
    });
  });

  it("does not break the streak on a day without a schedule", () => {
    const result = calculateHistoryStreak(
      [dose("2026-07-14", "taken"), dose("2026-07-12", "taken")],
      now
    );

    expect(result).toEqual({
      currentStreakDays: 2,
      isAtLeast: false,
      todayStatus: "noSchedule"
    });
  });

  it("shows one day after the first completed day", () => {
    const result = calculateHistoryStreak([dose("2026-07-15", "taken")], now);

    expect(result.currentStreakDays).toBe(1);
    expect(result.todayStatus).toBe("complete");
  });

  it("caps very long streaks and marks the value as a minimum", () => {
    const result = calculateHistoryStreak(
      [dose("2026-07-15", "taken"), dose("2026-07-14", "taken"), dose("2026-07-13", "taken")],
      now,
      "Asia/Tokyo",
      2
    );

    expect(result.currentStreakDays).toBe(2);
    expect(result.isAtLeast).toBe(true);
  });
});
