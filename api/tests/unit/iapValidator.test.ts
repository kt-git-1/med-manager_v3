import { describe, expect, it } from "vitest";
import { validateClaimInput } from "../../src/validators/iapValidator";

const VALID_PRODUCT_ID = "com.yourcompany.medicationapp.premium_unlock";

describe("IAP claim validator", () => {
  it("accepts valid claim payload", () => {
    const result = validateClaimInput({
      productId: VALID_PRODUCT_ID,
      signedTransactionInfo: "header.payload.signature"
    });
    expect(result.errors).toEqual([]);
    expect(result.productId).toBe(VALID_PRODUCT_ID);
    expect(result.signedTransactionInfo).toBe("header.payload.signature");
    expect(result.environment).toBe("Production");
  });

  it("accepts valid payload with explicit environment", () => {
    const result = validateClaimInput({
      productId: VALID_PRODUCT_ID,
      signedTransactionInfo: "header.payload.signature",
      environment: "Sandbox"
    });
    expect(result.errors).toEqual([]);
    expect(result.environment).toBe("Sandbox");
  });

  it("rejects missing productId", () => {
    const result = validateClaimInput({
      signedTransactionInfo: "header.payload.signature"
    });
    expect(result.errors).toContain("productId is required");
  });

  it("rejects wrong productId", () => {
    const result = validateClaimInput({
      productId: "com.wrong.product",
      signedTransactionInfo: "header.payload.signature"
    });
    expect(result.errors).toContain("productId does not match known product");
  });

  it("rejects empty productId", () => {
    const result = validateClaimInput({
      productId: "",
      signedTransactionInfo: "header.payload.signature"
    });
    expect(result.errors).toContain("productId is required");
  });

  it("rejects missing signedTransactionInfo", () => {
    const result = validateClaimInput({
      productId: VALID_PRODUCT_ID
    });
    expect(result.errors).toContain("signedTransactionInfo is required");
  });

  it("rejects empty signedTransactionInfo", () => {
    const result = validateClaimInput({
      productId: VALID_PRODUCT_ID,
      signedTransactionInfo: ""
    });
    expect(result.errors).toContain("signedTransactionInfo is required");
  });

  it("rejects non-string signedTransactionInfo", () => {
    const result = validateClaimInput({
      productId: VALID_PRODUCT_ID,
      signedTransactionInfo: 12345
    });
    expect(result.errors).toContain("signedTransactionInfo must be a string");
  });

  it("rejects non-string productId", () => {
    const result = validateClaimInput({
      productId: 123,
      signedTransactionInfo: "header.payload.signature"
    });
    expect(result.errors).toContain("productId must be a string");
  });
});
