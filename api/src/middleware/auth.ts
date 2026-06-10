import { patientSessionVerifier } from "../auth/patientSessionVerifier";
import { verifySupabaseJwt } from "../auth/supabaseJwt";
import { getPatientRecordForCaregiver } from "../repositories/patientRepo";
import { CAREGIVER_TOKEN_PREFIX, CAREGIVER_PLACEHOLDER_TOKEN } from "../constants";

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
  if (!token || token === CAREGIVER_PLACEHOLDER_TOKEN) {
    return false;
  }
  return token.startsWith(CAREGIVER_TOKEN_PREFIX) || isJwtLikeToken(token);
}

export async function requireCaregiver(authHeader?: string) {
  const token = getBearerToken(authHeader);
  if (!token) {
    throw new AuthError("Unauthorized", 401);
  }
  if (!isCaregiverToken(token)) {
    throw new AuthError("Unauthorized", 401);
  }
  const rawToken = token.startsWith(CAREGIVER_TOKEN_PREFIX)
    ? token.slice(CAREGIVER_TOKEN_PREFIX.length)
    : token;
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

function isJwtLikeToken(token: string) {
  return token.split(".").length === 3;
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
  const patient = await getPatientRecordForCaregiver(requestedPatientId, caregiverUserId);
  if (!patient) {
    throw new AuthError("Not found", 404);
  }
}
