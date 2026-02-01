import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../src/repositories/prisma", () => ({
  prisma: {
    patient: {
      create: vi.fn(),
      findMany: vi.fn(),
      findUnique: vi.fn(),
      findFirst: vi.fn()
    },
    caregiverPatientLink: {
      create: vi.fn(),
      findFirst: vi.fn()
    },
    patientSession: {
      create: vi.fn(),
      updateMany: vi.fn(),
      findFirst: vi.fn()
    },
    linkingCode: {
      create: vi.fn(),
      updateMany: vi.fn(),
      findFirst: vi.fn(),
      update: vi.fn()
    },
    linkingAttempt: {
      findUnique: vi.fn(),
      upsert: vi.fn(),
      updateMany: vi.fn()
    }
  }
}));

import { prisma } from "../../src/repositories/prisma";
import {
  createPatientRecord,
  getPatientRecordById,
  getPatientRecordForCaregiver,
  listPatientRecordsByCaregiver
} from "../../src/repositories/patientRepo";
import {
  createCaregiverPatientLink,
  getActiveLinkForCaregiverPatient
} from "../../src/repositories/caregiverPatientLinkRepo";
import {
  createLinkingCodeRecord,
  findLinkingCodeByHash,
  invalidateActiveLinkingCodes,
  markLinkingCodeUsed
} from "../../src/repositories/linkingCodeRepo";
import {
  createPatientSessionRecord,
  findActivePatientSessionByTokenHash,
  revokePatientSessionByTokenHash,
  revokePatientSessionsByPatientId
} from "../../src/repositories/patientSessionRepo";

const mockedPrisma = prisma as unknown as {
  patient: {
    create: ReturnType<typeof vi.fn>;
    findMany: ReturnType<typeof vi.fn>;
    findUnique: ReturnType<typeof vi.fn>;
    findFirst: ReturnType<typeof vi.fn>;
  };
  caregiverPatientLink: {
    create: ReturnType<typeof vi.fn>;
    findFirst: ReturnType<typeof vi.fn>;
  };
  linkingCode: {
    create: ReturnType<typeof vi.fn>;
    updateMany: ReturnType<typeof vi.fn>;
    findFirst: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
  };
  patientSession: {
    create: ReturnType<typeof vi.fn>;
    updateMany: ReturnType<typeof vi.fn>;
    findFirst: ReturnType<typeof vi.fn>;
  };
};

