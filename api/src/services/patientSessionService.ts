import { randomBytes } from "crypto";
import { AuthError } from "../middleware/auth";
import { hashPatientSessionToken } from "../auth/patientSessionVerifier";
import {
  createPatientSessionRecord,
  findActivePatientSessionByTokenHash,
  revokePatientSessionByTokenHash
} from "../repositories/patientSessionRepo";
import {
  findLinkingCodeByHash,
  markLinkingCodeUsed
} from "../repositories/linkingCodeRepo";
import {
  assertLinkingNotLocked,
  registerLinkingAttemptFailure,
  resetLinkingAttempts
} from "./linkingService";
import { createHash } from "crypto";
import { prisma } from "../repositories/prisma";

export type PatientSessionIssued = {
  patientSessionToken: string;
  expiresAt: Date | null;
  patientId: string;
};

function hashLinkingCode(code: string) {
  return createHash("sha256").update(code).digest("hex");
}

function generatePatientSessionToken() {
  return randomBytes(32).toString("hex");
}

export async function exchangeLinkingCodeForSession(code: string): Promise<PatientSessionIssued> {
  const codeHash = hashLinkingCode(code);
  const linkingCode = await findLinkingCodeByHash(codeHash);
  if (!linkingCode) {
    throw new AuthError("Not found", 404);
  }
  await assertLinkingNotLocked(linkingCode.patientId);
  const now = new Date();
  if (linkingCode.usedAt || linkingCode.expiresAt <= now) {
    await registerLinkingAttemptFailure(linkingCode.patientId);
    throw new AuthError("Not found", 404);
  }
  const patientSessionToken = generatePatientSessionToken();
  const tokenHash = hashPatientSessionToken(patientSessionToken);

  await prisma.$transaction([
    markLinkingCodeUsed(linkingCode.id, now),
    createPatientSessionRecord({
      patientId: linkingCode.patientId,
      tokenHash,
      issuedAt: now,
      lastRotatedAt: now,
      expiresAt: null
    })
  ]);
  await resetLinkingAttempts(linkingCode.patientId);
  return { patientSessionToken, expiresAt: null, patientId: linkingCode.patientId };
}

export async function refreshPatientSessionToken(token: string): Promise<PatientSessionIssued> {
  const tokenHash = hashPatientSessionToken(token);
  const session = await findActivePatientSessionByTokenHash(tokenHash);
  if (!session) {
    throw new AuthError("Unauthorized", 401);
  }
  if (session.expiresAt && session.expiresAt <= new Date()) {
    throw new AuthError("Unauthorized", 401);
  }
  const now = new Date();
  const nextToken = generatePatientSessionToken();
  const nextHash = hashPatientSessionToken(nextToken);

  await prisma.$transaction([
    revokePatientSessionByTokenHash(tokenHash, now),
    createPatientSessionRecord({
      patientId: session.patientId,
      tokenHash: nextHash,
      issuedAt: now,
      lastRotatedAt: now,
      expiresAt: null
    })
  ]);
  return { patientSessionToken: nextToken, expiresAt: null, patientId: session.patientId };
}
