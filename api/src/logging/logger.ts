type LogLevel = "info" | "warn" | "error";

function redact(input: string) {
  return input
    .replace(/Bearer\s+[A-Za-z0-9\-_\.]+/g, "Bearer ***")
    .replace(/"patientSessionToken"\s*:\s*"[^"]+"/g, "\"patientSessionToken\":\"***\"")
    .replace(/"code"\s*:\s*"\d{6}"/g, "\"code\":\"***\"")
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "***@***");
}

export function log(level: LogLevel, message: string) {
  const sanitized = redact(message);
  console[level](sanitized);
}
