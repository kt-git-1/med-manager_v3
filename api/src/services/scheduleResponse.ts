export type ScheduleResponseDose = {
  key: string;
  patientId: string;
  medicationId: string;
  scheduledAt: string;
  effectiveStatus?: "pending" | "taken" | "missed";
  recordedByType?: "patient" | "caregiver";
  medicationSnapshot: {
    name: string;
    dosageText: string;
    doseCountPerIntake: number;
    dosageStrengthValue: number;
    dosageStrengthUnit: string;
  };
};

export type ScheduleResponseInput = Omit<ScheduleResponseDose, "key"> & { key?: string };

export function buildScheduleResponse(doses: ScheduleResponseInput[]) {
  return {
    data: doses.map((dose) => ({
      ...dose,
      key: dose.key ?? `${dose.patientId}:${dose.medicationId}:${dose.scheduledAt}`
    }))
  };
}
