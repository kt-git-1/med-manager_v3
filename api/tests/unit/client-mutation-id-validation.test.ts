import { describe, expect, it } from "vitest";
import { validateInventoryAdjust } from "../../src/validators/inventory";
import { validatePrnDoseRecordCreate } from "../../src/validators/prnDoseRecord";

const VALID = "ABCDEF12-3456-4789-ABCD-EF1234567890";

describe("client mutation id validation", () => {
  it("accepts legacy requests without an id and normalizes valid UUID v4 values", () => {
    expect(validatePrnDoseRecordCreate({ medicationId: "med-1" }).errors).toEqual([]);
    expect(validateInventoryAdjust({ reason: "REFILL", delta: 1 }).errors).toEqual([]);

    expect(
      validatePrnDoseRecordCreate({ medicationId: "med-1", clientMutationId: VALID })
        .clientMutationId
    ).toBe(VALID.toLowerCase());
    expect(
      validateInventoryAdjust({ reason: "REFILL", delta: 1, clientMutationId: VALID })
        .clientMutationId
    ).toBe(VALID.toLowerCase());
  });

  it("rejects malformed and non-v4 identifiers", () => {
    expect(
      validatePrnDoseRecordCreate({ medicationId: "med-1", clientMutationId: "not-a-uuid" }).errors
    ).toContain("clientMutationId must be a UUID v4");
    expect(
      validateInventoryAdjust({
        reason: "REFILL",
        delta: 1,
        clientMutationId: "abcdef12-3456-3789-abcd-ef1234567890"
      }).errors
    ).toContain("clientMutationId must be a UUID v4");
  });
});
