import { createHash, randomInt } from "crypto";
import { AuthError } from "../middleware/auth";
import { prisma } from "../repositories/prisma";
import { FREE_PATIENT_LIMIT } from "./patientLimitConstants";
import { PatientLimitError } from "../errors/patientLimitError";
import { listPatientRecordsByCaregiver, getPatientRecordForCaregiver } from "../repositories/patientRepo";
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
    // Count ACTIVE links inside the transaction for atomicity (race-condition safe)
    const activeCount = await tx.caregiverPatientLink.count({
      where: { caregiverId: caregiverUserId, status: "ACTIVE" }
    });

    // If at or over the free limit, check entitlement before proceeding
    if (activeCount >= FREE_PATIENT_LIMIT) {
      const entitlement = await tx.caregiverEntitlement.findFirst({
        where: { caregiverId: caregiverUserId, status: "ACTIVE" }
      });
      if (!entitlement) {
        throw new PatientLimitError(FREE_PATIENT_LIMIT, activeCount);
      }
    }

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
  const link = await prisma.caregiverPatientLink.findFirst({
    where: { caregiverId: caregiverUserId, patientId }
  });
  if (!link) {
    throw new AuthError("Not found", 404);
  }
  if (link.status !== "ACTIVE" || link.revokedAt) {
    await prisma.caregiverPatientLink.update({
      where: { id: link.id },
      data: { status: "ACTIVE", revokedAt: null }
    });
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

export async function deletePatientForCaregiver(
  caregiverUserId: string,
  patientId: string
) {
  const patient = await getPatientRecordForCaregiver(patientId, caregiverUserId);
  if (!patient) {
    throw new AuthError("Not found", 404);
  }

  await prisma.$transaction(async (tx) => {
    // Delete all related records in dependency order
    await tx.inventoryAlertEvent.deleteMany({ where: { patientId } });
    await tx.medicationInventoryAdjustment.deleteMany({ where: { patientId } });
    await tx.prnDoseRecord.deleteMany({ where: { patientId } });
    await tx.doseRecord.deleteMany({ where: { patientId } });
    await tx.doseRecordEvent.deleteMany({ where: { patientId } });
    await tx.regimen.deleteMany({ where: { patientId } });
    await tx.medication.deleteMany({ where: { patientId } });
    await tx.patientSession.deleteMany({ where: { patientId } });
    await tx.linkingCode.deleteMany({ where: { patientId } });
    await tx.linkingAttempt.deleteMany({ where: { patientId } });
    await tx.caregiverPatientLink.deleteMany({ where: { patientId } });
    await tx.patient.delete({ where: { id: patientId } });
  });

  return { deleted: true };
}
