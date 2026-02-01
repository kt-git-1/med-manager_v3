import { validateDateRange, validateTimes } from "./schedule";

export type RegimenInput = {
  timezone: string;
  times: string[];
  startDate: Date;
  endDate?: Date;
  daysOfWeek?: string[];
};

const allowedDays = new Set(["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]);

export function validateRegimen(input: RegimenInput) {
  const errors: string[] = [];
  if (!input.timezone) {
    errors.push("timezone is required");
  }
  errors.push(...validateTimes(input.times));
  errors.push(...validateDateRange(input.startDate, input.endDate));
  if (input.daysOfWeek) {
    for (const day of input.daysOfWeek) {
      if (!allowedDays.has(day)) {
        errors.push(`invalid dayOfWeek: ${day}`);
      }
    }
  }
  return errors;
}
