import { AuthError } from "./auth";
import { getClientIp } from "./ipRateLimit";
import { incrementApiRateLimit } from "../repositories/apiRateLimitRepo";

type RateLimitOptions = {
  maxRequests: number;
  windowMs: number;
};

export async function assertPersistentIpRateLimit(
  request: Request,
  keyPrefix: string,
  options: RateLimitOptions
) {
  const key = `${keyPrefix}:${getClientIp(request)}`;
  const result = await incrementApiRateLimit({ key, ...options });
  if (!result.allowed) {
    throw new AuthError("Too many requests", 429);
  }
}
