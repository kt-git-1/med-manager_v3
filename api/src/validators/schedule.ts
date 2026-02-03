const timeRegex = /^([01]\d|2[0-3]):[0-5]\d$/;

export function validateTimes(times: string[]) {
  const errors: string[] = [];
  if (!times || times.length === 0) {
    errors.push("times must have at least one entry");
    return errors;
  }
  const seen = new Set<string>();
  for (const time of times) {
    if (!timeRegex.test(time)) {
      errors.push(`invalid time format: ${time}`);
    }
    if (seen.has(time)) {
      errors.push(`duplicate time: ${time}`);
    }
    seen.add(time);
  }
  return errors;
}

export function validateDateRange(startDate?: Date, endDate?: Date) {
  if (startDate && endDate && startDate > endDate) {
    return ["startDate must be <= endDate"];
  }
  return [];
}

export function validateYearMonth(year: number, month: number) {
  const errors: string[] = [];
  if (!Number.isInteger(year) || year < 1970 || year > 9999) {
    errors.push("year must be a valid four-digit number");
  }
  if (!Number.isInteger(month) || month < 1 || month > 12) {
    errors.push("month must be between 1 and 12");
  }
  return errors;
}

const dateRegex = /^\d{4}-\d{2}-\d{2}$/;

export function validateDateString(date: string) {
  const errors: string[] = [];
  if (!dateRegex.test(date)) {
    errors.push("date must be in YYYY-MM-DD format");
    return errors;
  }
  const [year, month, day] = date.split("-").map(Number);
  if (month < 1 || month > 12) {
    errors.push("date month must be between 1 and 12");
  }
  if (day < 1 || day > 31) {
    errors.push("date day must be between 1 and 31");
  }
  return errors;
}
