import { AuthError } from "./auth";

type RateLimitBucket = {
  count: number;
  resetAt: number;
};

type RateLimitOptions = {
  maxRequests: number;
  windowMs: number;
};

const buckets = new Map<string, RateLimitBucket>();
const UNKNOWN_IP_KEY = "unknown";

export function getClientIp(request: Request): string {
  const forwardedFor = request.headers.get("x-forwarded-for");
  if (forwardedFor) {
    const firstForwardedIp = forwardedFor.split(",")[0]?.trim();
    if (firstForwardedIp) {
      return firstForwardedIp;
    }
  }

  return (
    request.headers.get("x-real-ip")?.trim() ||
    request.headers.get("cf-connecting-ip")?.trim() ||
    UNKNOWN_IP_KEY
  );
}

export function assertIpRateLimit(request: Request, keyPrefix: string, options: RateLimitOptions) {
  const now = Date.now();
  const key = `${keyPrefix}:${getClientIp(request)}`;
  const current = buckets.get(key);

  if (!current || current.resetAt <= now) {
    buckets.set(key, { count: 1, resetAt: now + options.windowMs });
    return;
  }

  if (current.count >= options.maxRequests) {
    throw new AuthError("Too many requests", 429);
  }

  current.count += 1;
}

export function clearIpRateLimitBucketsForTests() {
  buckets.clear();
}
