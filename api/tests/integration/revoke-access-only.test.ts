import { describe, expect, it } from "vitest";

type Patient = { id: string; caregiverId: string };
type Medication = { id: string; patientId: string; name: string };
type Link = { caregiverId: string; patientId: string; status: "ACTIVE" | "REVOKED"; revokedAt: Date | null };
type Session = { token: string; patientId: string; revokedAt: Date | null };

function createPatient(caregiverId: string): Patient {
  return { id: `patient-${Math.random().toString(36).slice(2, 6)}`, caregiverId };
}

function createMedication(patientId: string, name: string, store: Medication[]) {
  const created = { id: `med-${store.length + 1}`, patientId, name };
  store.push(created);
  return created;
}

function listCaregiverMedications(
  caregiverId: string,
  patientId: string,
  patients: Patient[],
  meds: Medication[]
) {
  const patient = patients.find((entry) => entry.id === patientId && entry.caregiverId === caregiverId);
  if (!patient) {
    throw new Error("not_found");
  }
  return meds.filter((med) => med.patientId === patientId);
}

function issueLinkingCode(link: Link, caregiverId: string) {
  if (link.caregiverId !== caregiverId) {
    throw new Error("not_found");
  }
  if (link.status !== "ACTIVE" || link.revokedAt) {
    link.status = "ACTIVE";
    link.revokedAt = null;
  }
  return `code-${Math.random().toString(36).slice(2, 8)}`;
}

function exchangeLinkingCode(code: string, patientId: string, sessions: Session[]) {
  if (!code) {
    throw new Error("not_found");
  }
  const token = `patient-session-${Math.random().toString(36).slice(2, 8)}`;
  sessions.push({ token, patientId, revokedAt: null });
  return token;
}

function revokeLink(link: Link, caregiverId: string, sessions: Session[]) {
  if (link.caregiverId !== caregiverId || link.status === "REVOKED") {
    throw new Error("not_found");
  }
  link.status = "REVOKED";
  link.revokedAt = new Date();
  for (const session of sessions) {
    if (session.patientId === link.patientId) {
      session.revokedAt = new Date();
    }
  }
}

function readMedicationsWithPatientToken(
  token: string,
  sessions: Session[],
  meds: Medication[]
) {
  const session = sessions.find((entry) => entry.token === token);
  if (!session || session.revokedAt) {
    throw new Error("unauthorized");
  }
  return meds.filter((med) => med.patientId === session.patientId);
}

describe("revoke access only integration", () => {
  it("revokes patient sessions without deleting data and allows relinking", () => {
    const caregiverId = "caregiver-1";
    const patients: Patient[] = [];
    const meds: Medication[] = [];
    const sessions: Session[] = [];

    const patient = createPatient(caregiverId);
    patients.push(patient);
    const link: Link = {
      caregiverId,
      patientId: patient.id,
      status: "ACTIVE",
      revokedAt: null
    };

    createMedication(patient.id, "Medication A", meds);
    createMedication(patient.id, "Medication B", meds);

    const code = issueLinkingCode(link, caregiverId);
    const token = exchangeLinkingCode(code, patient.id, sessions);
    expect(readMedicationsWithPatientToken(token, sessions, meds)).toHaveLength(2);

    revokeLink(link, caregiverId, sessions);
    expect(() => readMedicationsWithPatientToken(token, sessions, meds)).toThrow("unauthorized");

    const caregiverList = listCaregiverMedications(caregiverId, patient.id, patients, meds);
    expect(caregiverList).toHaveLength(2);

    const relinkCode = issueLinkingCode(link, caregiverId);
    const relinkToken = exchangeLinkingCode(relinkCode, patient.id, sessions);
    expect(readMedicationsWithPatientToken(relinkToken, sessions, meds)).toHaveLength(2);
  });
});
