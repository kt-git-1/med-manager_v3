import { describe, expect, it, vi } from "vitest";
import {
  DatabasePatientSessionVerifier,
  hashPatientSessionToken
} from "../../src/auth/patientSessionVerifier";
import { findActivePatientSessionByTokenHash } from "../../src/repositories/patientSessionRepo";

vi.mock("../../src/repositories/patientSessionRepo", () => ({
  findActivePatientSessionByTokenHash: vi.fn()
}));

const mockedFind = vi.mocked(findActivePatientSessionByTokenHash);

describe("patient session verifier", () => {
  it("verifies valid token by hash", async () => {
    const token = "token-123";
    mockedFind.mockResolvedValueOnce({
      id: "session-1",
      patientId: "patient-1",
      tokenHash: hashPatientSessionToken(token),
      issuedAt: new Date(),
      expiresAt: null,
      lastRotatedAt: null,
      revokedAt: null
    });

    const verifier = new DatabasePatientSessionVerifier();
    await expect(verifier.verify(token)).resolves.toEqual({ patientId: "patient-1" });
    expect(mockedFind).toHaveBeenCalledWith(hashPatientSessionToken(token));
  });

  it("rejects expired sessions", async () => {
    const token = "token-expired";
    mockedFind.mockResolvedValueOnce({
      id: "session-2",
      patientId: "patient-2",
      tokenHash: hashPatientSessionToken(token),
      issuedAt: new Date(Date.now() - 1000),
      expiresAt: new Date(Date.now() - 1),
      lastRotatedAt: null,
      revokedAt: null
    });

    const verifier = new DatabasePatientSessionVerifier();
    await expect(verifier.verify(token)).rejects.toThrow("Expired patient session token");
  });

  it("rejects missing sessions", async () => {
    mockedFind.mockResolvedValueOnce(null);
    const verifier = new DatabasePatientSessionVerifier();
    await expect(verifier.verify("missing")).rejects.toThrow("Invalid patient session token");
  });
});
