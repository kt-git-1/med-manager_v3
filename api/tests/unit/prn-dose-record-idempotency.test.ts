import { beforeEach, describe, expect, it, vi } from "vitest";
import { Prisma } from "@prisma/client";

const prismaMock = vi.hoisted(() => ({
  prnDoseRecord: {
    findUnique: vi.fn(),
    create: vi.fn()
  }
}));

vi.mock("../../src/repositories/prisma", () => ({ prisma: prismaMock }));

import { createPrnDoseRecordIdempotent } from "../../src/repositories/prnDoseRecordRepo";

const record = {
  id: "prn-1",
  patientId: "patient-1",
  medicationId: "med-1",
  clientMutationId: "11111111-1111-4111-8111-111111111111",
  takenAt: new Date("2026-08-17T00:00:00.000Z"),
  quantityTaken: 1,
  actorType: "PATIENT" as const,
  createdAt: new Date("2026-08-17T00:00:00.000Z")
};

describe("PRN record repository idempotency", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns an existing record without creating for a replayed key", async () => {
    prismaMock.prnDoseRecord.findUnique.mockResolvedValueOnce(record);

    const result = await createPrnDoseRecordIdempotent({
      patientId: record.patientId,
      medicationId: record.medicationId,
      clientMutationId: record.clientMutationId,
      takenAt: record.takenAt,
      quantityTaken: record.quantityTaken,
      actorType: record.actorType
    });

    expect(result).toEqual({ record, created: false });
    expect(prismaMock.prnDoseRecord.create).not.toHaveBeenCalled();
  });

  it("recovers the winning record when concurrent inserts hit the unique constraint", async () => {
    prismaMock.prnDoseRecord.findUnique.mockResolvedValueOnce(null).mockResolvedValueOnce(record);
    prismaMock.prnDoseRecord.create.mockRejectedValueOnce(
      new Prisma.PrismaClientKnownRequestError("unique", {
        code: "P2002",
        clientVersion: "test"
      })
    );

    const result = await createPrnDoseRecordIdempotent({
      patientId: record.patientId,
      medicationId: record.medicationId,
      clientMutationId: record.clientMutationId,
      takenAt: record.takenAt,
      quantityTaken: record.quantityTaken,
      actorType: record.actorType
    });

    expect(result).toEqual({ record, created: false });
    expect(prismaMock.prnDoseRecord.findUnique).toHaveBeenCalledTimes(2);
  });
});
