type LogLevel = "info" | "warn" | "error";

function redact(input: string) {
  return input.replace(/Bearer\s+[A-Za-z0-9\-_\.]+/g, "Bearer ***");
}

export function log(level: LogLevel, message: string) {
  const sanitized = redact(message);
  console[level](sanitized);
}
