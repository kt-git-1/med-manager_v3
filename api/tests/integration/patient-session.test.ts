import { describe, expect, it } from "vitest";
import { LINKING_CODE_LENGTH } from "../../src/services/linkingConstants";

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
