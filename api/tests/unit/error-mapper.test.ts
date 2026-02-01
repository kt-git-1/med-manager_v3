import { describe, expect, it } from "vitest";
import { toHttpError } from "../../src/middleware/error";

class TestHttpError extends Error {
  statusCode: number;

  constructor(message: string, statusCode: number) {
    super(message);
    this.statusCode = statusCode;
  }
}

describe("error mapper", () => {
  it("maps known status codes", () => {
    const cases: Array<[number, string]> = [
      [401, "unauthorized"],
      [403, "forbidden"],
      [404, "not_found"],
      [409, "conflict"],
      [422, "validation_error"],
      [429, "rate_limited"]
    ];
    for (const [status, code] of cases) {
      const error = toHttpError(new TestHttpError("boom", status));
      expect(error.status).toBe(status);
      expect(error.code).toBe(code);
      expect(error.message).toBe("boom");
    }
  });

  it("falls back to internal error", () => {
    const error = toHttpError(new Error("nope"));
    expect(error.status).toBe(500);
    expect(error.code).toBe("internal_error");
  });
});
