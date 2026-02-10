// ---------------------------------------------------------------------------
// IAP Claim Input Validator
// ---------------------------------------------------------------------------

/** Known product identifier for Premium Unlock (Non-Consumable). */
export const PREMIUM_PRODUCT_ID = "com.yourcompany.medicationapp.premium_unlock";

const VALID_ENVIRONMENTS = ["Sandbox", "Production"] as const;

type ClaimValidationResult = {
  errors: string[];
  productId: string;
  signedTransactionInfo: string;
  environment: string;
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function validateClaimInput(body: any): ClaimValidationResult {
  const errors: string[] = [];

  // productId
  if (body.productId === undefined || body.productId === null || body.productId === "") {
    if (typeof body.productId === "string" && body.productId === "") {
      errors.push("productId is required");
    } else if (body.productId !== undefined && typeof body.productId !== "string") {
      errors.push("productId must be a string");
    } else {
      errors.push("productId is required");
    }
  } else if (typeof body.productId !== "string") {
    errors.push("productId must be a string");
  } else if (body.productId !== PREMIUM_PRODUCT_ID) {
    errors.push("productId does not match known product");
  }

  // signedTransactionInfo
  if (
    body.signedTransactionInfo === undefined ||
    body.signedTransactionInfo === null ||
    body.signedTransactionInfo === ""
  ) {
    if (typeof body.signedTransactionInfo === "string" && body.signedTransactionInfo === "") {
      errors.push("signedTransactionInfo is required");
    } else if (
      body.signedTransactionInfo !== undefined &&
      typeof body.signedTransactionInfo !== "string"
    ) {
      errors.push("signedTransactionInfo must be a string");
    } else {
      errors.push("signedTransactionInfo is required");
    }
  } else if (typeof body.signedTransactionInfo !== "string") {
    errors.push("signedTransactionInfo must be a string");
  }

  // environment (optional, defaults to "Production")
  let environment = "Production";
  if (body.environment !== undefined && body.environment !== null) {
    if (typeof body.environment === "string" && VALID_ENVIRONMENTS.includes(body.environment as (typeof VALID_ENVIRONMENTS)[number])) {
      environment = body.environment;
    }
    // Invalid environment silently falls back to Production
  }

  return {
    errors,
    productId: typeof body.productId === "string" ? body.productId : "",
    signedTransactionInfo:
      typeof body.signedTransactionInfo === "string" ? body.signedTransactionInfo : "",
    environment
  };
}
