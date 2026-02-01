import {
  createRegimenRecord,
  getRegimenRecord,
  stopRegimenRecord,
  updateRegimenRecord
} from "../repositories/regimenRepo";

export type RegimenCreateInput = {
  patientId: string;
  medicationId: string;
  timezone: string;
  startDate: Date;
  endDate?: Date;
  times: string[];
  daysOfWeek?: string[];
};

export type RegimenUpdateInput = Partial<RegimenCreateInput> & {
  enabled?: boolean;
};

export async function createRegimen(input: RegimenCreateInput) {
  return createRegimenRecord(input);
}

export async function updateRegimen(id: string, input: RegimenUpdateInput) {
  return updateRegimenRecord(id, input);
}

export async function stopRegimen(id: string) {
  return stopRegimenRecord(id);
}

export async function getRegimen(id: string) {
  return getRegimenRecord(id);
}
