import { describe, expect, it } from "vitest";
import { computeInventoryState } from "../../src/services/medicationService";

describe("computeInventoryState", () => {
  it("treats positive stock below one day as low instead of out", () => {
    expect(computeInventoryState(2, 3, 0)).toBe("LOW");
  });

  it("treats only zero stock as out", () => {
    expect(computeInventoryState(0, 3, 0)).toBe("OUT");
  });

  it("keeps stock above the threshold available", () => {
    expect(computeInventoryState(20, 3, 5)).toBe("NONE");
  });
});
