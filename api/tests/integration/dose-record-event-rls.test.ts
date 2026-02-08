import { describe, expect, it, vi } from "vitest";
import { assertCaregiverPatientScope, AuthError } from "../../src/middleware/auth";
import { listDoseRecordEventsByPatient } from "../../src/repositories/doseRecordEventRepo";

const ownedPatients = new Set<string>();

vi.mock("../../src/repositories/patientRepo", () => ({
  getPatientRecordForCaregiver: vi.fn(async (patientId: string, caregiverId: string) => {
    if (caregiverId === "caregiver-1" && ownedPatients.has(patientId)) {
      return { id: patientId, caregiverId, displayName: "Care Recipient", createdAt: new Date(), updatedAt: new Date() };
    }
    return null;
  })
}));

const listEventsMock = vi.fn(async () => [
  { id: "event-1", patientId: "patient-1", createdAt: new Date() }
]);

vi.mock("../../src/repositories/doseRecordEventRepo", () => ({
  listDoseRecordEventsByPatient: (...args: unknown[]) => listEventsMock(...args)
}));

describe("dose record event RLS", () => {
  it("prevents non-owned caregivers from reading events", async () => {
    ownedPatients.clear();
    listEventsMock.mockClear();

    let thrown: unknown;
    try {
      await assertCaregiverPatientScope("caregiver-1", "patient-999");
      await listDoseRecordEventsByPatient({ patientId: "patient-999" });
    } catch (error) {
      thrown = error;
    }

    expect(thrown).toBeInstanceOf(AuthError);
    expect((thrown as AuthError).statusCode).toBe(404);
    expect(listEventsMock).not.toHaveBeenCalled();
  });
});
