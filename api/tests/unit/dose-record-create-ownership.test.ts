import { beforeEach, describe, expect, it, vi } from "vitest";
import { Prisma } from "@prisma/client";

const prismaMock = vi.hoisted(() => ({
  doseRecord: {
    create: vi.fn(),
    findUnique: vi.fn()
  }
}));

vi.mock("../../src/repositories/prisma", () => ({ prisma: prismaMock }));

import { createDoseRecordIfAbsent } from "../../src/repositories/doseRecordRepo";

const record = {
  id: "dose-1",
  patientId: "patient-1",
  medicationId: "med-1",
  scheduledAt: new Date("2026-08-17T00:00:00.000Z"),
  takenAt: new Date("2026-08-17T00:01:00.000Z"),
  recordedByType: "PATIENT" as const,
  recordedById: null,
  recordingGroupId: null,
  createdAt: new Date("2026-08-17T00:01:00.000Z"),
  updatedAt: new Date("2026-08-17T00:01:00.000Z")
};

describe("scheduled dose creation ownership", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("marks a new insert as the sole side-effect owner", async () => {
    prismaMock.doseRecord.create.mockResolvedValueOnce(record);

    const result = await createDoseRecordIfAbsent(record);

    expect(result).toEqual({ record, created: true });
    expect(prismaMock.doseRecord.findUnique).not.toHaveBeenCalled();
  });

  it("returns the winning row without side-effect ownership after a unique race", async () => {
    prismaMock.doseRecord.create.mockRejectedValueOnce(
      new Prisma.PrismaClientKnownRequestError("unique", {
        code: "P2002",
        clientVersion: "test"
      })
    );
    prismaMock.doseRecord.findUnique.mockResolvedValueOnce(record);

    const result = await createDoseRecordIfAbsent(record);

    expect(result).toEqual({ record, created: false });
  });
});
