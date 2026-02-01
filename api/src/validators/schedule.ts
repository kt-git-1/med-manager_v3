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
