import { describe, expect, it } from "vitest";

type SessionStore = Map<string, string>;

function revokePatientSessions(patientId: string, sessions: SessionStore) {
  for (const [token, id] of sessions.entries()) {
    if (id === patientId) {
      sessions.delete(token);
    }
  }
}

describe("patient revoke integration", () => {
  it("invalidates patient sessions on revoke", () => {
    const sessions: SessionStore = new Map();
    sessions.set("token-1", "patient-1");
    sessions.set("token-2", "patient-1");
    sessions.set("token-3", "patient-2");

    revokePatientSessions("patient-1", sessions);

    expect(sessions.has("token-1")).toBe(false);
    expect(sessions.has("token-2")).toBe(false);
    expect(sessions.has("token-3")).toBe(true);
  });
});
