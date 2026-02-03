import { describe, expect, it, vi } from "vitest";
import { AuthError, requirePatient } from "../../src/middleware/auth";

vi.mock("../../src/auth/patientSessionVerifier", () => ({
  patientSessionVerifier: {
    verify: vi.fn(async (token: string) => {
      if (token === "revoked-token") {
        throw new Error("Invalid patient session token");
      }
      return { patientId: "patient-1" };
    })
  }
}));

describe("patient revoke access integration", () => {
  it("rejects revoked patient session tokens", async () => {
    await expect(requirePatient("Bearer revoked-token")).rejects.toMatchObject<AuthError>({
      statusCode: 401
    });
  });

  it("accepts active patient session tokens", async () => {
    const session = await requirePatient("Bearer active-token");
    expect(session.patientId).toBe("patient-1");
  });
});