describe("prisma repositories", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("creates patient records", async () => {
    mockedPrisma.patient.create.mockResolvedValueOnce({
      id: "patient-1",
      caregiverId: "caregiver-1",
      displayName: "Care Recipient",
      createdAt: new Date(),
      updatedAt: new Date()
    });

    await createPatientRecord({ caregiverId: "caregiver-1", displayName: "Care Recipient" });

    expect(mockedPrisma.patient.create).toHaveBeenCalledWith({
      data: { caregiverId: "caregiver-1", displayName: "Care Recipient" }
    });
  });

  it("lists patients by caregiver", async () => {
    mockedPrisma.patient.findMany.mockResolvedValueOnce([]);
    await listPatientRecordsByCaregiver("caregiver-1");
    expect(mockedPrisma.patient.findMany).toHaveBeenCalledWith({
      where: { caregiverId: "caregiver-1" },
      orderBy: { createdAt: "desc" }
    });
  });

  it("fetches patient by id and caregiver", async () => {
    mockedPrisma.patient.findFirst.mockResolvedValueOnce(null);
    await getPatientRecordForCaregiver("patient-1", "caregiver-1");
    expect(mockedPrisma.patient.findFirst).toHaveBeenCalledWith({
      where: { id: "patient-1", caregiverId: "caregiver-1" }
    });
  });

  it("fetches patient by id", async () => {
    mockedPrisma.patient.findUnique.mockResolvedValueOnce(null);
    await getPatientRecordById("patient-1");
    expect(mockedPrisma.patient.findUnique).toHaveBeenCalledWith({
      where: { id: "patient-1" }
    });
  });

  it("creates caregiver patient links", async () => {
    mockedPrisma.caregiverPatientLink.create.mockResolvedValueOnce({
      id: "link-1",
      caregiverId: "caregiver-1",
      patientId: "patient-1",
      status: "ACTIVE",
      revokedAt: null,
      createdAt: new Date(),
      updatedAt: new Date()
    });

    await createCaregiverPatientLink({ caregiverId: "caregiver-1", patientId: "patient-1" });

    expect(mockedPrisma.caregiverPatientLink.create).toHaveBeenCalledWith({
      data: { caregiverId: "caregiver-1", patientId: "patient-1", status: "ACTIVE" }
    });
  });

  it("finds active caregiver patient link", async () => {
    mockedPrisma.caregiverPatientLink.findFirst.mockResolvedValueOnce(null);
    await getActiveLinkForCaregiverPatient("caregiver-1", "patient-1");
    expect(mockedPrisma.caregiverPatientLink.findFirst).toHaveBeenCalledWith({
      where: {
        caregiverId: "caregiver-1",
        patientId: "patient-1",
        status: "ACTIVE",
        revokedAt: null
      }
    });
  });

  it("invalidates active linking codes", async () => {
    const usedAt = new Date();
    mockedPrisma.linkingCode.updateMany.mockResolvedValueOnce({ count: 0 });
    await invalidateActiveLinkingCodes("patient-1", usedAt);
    expect(mockedPrisma.linkingCode.updateMany).toHaveBeenCalledWith({
      where: { patientId: "patient-1", usedAt: null },
      data: { usedAt }
    });
  });

  it("creates linking code record", async () => {
    const expiresAt = new Date();
    mockedPrisma.linkingCode.create.mockResolvedValueOnce({
      id: "code-1",
      patientId: "patient-1",
      codeHash: "hash",
      expiresAt,
      usedAt: null,
      issuedBy: "caregiver-1",
      createdAt: new Date()
    });
    await createLinkingCodeRecord({
      patientId: "patient-1",
      codeHash: "hash",
      expiresAt,
      issuedBy: "caregiver-1"
    });
    expect(mockedPrisma.linkingCode.create).toHaveBeenCalledWith({
      data: {
        patientId: "patient-1",
        codeHash: "hash",
        expiresAt,
        issuedBy: "caregiver-1"
      }
    });
  });

  it("finds linking code by hash", async () => {
    mockedPrisma.linkingCode.findFirst.mockResolvedValueOnce(null);
    await findLinkingCodeByHash("hash");
    expect(mockedPrisma.linkingCode.findFirst).toHaveBeenCalledWith({
      where: { codeHash: "hash" },
      orderBy: { createdAt: "desc" }
    });
  });

  it("marks linking code used", async () => {
    const usedAt = new Date();
    mockedPrisma.linkingCode.update.mockResolvedValueOnce({
      id: "code-1",
      patientId: "patient-1",
      codeHash: "hash",
      expiresAt: new Date(),
      usedAt,
      issuedBy: "caregiver-1",
      createdAt: new Date()
    });
    await markLinkingCodeUsed("code-1", usedAt);
    expect(mockedPrisma.linkingCode.update).toHaveBeenCalledWith({
      where: { id: "code-1" },
      data: { usedAt }
    });
  });

  it("creates patient session record", async () => {
    const issuedAt = new Date();
    mockedPrisma.patientSession.create.mockResolvedValueOnce({
      id: "session-1",
      patientId: "patient-1",
      tokenHash: "hash",
      issuedAt,
      expiresAt: null,
      lastRotatedAt: null,
      revokedAt: null
    });
    await createPatientSessionRecord({
      patientId: "patient-1",
      tokenHash: "hash",
      issuedAt,
      lastRotatedAt: null,
      expiresAt: null
    });
    expect(mockedPrisma.patientSession.create).toHaveBeenCalledWith({
      data: {
        patientId: "patient-1",
        tokenHash: "hash",
        issuedAt,
        lastRotatedAt: null,
        expiresAt: null
      }
    });
  });

  it("finds active patient session by token hash", async () => {
    mockedPrisma.patientSession.findFirst.mockResolvedValueOnce(null);
    await findActivePatientSessionByTokenHash("hash");
    expect(mockedPrisma.patientSession.findFirst).toHaveBeenCalledWith({
      where: { tokenHash: "hash", revokedAt: null }
    });
  });

  it("revokes patient session by token hash", async () => {
    const revokedAt = new Date();
    mockedPrisma.patientSession.updateMany.mockResolvedValueOnce({ count: 1 });
    await revokePatientSessionByTokenHash("hash", revokedAt);
    expect(mockedPrisma.patientSession.updateMany).toHaveBeenCalledWith({
      where: { tokenHash: "hash" },
      data: { revokedAt }
    });
  });

  it("revokes patient sessions by patient id", async () => {
    const revokedAt = new Date();
    mockedPrisma.patientSession.updateMany.mockResolvedValueOnce({ count: 2 });
    await revokePatientSessionsByPatientId("patient-1", revokedAt);
    expect(mockedPrisma.patientSession.updateMany).toHaveBeenCalledWith({
      where: { patientId: "patient-1", revokedAt: null },
      data: { revokedAt }
    });
  });
});
