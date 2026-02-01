import {
  archiveMedicationRecord,
  createMedicationRecord,
  getMedicationRecord,
  listMedicationRecords,
  updateMedicationRecord
} from "../repositories/medicationRepo";
import type { Medication } from "@prisma/client";

export type MedicationCreateInput = {
  patientId: string;
  name: string;
  dosageText: string;
  doseCountPerIntake: number;
  dosageStrengthValue: number;
  dosageStrengthUnit: string;
  notes?: string;
  startDate: Date;
  endDate?: Date;
  inventoryCount?: number;
  inventoryUnit?: string;
};

export type MedicationUpdateInput = Partial<MedicationCreateInput> & {
  isActive?: boolean;
};

export async function createMedication(input: MedicationCreateInput) {
  return createMedicationRecord(input);
}

export async function listMedications(patientId: string): Promise<Medication[]> {
  return listMedicationRecords(patientId);
}

export async function getMedication(id: string): Promise<Medication | null> {
  return getMedicationRecord(id);
}

export async function updateMedication(id: string, input: MedicationUpdateInput) {
  return updateMedicationRecord(id, input);
}

export async function archiveMedication(id: string) {
  return archiveMedicationRecord(id);
}
