// ---------------------------------------------------------------------------
// Entitlement Service — claim + read
// ---------------------------------------------------------------------------

import {
  upsertEntitlement,
  findEntitlementsByCaregiverId
} from "../repositories/entitlementRepo";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type ClaimInput = {
  productId: string;
  signedTransactionInfo: string;
  environment: string;
};

export type ClaimResult = {
  premium: boolean;
  productId: string;
  status: string;
  updatedAt: string; // ISO string
};

export type EntitlementReadResult = {
  premium: boolean;
  entitlements: {
    productId: string;
    status: string;
    purchasedAt: string;
    originalTransactionId: string;
  }[];
};

// ---------------------------------------------------------------------------
// JWS Payload Decoding (MVP — structure validation only, no cert chain)
// ---------------------------------------------------------------------------

type JwsTransactionPayload = {
  originalTransactionId: string;
  transactionId: string;
  purchaseDate: number; // ms epoch
  productId: string;
};

function decodeJwsPayload(jws: string): JwsTransactionPayload {
  const parts = jws.split(".");
  if (parts.length !== 3) {
    throw new Error("Invalid JWS format");
  }
  const payloadJson = Buffer.from(parts[1], "base64url").toString("utf8");
  const payload = JSON.parse(payloadJson) as JwsTransactionPayload;

  if (!payload.originalTransactionId || !payload.transactionId || !payload.productId) {
    throw new Error("Missing required fields in JWS payload");
  }

  return payload;
}

// ---------------------------------------------------------------------------
// Claim Entitlement
// ---------------------------------------------------------------------------

export async function claimEntitlement(
  caregiverId: string,
  input: ClaimInput
): Promise<ClaimResult> {
  const payload = decodeJwsPayload(input.signedTransactionInfo);

  const record = await upsertEntitlement({
    caregiverId,
    productId: input.productId,
    originalTransactionId: payload.originalTransactionId,
    transactionId: payload.transactionId,
    purchasedAt: new Date(payload.purchaseDate),
    environment: input.environment
  });

  return {
    premium: record.status === "ACTIVE",
    productId: record.productId,
    status: record.status,
    updatedAt: record.updatedAt.toISOString()
  };
}

// ---------------------------------------------------------------------------
// Get Entitlements
// ---------------------------------------------------------------------------

export async function getEntitlements(
  caregiverId: string
): Promise<EntitlementReadResult> {
  const records = await findEntitlementsByCaregiverId(caregiverId);
  const premium = records.some((r) => r.status === "ACTIVE");

  return {
    premium,
    entitlements: records.map((r) => ({
      productId: r.productId,
      status: r.status,
      purchasedAt: r.purchasedAt.toISOString(),
      originalTransactionId: r.originalTransactionId
    }))
  };
}
