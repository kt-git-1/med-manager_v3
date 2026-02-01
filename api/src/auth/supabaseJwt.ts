export type CaregiverSession = {
  caregiverUserId: string;
};

export function verifySupabaseJwt(token: string): CaregiverSession {
  if (!token) {
    throw new Error("Missing token");
  }
  // TODO: replace with real Supabase JWT verification.
  return { caregiverUserId: "caregiver-placeholder" };
}
