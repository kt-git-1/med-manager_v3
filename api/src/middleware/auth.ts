import { patientSessionVerifier } from "../auth/patientSessionVerifier";
import { verifySupabaseJwt } from "../auth/supabaseJwt";
import { getActiveLinkForCaregiverPatient } from "../repositories/caregiverPatientLinkRepo";

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
  const rawToken = token.slice("caregiver-".length);
  try {
    const session = await verifySupabaseJwt(rawToken);
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
  if (isCaregiverToken(token)) {
    throw new AuthError("Forbidden", 403);
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

export async function assertCaregiverPatientScope(
  caregiverUserId: string,
  requestedPatientId: string
) {
  const link = await getActiveLinkForCaregiverPatient(caregiverUserId, requestedPatientId);
  if (!link) {
    throw new AuthError("Not found", 404);
  }
}
