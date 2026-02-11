// ---------------------------------------------------------------------------
// Push register/unregister request validators
// ---------------------------------------------------------------------------

type RegisterResult = {
  errors: string[];
  token?: string;
  platform?: string;
  environment?: string;
};

type UnregisterResult = {
  errors: string[];
  token?: string;
};

const VALID_PLATFORMS = ["ios"] as const;
const VALID_ENVIRONMENTS = ["DEV", "PROD"] as const;

/**
 * Validate POST /api/push/register request body.
 * Returns parsed fields and any validation errors.
 */
export function validateRegisterRequest(body: unknown): RegisterResult {
  const errors: string[] = [];

  if (!body || typeof body !== "object") {
    return { errors: ["request body is required"] };
  }

  const obj = body as Record<string, unknown>;

  // token: required, non-empty string
  if (!obj.token || typeof obj.token !== "string" || obj.token.trim() === "") {
    errors.push("token is required");
  }

  // platform: required, must be "ios"
  if (
    !obj.platform ||
    typeof obj.platform !== "string" ||
    !(VALID_PLATFORMS as readonly string[]).includes(obj.platform)
  ) {
    errors.push("platform must be ios");
  }

  // environment: required, must be "DEV" or "PROD"
  if (
    !obj.environment ||
    typeof obj.environment !== "string" ||
    !(VALID_ENVIRONMENTS as readonly string[]).includes(obj.environment)
  ) {
    errors.push("environment must be DEV or PROD");
  }

  if (errors.length > 0) {
    return { errors };
  }

  return {
    errors: [],
    token: (obj.token as string).trim(),
    platform: obj.platform as string,
    environment: obj.environment as string
  };
}

/**
 * Validate POST /api/push/unregister request body.
 * Returns parsed token and any validation errors.
 */
export function validateUnregisterRequest(body: unknown): UnregisterResult {
  const errors: string[] = [];

  if (!body || typeof body !== "object") {
    return { errors: ["request body is required"] };
  }

  const obj = body as Record<string, unknown>;

  // token: required, non-empty string
  if (!obj.token || typeof obj.token !== "string" || obj.token.trim() === "") {
    errors.push("token is required");
  }

  if (errors.length > 0) {
    return { errors };
  }

  return {
    errors: [],
    token: (obj.token as string).trim()
  };
}
