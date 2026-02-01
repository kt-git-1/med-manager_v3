import { createHash } from "crypto";
import { findActivePatientSessionByTokenHash } from "../repositories/patientSessionRepo";

export type PatientSession = {
  patientId: string;
};

export interface PatientSessionVerifier {
  verify(token: string): Promise<PatientSession>;
}

export function hashPatientSessionToken(token: string) {
  return createHash("sha256").update(token).digest("hex");
}

export class DatabasePatientSessionVerifier implements PatientSessionVerifier {
  async verify(token: string): Promise<PatientSession> {
    if (!token) {
      throw new Error("Missing patient session token");
    }
    const tokenHash = hashPatientSessionToken(token);
    const session = await findActivePatientSessionByTokenHash(tokenHash);
    if (!session) {
      throw new Error("Invalid patient session token");
    }
    if (session.expiresAt && session.expiresAt <= new Date()) {
      throw new Error("Expired patient session token");
    }
    return { patientId: session.patientId };
  }
}

export const patientSessionVerifier = new DatabasePatientSessionVerifier();
