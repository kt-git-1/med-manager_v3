import { createHash, X509Certificate } from "node:crypto";

import { describe, expect, it } from "vitest";

import {
  createDatabasePoolConfig,
  DatabaseTlsError,
  SUPABASE_PRODUCTION_CA_PEM,
  SUPABASE_PRODUCTION_CA_DER_SHA256
} from "../../src/repositories/databaseTls";

describe("database TLS policy", () => {
  it("pins the reviewed Supabase root certificate", () => {
    const certificate = new X509Certificate(SUPABASE_PRODUCTION_CA_PEM);
    expect(createHash("sha256").update(certificate.raw).digest("hex")).toBe(
      SUPABASE_PRODUCTION_CA_DER_SHA256
    );
    expect(certificate.ca).toBe(true);
    expect(certificate.subject).toContain("CN=Supabase Root 2021 CA");
    expect(certificate.issuer).toBe(certificate.subject);
    expect(new Date(certificate.validFrom).toISOString()).toBe("2021-04-28T10:56:53.000Z");
    expect(new Date(certificate.validTo).toISOString()).toBe("2031-04-26T10:56:53.000Z");
  });

  it("forces CA and hostname verification for a Supabase pooler", () => {
    const config = createDatabasePoolConfig(
      "postgresql://user:password@aws-0-region.pooler.supabase.com:5432/postgres" +
        "?application_name=medmanager&sslmode=require&uselibpqcompat=true&sslrootcert=/tmp/weak.crt",
      "production"
    );
    const normalized = new URL(String(config.connectionString));
    expect(normalized.searchParams.get("application_name")).toBe("medmanager");
    expect(normalized.searchParams.has("sslmode")).toBe(false);
    expect(normalized.searchParams.has("uselibpqcompat")).toBe(false);
    expect(normalized.searchParams.has("sslrootcert")).toBe(false);
    expect(config.ssl).toEqual({
      ca: SUPABASE_PRODUCTION_CA_PEM,
      rejectUnauthorized: true
    });
  });

  it("forces the same policy for direct Supabase connections", () => {
    const config = createDatabasePoolConfig(
      "postgresql://user:password@db.project.supabase.co:5432/postgres",
      "development"
    );
    expect(config.ssl).toEqual({
      ca: SUPABASE_PRODUCTION_CA_PEM,
      rejectUnauthorized: true
    });
  });

  it("keeps loopback databases available for production builds and isolated tests", () => {
    const url = "postgresql://test:test@127.0.0.1:5432/test";
    expect(createDatabasePoolConfig(url, "production")).toEqual({ connectionString: url });
  });

  it("allows a non-Supabase remote fixture only outside production", () => {
    const url = "postgresql://test:test@db.example.invalid:5432/test";
    expect(createDatabasePoolConfig(url, "test")).toEqual({ connectionString: url });
    expect(() => createDatabasePoolConfig(url, "production")).toThrow(DatabaseTlsError);
  });

  it.each([
    [undefined, /required/],
    ["", /required/],
    ["not-a-url", /valid PostgreSQL/],
    ["https://db.project.supabase.co/postgres", /use PostgreSQL/]
  ])("rejects an invalid database URL", (url, message) => {
    expect(() => createDatabasePoolConfig(url, "production")).toThrow(message);
  });
});
