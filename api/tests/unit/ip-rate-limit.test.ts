import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  assertIpRateLimit,
  clearIpRateLimitBucketsForTests,
  getClientIp
} from "../../src/middleware/ipRateLimit";

describe("ip rate limit middleware", () => {
  beforeEach(() => {
    clearIpRateLimitBucketsForTests();
    vi.useRealTimers();
  });

  it("uses the first x-forwarded-for address", () => {
    const request = new Request("http://localhost/api/patient/link", {
      headers: { "x-forwarded-for": "203.0.113.10, 198.51.100.20" }
    });

    expect(getClientIp(request)).toBe("203.0.113.10");
  });

  it("throws a 429 error after the per-IP limit is exceeded", () => {
    const request = new Request("http://localhost/api/patient/link", {
      headers: { "x-forwarded-for": "203.0.113.11" }
    });

    assertIpRateLimit(request, "test-link", { maxRequests: 2, windowMs: 60_000 });
    assertIpRateLimit(request, "test-link", { maxRequests: 2, windowMs: 60_000 });

    expect(() =>
      assertIpRateLimit(request, "test-link", { maxRequests: 2, windowMs: 60_000 })
    ).toThrowError("Too many requests");
  });

  it("resets the bucket after the window expires", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-06-10T00:00:00.000Z"));
    const request = new Request("http://localhost/api/patient/link", {
      headers: { "x-forwarded-for": "203.0.113.12" }
    });

    assertIpRateLimit(request, "test-link", { maxRequests: 1, windowMs: 1_000 });
    expect(() =>
      assertIpRateLimit(request, "test-link", { maxRequests: 1, windowMs: 1_000 })
    ).toThrowError("Too many requests");

    vi.setSystemTime(new Date("2026-06-10T00:00:01.001Z"));

    expect(() =>
      assertIpRateLimit(request, "test-link", { maxRequests: 1, windowMs: 1_000 })
    ).not.toThrow();
  });
});
