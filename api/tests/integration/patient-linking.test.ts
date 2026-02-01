import { describe, expect, it } from "vitest";
import { LINKING_CODE_LENGTH, LINKING_CODE_TTL_MINUTES } from "../../src/services/linkingConstants";
import { validatePatientCreate } from "../../src/validators/patient";

type Patient = { id: string; displayName: string };
type LinkingCode = { patientId: string; code: string; expiresAt: Date };

function createPatient(displayName: string, store: Patient[]) {
  const { errors, displayName: normalized } = validatePatientCreate({ displayName });
  if (errors.length) {
    throw new Error(errors.join(","));
  }
  const created = { id: `patient-${store.length + 1}`, displayName: normalized };
  store.push(created);
  return created;
}

function listPatients(store: Patient[]) {
  return store;
}

function issueLinkingCode(patientId: string, patients: Patient[], codes: LinkingCode[]) {
  const patient = patients.find((entry) => entry.id === patientId);
  if (!patient) {
    throw new Error("not_found");
  }
  const code = "2".repeat(LINKING_CODE_LENGTH);
  const expiresAt = new Date(Date.now() + LINKING_CODE_TTL_MINUTES * 60 * 1000);
  const issued = { patientId, code, expiresAt };
  codes.push(issued);
  return issued;
}

describe("patient linking integration", () => {
  it("creates, lists, and issues linking code", () => {
    const patients: Patient[] = [];
    const codes: LinkingCode[] = [];

    const created = createPatient("Care Recipient", patients);
    const listed = listPatients(patients);
    const issued = issueLinkingCode(created.id, patients, codes);

    expect(listed).toHaveLength(1);
    expect(listed[0]).toEqual(created);
    expect(issued.code).toHaveLength(LINKING_CODE_LENGTH);
    expect(issued.expiresAt.getTime()).toBeGreaterThan(Date.now());
  });
});
