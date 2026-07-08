import { describe, expect, it } from "vitest";
import { computeRefillPlan } from "../../src/lib/computeRefillPlan";

describe("computeRefillPlan", () => {
  it("counts only scheduled weekdays for the next seven days refill amount", () => {
    const plan = computeRefillPlan({
      inventoryEnabled: true,
      inventoryQuantity: 2,
      doseCountPerIntake: 1,
      now: new Date("2026-07-06T15:00:00.000Z"), // 2026-07-07 Tue in Asia/Tokyo
      regimens: [
        {
          startDate: new Date("2026-01-01T00:00:00.000Z"),
          times: ["08:00"],
          daysOfWeek: ["MON", "TUE", "WED", "THU", "FRI"],
          enabled: true
        }
      ]
    });

    expect(plan.dailyPlannedUnits).toBe(1);
    expect(plan.nextSevenDaysPlannedUnits).toBe(5);
    expect(plan.nextFourteenDaysPlannedUnits).toBe(10);
    expect(plan.nextTwentyOneDaysPlannedUnits).toBe(15);
  });
});
