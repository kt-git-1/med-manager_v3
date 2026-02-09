import { LINKING_CODE_LENGTH } from "../services/linkingConstants";

export const DISPLAY_NAME_MAX_LENGTH = 50;

export type PatientCreateInput = {
  displayName: string;
};

export type LinkCodeInput = {
  code: unknown;
};

const linkCodeRegex = new RegExp(`^\\d{${LINKING_CODE_LENGTH}}$`);

export function normalizeDisplayName(displayName: string) {
  return displayName.trim();
}

export function validatePatientCreate(input: PatientCreateInput) {
  const errors: string[] = [];
  const displayName = normalizeDisplayName(input.displayName ?? "");
  if (!displayName) {
    errors.push("displayName is required");
  } else if (displayName.length > DISPLAY_NAME_MAX_LENGTH) {
    errors.push(`displayName must be <= ${DISPLAY_NAME_MAX_LENGTH} characters`);
  }
  return { errors, displayName };
}

export function normalizeLinkCode(code: unknown) {
  if (typeof code !== "string") {
    return "";
  }
  return code.trim();
}

export function validateLinkCodeInput(input: LinkCodeInput) {
  const errors: string[] = [];
  if (typeof input.code !== "string") {
    errors.push("code must be a string");
  }
  const code = normalizeLinkCode(input.code);
  if (!code) {
    errors.push("code is required");
  } else if (!linkCodeRegex.test(code)) {
    errors.push(`code must be ${LINKING_CODE_LENGTH} digits`);
  }
  return { errors, code };
}
