import { describe, expect, it } from "vitest";
import {
  DISPLAY_NAME_MAX_LENGTH,
  validateLinkCodeInput,
  validatePatientCreate
} from "../../src/validators/patient";

describe("patient validators", () => {
  it("trims and accepts valid displayName", () => {
    const result = validatePatientCreate({ displayName: "  Sakura  " });
    expect(result.errors).toEqual([]);
    expect(result.displayName).toBe("Sakura");
  });

  it("rejects blank displayName", () => {
    const result = validatePatientCreate({ displayName: "   " });
    expect(result.errors).toEqual(["displayName is required"]);
  });

  it("rejects displayName longer than max length", () => {
    const tooLong = "a".repeat(DISPLAY_NAME_MAX_LENGTH + 1);
    const result = validatePatientCreate({ displayName: tooLong });
    expect(result.errors).toEqual([`displayName must be <= ${DISPLAY_NAME_MAX_LENGTH} characters`]);
  });
});

describe("link code validator", () => {
  it("accepts 6-digit codes and trims input", () => {
    const result = validateLinkCodeInput({ code: " 123456 " });
    expect(result.errors).toEqual([]);
    expect(result.code).toBe("123456");
  });

  it("rejects missing code", () => {
    const result = validateLinkCodeInput({ code: "   " });
    expect(result.errors).toEqual(["code is required"]);
  });

  it("rejects non-numeric or wrong length codes", () => {
    expect(validateLinkCodeInput({ code: "12345" }).errors).toEqual([
      "code must be 6 digits"
    ]);
    expect(validateLinkCodeInput({ code: "abc123" }).errors).toEqual([
      "code must be 6 digits"
    ]);
  });
});
