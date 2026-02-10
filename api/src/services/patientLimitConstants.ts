/**
 * Maximum number of patients a free caregiver can register.
 *
 * Premium caregivers have no limit. This constant is used both in the
 * service layer (linkingService.ts) for enforcement and in the error
 * response payload (PatientLimitError).
 */
export const FREE_PATIENT_LIMIT = 1;
