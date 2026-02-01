import { createHash, randomInt } from "crypto";
import { AuthError } from "../middleware/auth";
import { prisma } from "../repositories/prisma";
import { listPatientRecordsByCaregiver, getPatientRecordForCaregiver } from "../repositories/patientRepo";
import { getActiveLinkForCaregiverPatient } from "../repositories/caregiverPatientLinkRepo";
import { createLinkingCodeRecord, invalidateActiveLinkingCodes } from "../repositories/linkingCodeRepo";
import {
  LINKING_CODE_LENGTH,
  LINKING_CODE_MAX_ATTEMPTS,
  LINKING_CODE_LOCKOUT_MINUTES,
  LINKING_CODE_TTL_MINUTES
} from "./linkingConstants";
import {
  getLinkingAttempt,
  resetLinkingAttempt,
  upsertLinkingAttempt
} from "../repositories/linkingAttemptRepo";
import { revokePatientSessionsByPatientId } from "../repositories/patientSessionRepo";

export type PatientSummary = {
  id: string;
  displayName: string;
};

export type LinkingCodeIssued = {
  code: string;
  expiresAt: Date;
};

function hashLinkingCode(code: string) {
  return createHash("sha256").update(code).digest("hex");
}

function generateLinkingCode() {
  const max = 10 ** LINKING_CODE_LENGTH;
  const value = randomInt(0, max);
  return value.toString().padStart(LINKING_CODE_LENGTH, "0");
}

export async function createPatientForCaregiver(
  caregiverUserId: string,
  displayName: string
): Promise<PatientSummary> {
  const patient = await prisma.$transaction(async (tx) => {
    const created = await tx.patient.create({
      data: { caregiverId: caregiverUserId, displayName }
    });
    await tx.caregiverPatientLink.create({
      data: { caregiverId: caregiverUserId, patientId: created.id, status: "ACTIVE" }
    });
    return created;
  });
  return { id: patient.id, displayName: patient.displayName };
}

export async function listPatientsForCaregiver(
  caregiverUserId: string
): Promise<PatientSummary[]> {
  const records = await listPatientRecordsByCaregiver(caregiverUserId);
  return records.map((record) => ({ id: record.id, displayName: record.displayName }));
}

export async function issueLinkingCodeForPatient(
  caregiverUserId: string,
  patientId: string
): Promise<LinkingCodeIssued> {
  const patient = await getPatientRecordForCaregiver(patientId, caregiverUserId);
  if (!patient) {
    throw new AuthError("Not found", 404);
  }
  const link = await getActiveLinkForCaregiverPatient(caregiverUserId, patientId);
  if (!link) {
    throw new AuthError("Not found", 404);
  }
  const code = generateLinkingCode();
  const codeHash = hashLinkingCode(code);
  const expiresAt = new Date(Date.now() + LINKING_CODE_TTL_MINUTES * 60 * 1000);
  const now = new Date();
  await prisma.$transaction([
    invalidateActiveLinkingCodes(patientId, now),
    createLinkingCodeRecord({
      patientId,
      codeHash,
      expiresAt,
      issuedBy: caregiverUserId
    })
  ]);
  return { code, expiresAt };
}

export async function revokePatientLinkForCaregiver(
  caregiverUserId: string,
  patientId: string
) {
  const now = new Date();
  const [linkResult] = await prisma.$transaction([
    prisma.caregiverPatientLink.updateMany({
      where: {
        caregiverId: caregiverUserId,
        patientId,
        status: "ACTIVE",
        revokedAt: null
      },
      data: { status: "REVOKED", revokedAt: now }
    }),
    revokePatientSessionsByPatientId(patientId, now)
  ]);

  if (linkResult.count === 0) {
    throw new AuthError("Not found", 404);
  }

  return { revokedAt: now };
}

export async function assertLinkingNotLocked(patientId: string) {
  const attempt = await getLinkingAttempt(patientId);
  if (!attempt || !attempt.lockedUntil) {
    return;
  }
  const now = new Date();
  if (attempt.lockedUntil > now) {
    throw new AuthError("Too many attempts", 429);
  }
  await resetLinkingAttempt(patientId);
}

export async function registerLinkingAttemptFailure(patientId: string) {
  const now = new Date();
  const attempt = await getLinkingAttempt(patientId);
  const nextCount = (attempt?.attemptCount ?? 0) + 1;
  const lockedUntil =
    nextCount >= LINKING_CODE_MAX_ATTEMPTS
      ? new Date(now.getTime() + LINKING_CODE_LOCKOUT_MINUTES * 60 * 1000)
      : null;
  await upsertLinkingAttempt({ patientId, attemptCount: nextCount, lockedUntil });
  if (lockedUntil) {
    throw new AuthError("Too many attempts", 429);
  }
}

export async function resetLinkingAttempts(patientId: string) {
  await resetLinkingAttempt(patientId);
}
