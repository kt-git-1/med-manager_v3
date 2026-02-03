import { describe, expect, it, vi } from "vitest";
import { assertCaregiverPatientScope, AuthError } from "../../src/middleware/auth";

vi.mock("../../src/repositories/patientRepo", () => ({
  getPatientRecordForCaregiver: vi.fn(async (patientId: string, caregiverId: string) => {
    if (patientId === "patient-1" && caregiverId === "caregiver-1") {
      return {
        id: patientId,
        caregiverId,
        displayName: "Care Recipient",
        createdAt: new Date(),
        updatedAt: new Date()
      };
    }
    return null;
  })
}));

describe("caregiver concealment integration", () => {
  it("returns not found for non-owned patient", async () => {
    await expect(
      assertCaregiverPatientScope("caregiver-1", "patient-999")
    ).rejects.toMatchObject<AuthError>({
      statusCode: 404
    });
  });

  it("allows access for owned patient", async () => {
    await expect(
      assertCaregiverPatientScope("caregiver-1", "patient-1")
    ).resolves.toBeUndefined();
  });
});
