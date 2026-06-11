// ---------------------------------------------------------------------------
// Entitlement Service — claim + read
// ---------------------------------------------------------------------------

import {
  upsertEntitlement,
  findEntitlementsByCaregiverId
} from "../repositories/entitlementRepo";
import { X509Certificate, createPublicKey, verify } from "crypto";

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

type JwsHeader = {
  alg?: string;
  x5c?: string[];
};

export class InvalidStoreKitTransactionError extends Error {
  statusCode = 422;
  code = "validation_error";

  constructor(message = "Invalid StoreKit transaction") {
    super(message);
  }
}

function base64UrlDecode(input: string) {
  const normalized = input.replace(/-/g, "+").replace(/_/g, "/");
  const padding = normalized.length % 4 === 0 ? "" : "=".repeat(4 - (normalized.length % 4));
  return Buffer.from(`${normalized}${padding}`, "base64");
}

function parseJson<T>(input: string): T {
  try {
    return JSON.parse(base64UrlDecode(input).toString("utf8")) as T;
  } catch {
    throw new InvalidStoreKitTransactionError();
  }
}

function normalizeDerInteger(input: Buffer) {
  let offset = 0;
  while (offset < input.length - 1 && input[offset] === 0) {
    offset += 1;
  }
  let normalized = input.subarray(offset);
  if (normalized[0] & 0x80) {
    normalized = Buffer.concat([Buffer.from([0]), normalized]);
  }
  return normalized;
}

function rawSignatureToDer(signature: Buffer) {
  if (signature.length !== 64) {
    throw new InvalidStoreKitTransactionError();
  }
  const r = normalizeDerInteger(signature.subarray(0, 32));
  const s = normalizeDerInteger(signature.subarray(32));
  const rPart = Buffer.concat([Buffer.from([0x02, r.length]), r]);
  const sPart = Buffer.concat([Buffer.from([0x02, s.length]), s]);
  const totalLength = rPart.length + sPart.length;
  return Buffer.concat([Buffer.from([0x30, totalLength]), rPart, sPart]);
}

function pemFromBase64Certificate(input: string) {
  return [
    "-----BEGIN CERTIFICATE-----",
    input.match(/.{1,64}/g)?.join("\n") ?? input,
    "-----END CERTIFICATE-----"
  ].join("\n");
}

function normalizeCertificatePem(input: string) {
  return input.includes("-----BEGIN CERTIFICATE-----")
    ? input
    : pemFromBase64Certificate(input);
}

function certificateFingerprint(cert: X509Certificate) {
  return cert.fingerprint256.replace(/:/g, "").toUpperCase();
}

function assertCertificateIsCurrentlyValid(cert: X509Certificate) {
  const now = Date.now();
  if (Date.parse(cert.validFrom) > now || Date.parse(cert.validTo) < now) {
    throw new InvalidStoreKitTransactionError("Expired StoreKit signing certificate");
  }
}

function trustedRootCertificates() {
  const values = [
    process.env.APPLE_ROOT_CA_PEM,
    process.env.APPLE_ROOT_CA_PEM_2,
    process.env.APPLE_ROOT_CA_PEM_3
  ].filter((value): value is string => !!value);
  return values.map((value) => new X509Certificate(normalizeCertificatePem(value)));
}

function verifyCertificateChain(certs: X509Certificate[]) {
  if (certs.length === 0) {
    throw new InvalidStoreKitTransactionError();
  }
  for (const cert of certs) {
    assertCertificateIsCurrentlyValid(cert);
  }

  for (let index = 0; index < certs.length - 1; index += 1) {
    if (!certs[index].verify(certs[index + 1].publicKey)) {
      throw new InvalidStoreKitTransactionError("Invalid StoreKit certificate chain");
    }
  }

  const trustedRoots = trustedRootCertificates();
  if (trustedRoots.length === 0) {
    throw new InvalidStoreKitTransactionError("Apple root certificate is not configured");
  }

  const presentedRoot = certs[certs.length - 1];
  const trustedRoot = trustedRoots.find(
    (root) => certificateFingerprint(root) === certificateFingerprint(presentedRoot)
  );
  if (trustedRoot) {
    if (!presentedRoot.verify(trustedRoot.publicKey)) {
      throw new InvalidStoreKitTransactionError("Invalid Apple root certificate");
    }
    return;
  }

  if (!trustedRoots.some((root) => presentedRoot.verify(root.publicKey))) {
    throw new InvalidStoreKitTransactionError("Untrusted StoreKit signing certificate");
  }
}

function resolveVerificationKey(header: JwsHeader) {
  const pinnedPublicKey = process.env.STOREKIT_JWS_PUBLIC_KEY_PEM;
  if (pinnedPublicKey) {
    return createPublicKey(pinnedPublicKey);
  }

  if (!Array.isArray(header.x5c) || header.x5c.length === 0) {
    throw new InvalidStoreKitTransactionError("Missing StoreKit certificate chain");
  }
  const certificates = header.x5c.map((cert) => new X509Certificate(pemFromBase64Certificate(cert)));
  verifyCertificateChain(certificates);
  return certificates[0].publicKey;
}

function decodeAndVerifyJwsPayload(jws: string): JwsTransactionPayload {
  const parts = jws.split(".");
  if (parts.length !== 3) {
    throw new InvalidStoreKitTransactionError("Invalid JWS format");
  }
  const header = parseJson<JwsHeader>(parts[0]);
  if (header.alg !== "ES256") {
    throw new InvalidStoreKitTransactionError("Unsupported StoreKit JWS algorithm");
  }

  const publicKey = resolveVerificationKey(header);
  const verified = verify(
    "sha256",
    Buffer.from(`${parts[0]}.${parts[1]}`),
    publicKey,
    rawSignatureToDer(base64UrlDecode(parts[2]))
  );
  if (!verified) {
    throw new InvalidStoreKitTransactionError("Invalid StoreKit transaction signature");
  }

  const payload = parseJson<JwsTransactionPayload>(parts[1]);

  if (!payload.originalTransactionId || !payload.transactionId || !payload.productId) {
    throw new InvalidStoreKitTransactionError("Missing required fields in JWS payload");
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
  const payload = decodeAndVerifyJwsPayload(input.signedTransactionInfo);

  if (payload.productId !== input.productId) {
    throw new InvalidStoreKitTransactionError("StoreKit transaction product mismatch");
  }
  if (!Number.isFinite(payload.purchaseDate)) {
    throw new InvalidStoreKitTransactionError("Invalid StoreKit purchase date");
  }

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
