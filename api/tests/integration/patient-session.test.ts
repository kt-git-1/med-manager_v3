import { describe, expect, it } from "vitest";
import { LINKING_CODE_LENGTH } from "../../src/services/linkingConstants";
import { validateLinkCodeInput } from "../../src/validators/patient";

type SessionStore = Map<string, string>;

function issueLinkCode() {
  return "7".repeat(LINKING_CODE_LENGTH);
}

function exchangeLinkCode(code: string, validCodes: Set<string>, sessions: SessionStore) {
  if (!validCodes.has(code)) {
    throw new Error("not_found");
  }
  validCodes.delete(code);
  const token = `patient-session-${Date.now()}`;
  sessions.set(token, token);
  return token;
}


function exchangeLinkCodeRequest(body: { code: unknown }, validCodes: Set<string>, sessions: SessionStore) {
  const { errors, code } = validateLinkCodeInput({ code: body.code });
  if (errors.length) {
    return { status: 422, error: "validation", messages: errors };
  }
  const token = exchangeLinkCode(code, validCodes, sessions);
  return { status: 200, data: { patientSessionToken: token } };
}

function refreshSessionToken(currentToken: string, sessions: SessionStore) {
  if (!sessions.has(currentToken)) {
    throw new Error("unauthorized");
  }
  sessions.delete(currentToken);
  const nextToken = `patient-session-${Date.now()}-rotated`;
  sessions.set(nextToken, nextToken);
  return nextToken;
}

describe("patient session integration", () => {

  it("rejects non-string link code types", () => {
    const validCodes = new Set<string>([issueLinkCode()]);
    const sessions: SessionStore = new Map();

    for (const body of [{ code: 123456 }, { code: null }, { code: { value: "777777" } }]) {
      const response = exchangeLinkCodeRequest(body, validCodes, sessions);
      expect(response.status).toBe(422);
      expect(response.error).toBe("validation");
    }
  });

  it("exchanges code and rotates token", () => {
    const validCodes = new Set<string>();
    const sessions: SessionStore = new Map();
    const code = issueLinkCode();
    validCodes.add(code);

    const token = exchangeLinkCode(code, validCodes, sessions);
    expect(sessions.has(token)).toBe(true);

    const rotated = refreshSessionToken(token, sessions);
    expect(sessions.has(rotated)).toBe(true);
    expect(sessions.has(token)).toBe(false);
  });
});
