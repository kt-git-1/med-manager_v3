export function validateDeviceTokenRegister(body: unknown): {
  errors: string[];
  token: string;
  platform: string;
} {
  const errors: string[] = [];
  let token = "";
  let platform = "ios";

  if (!body || typeof body !== "object") {
    errors.push("Request body is required");
    return { errors, token, platform };
  }

  const b = body as Record<string, unknown>;

  if (typeof b.token !== "string" || b.token.trim().length === 0) {
    errors.push("token is required");
  } else {
    token = b.token.trim();
  }

  if (b.platform !== undefined) {
    if (typeof b.platform !== "string" || !["ios", "android"].includes(b.platform)) {
      errors.push("platform must be 'ios' or 'android'");
    } else {
      platform = b.platform;
    }
  }

  return { errors, token, platform };
}

export function validateDeviceTokenDelete(body: unknown): {
  errors: string[];
  token: string;
} {
  const errors: string[] = [];
  let token = "";

  if (!body || typeof body !== "object") {
    errors.push("Request body is required");
    return { errors, token };
  }

  const b = body as Record<string, unknown>;

  if (typeof b.token !== "string" || b.token.trim().length === 0) {
    errors.push("token is required");
  } else {
    token = b.token.trim();
  }

  return { errors, token };
}
