import { patientSessionVerifier } from "../auth/patientSessionVerifier";
import { verifySupabaseJwt } from "../auth/supabaseJwt";

export type AuthRole = "caregiver" | "patient";

export class AuthError extends Error {
  statusCode: number;

  constructor(message: string, statusCode: number) {
    super(message);
    this.statusCode = statusCode;
  }
}

export function getBearerToken(authHeader?: string): string | null {
  if (!authHeader) {
    return null;
  }
  const [scheme, token] = authHeader.split(" ");
  if (scheme !== "Bearer" || !token) {
    return null;
  }
  return token;
}

export function isCaregiverToken(token: string | null): boolean {
  return !!token && token.startsWith("caregiver-") && token !== "caregiver-placeholder";
}

export async function requireCaregiver(authHeader?: string) {
  const token = getBearerToken(authHeader);
  if (!token) {
    throw new AuthError("Unauthorized", 401);
  }
  if (!token.startsWith("caregiver-")) {
    throw new AuthError("Unauthorized", 401);
  }
  try {
    const session = verifySupabaseJwt(token);
    return { role: "caregiver" as const, caregiverUserId: session.caregiverUserId };
  } catch {
    throw new AuthError("Unauthorized", 401);
  }
}

export async function requirePatient(authHeader?: string) {
  const token = getBearerToken(authHeader);
  if (!token) {
    throw new AuthError("Unauthorized", 401);
  }
  try {
    const session = await patientSessionVerifier.verify(token);
    return { role: "patient" as const, patientId: session.patientId };
  } catch {
    throw new AuthError("Unauthorized", 401);
  }
}

export function assertPatientScope(requestedPatientId: string, sessionPatientId: string) {
  if (requestedPatientId !== sessionPatientId) {
    throw new AuthError("Not found", 404);
  }
}

export function assertCaregiverPatientScope(caregiverUserId: string, requestedPatientId: string) {
  // TODO: replace with caregiver->patient mapping check.
  if (caregiverUserId !== requestedPatientId) {
    throw new AuthError("Not found", 404);
  }
}
