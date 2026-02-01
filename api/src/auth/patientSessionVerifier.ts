export type PatientSession = {
  patientId: string;
};

export interface PatientSessionVerifier {
  verify(token: string): Promise<PatientSession>;
}

/**
 * 001 only: temporary stub verifier.
 * Replace this with real verification when link-code exchange is implemented.
 */
export class StubPatientSessionVerifier implements PatientSessionVerifier {
  async verify(token: string): Promise<PatientSession> {
    if (!token) {
      throw new Error("Missing patient session token");
    }
    return { patientId: token };
  }
}

export const patientSessionVerifier = new StubPatientSessionVerifier();
