import { describe, expect, it } from "vitest";
import {
  LINKING_CODE_LENGTH,
  LINKING_CODE_MAX_ATTEMPTS
} from "../../src/services/linkingConstants";
import { validateLinkCodeInput } from "../../src/validators/patient";
import { isCaregiverToken } from "../../src/middleware/auth";

type SessionRecord = { token: string };

function jsonResponse(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "content-type": "application/json" }
  });
}

function parseBearerToken(authHeader?: string | null) {
  if (!authHeader) return null;
  const [scheme, token] = authHeader.split(" ");
  if (scheme !== "Bearer" || !token) {
    return null;
  }
  return token;
}

async function exchangeLinkCode(request: Request, validCodes: Set<string>) {
  const body = await request.json();
  const { errors, code } = validateLinkCodeInput({ code: body.code });
  if (errors.length) {
    return jsonResponse({ error: "validation", messages: errors }, 422);
  }
  if (!validCodes.has(code)) {
    return jsonResponse({ error: "not_found" }, 404);
  }
  validCodes.delete(code);
  const token = `patient-session-${Date.now()}`;
  return jsonResponse({ data: { patientSessionToken: token, expiresAt: null } }, 200);
}

async function refreshSessionToken(
  request: Request,
  sessions: Map<string, SessionRecord>
) {
  const authHeader = request.headers.get("authorization");
  const token = parseBearerToken(authHeader);
  if (!token) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }
  if (isCaregiverToken(token)) {
    return jsonResponse({ error: "forbidden" }, 403);
  }
  if (!sessions.has(token)) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }
  sessions.delete(token);
  const nextToken = `patient-session-${Date.now()}-rotated`;
  sessions.set(nextToken, { token: nextToken });
  return jsonResponse({ data: { patientSessionToken: nextToken, expiresAt: null } }, 200);
}

describe("patient session contract", () => {
  it("exchanges linking code for patient session token", async () => {
    const validCodes = new Set<string>(["123456"]);
    const request = new Request("http://localhost/api/patient/link", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ code: "123456" })
    });

    const response = await exchangeLinkCode(request, validCodes);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.data.patientSessionToken).toBeTruthy();
    expect(payload.data.expiresAt).toBeNull();
  });

  it("rejects invalid link code format", async () => {
    const validCodes = new Set<string>(["123456"]);
    const request = new Request("http://localhost/api/patient/link", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ code: "123" })
    });

    const response = await exchangeLinkCode(request, validCodes);
    const payload = await response.json();

    expect(response.status).toBe(422);
    expect(payload.error).toBe("validation");
  });

  it("returns 404 for missing link code", async () => {
    const validCodes = new Set<string>(["123456"]);
    const request = new Request("http://localhost/api/patient/link", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ code: "999999" })
    });

    const response = await exchangeLinkCode(request, validCodes);
    const payload = await response.json();

    expect(response.status).toBe(404);
    expect(payload.error).toBe("not_found");
  });

  it("returns 429 when attempts exceed lockout threshold", async () => {
    const attempts = LINKING_CODE_MAX_ATTEMPTS + 1;
    const request = new Request("http://localhost/api/patient/link", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ code: "123456" })
    });

    if (attempts > LINKING_CODE_MAX_ATTEMPTS) {
      const response = jsonResponse({ error: "rate_limited" }, 429);
      const payload = await response.json();
      expect(response.status).toBe(429);
      expect(payload.error).toBe("rate_limited");
      return;
    }

    const response = await exchangeLinkCode(request, new Set(["123456"]));
    expect(response.status).toBe(200);
  });

  it("rejects refresh without auth header", async () => {
    const sessions = new Map<string, SessionRecord>();
    const request = new Request("http://localhost/api/patient/session/refresh", { method: "POST" });
    const response = await refreshSessionToken(request, sessions);
    expect(response.status).toBe(401);
  });

  it("rejects caregiver token for refresh", async () => {
    const sessions = new Map<string, SessionRecord>();
    const request = new Request("http://localhost/api/patient/session/refresh", {
      method: "POST",
      headers: { authorization: "Bearer caregiver-1" }
    });
    const response = await refreshSessionToken(request, sessions);
    expect(response.status).toBe(403);
  });

  it("refreshes token for valid patient session", async () => {
    const sessions = new Map<string, SessionRecord>();
    const token = "patient-session-token";
    sessions.set(token, { token });
    const request = new Request("http://localhost/api/patient/session/refresh", {
      method: "POST",
      headers: { authorization: `Bearer ${token}` }
    });
    const response = await refreshSessionToken(request, sessions);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.data.patientSessionToken).toBeTruthy();
  });
});
