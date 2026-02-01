import { describe, expect, it, vi } from "vitest";
import { requireCaregiver, requirePatient } from "../../src/middleware/auth";
import { patientSessionVerifier } from "../../src/auth/patientSessionVerifier";
import { verifySupabaseJwt } from "../../src/auth/supabaseJwt";

vi.mock("../../src/auth/patientSessionVerifier", () => ({
  patientSessionVerifier: {
    verify: vi.fn()
  }
}));

vi.mock("../../src/auth/supabaseJwt", () => ({
  verifySupabaseJwt: vi.fn()
}));

const mockedVerifier = vi.mocked(patientSessionVerifier);
const mockedJwt = vi.mocked(verifySupabaseJwt);

describe("auth middleware", () => {
  it("requires caregiver auth header", async () => {
    await expect(requireCaregiver()).rejects.toMatchObject({ statusCode: 401 });
  });

  it("accepts valid caregiver token", async () => {
    mockedJwt.mockResolvedValueOnce({ caregiverUserId: "caregiver-1" });
    await expect(requireCaregiver("Bearer caregiver-valid")).resolves.toEqual({
      role: "caregiver",
      caregiverUserId: "caregiver-1"
    });
  });

  it("requires patient auth header", async () => {
    await expect(requirePatient()).rejects.toMatchObject({ statusCode: 401 });
  });

  it("rejects caregiver token for patient routes", async () => {
    await expect(requirePatient("Bearer caregiver-valid")).rejects.toMatchObject({
      statusCode: 403
    });
  });

  it("accepts valid patient token", async () => {
    mockedVerifier.verify.mockResolvedValueOnce({ patientId: "patient-1" });
    await expect(requirePatient("Bearer patient-token")).resolves.toEqual({
      role: "patient",
      patientId: "patient-1"
    });
  });
});
