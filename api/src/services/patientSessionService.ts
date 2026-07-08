import { randomBytes } from "crypto";
import { AuthError } from "../middleware/auth";
import { hashPatientSessionToken } from "../auth/patientSessionVerifier";
import {
  createPatientSessionRecord,
  findActivePatientSessionByTokenHash,
  revokePatientSessionByTokenHash
} from "../repositories/patientSessionRepo";
import { findLinkingCodeByHash, markLinkingCodeUsed } from "../repositories/linkingCodeRepo";
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

const PATIENT_SESSION_TTL_DAYS = Number(process.env.PATIENT_SESSION_TTL_DAYS ?? 90);

function hashLinkingCode(code: string) {
  return createHash("sha256").update(code).digest("hex");
}

function generatePatientSessionToken() {
  return randomBytes(32).toString("hex");
}

function patientSessionExpiresAt(now: Date) {
  return new Date(now.getTime() + PATIENT_SESSION_TTL_DAYS * 24 * 60 * 60 * 1000);
}

export async function exchangeLinkingCodeForSession(code: string): Promise<PatientSessionIssued> {
  const codeHash = hashLinkingCode(code);
  const linkingCode = await findLinkingCodeByHash(codeHash);
  if (!linkingCode) {
    throw new AuthError("Not found", 404);
  }
  await assertLinkingNotLocked(linkingCode.patientId);
  const now = new Date();
  const expiresAt = patientSessionExpiresAt(now);
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
      expiresAt
    })
  ]);
  await resetLinkingAttempts(linkingCode.patientId);
  return { patientSessionToken, expiresAt, patientId: linkingCode.patientId };
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
  const expiresAt = patientSessionExpiresAt(now);

  await prisma.$transaction([
    revokePatientSessionByTokenHash(tokenHash, now),
    createPatientSessionRecord({
      patientId: session.patientId,
      tokenHash: nextHash,
      issuedAt: now,
      lastRotatedAt: now,
      expiresAt
    })
  ]);
  return { patientSessionToken: nextToken, expiresAt, patientId: session.patientId };
}

export async function revokePatientSessionToken(token: string) {
  const tokenHash = hashPatientSessionToken(token);
  const result = await revokePatientSessionByTokenHash(tokenHash, new Date());
  if (result.count === 0) {
    throw new AuthError("Unauthorized", 401);
  }
  return { revoked: true };
}
