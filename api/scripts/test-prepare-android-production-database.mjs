import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { mkdtemp, rm } from "node:fs/promises";

import {
  prepareProductionDatabaseUrl,
  ProductionDatabaseTlsError,
  SUPABASE_PRODUCTION_CA_DER_SHA256,
  writeGitHubEnvironment
} from "./prepare-android-production-database.mjs";

const SOURCE_URL = new URL("../src/repositories/databaseTls.ts", import.meta.url);
const source = await readFile(SOURCE_URL, "utf8");
const pemMatch = source.match(/export const SUPABASE_PRODUCTION_CA_PEM = `([\s\S]*?)`;/);
assert(pemMatch, "Pinned runtime Supabase CA was not found");
const certificateBytes = Buffer.from(pemMatch[1], "utf8");
assert(source.includes(`"${SUPABASE_PRODUCTION_CA_DER_SHA256}"`));

const certificatePath = "/tmp/supabase-prod-ca.crt";
const base = {
  databaseUrl:
    "postgresql://audit:secret@aws-0-region.pooler.supabase.com:5432/postgres" +
    "?application_name=medmanager&sslmode=require&uselibpqcompat=true",
  certificatePath,
  certificateBytes,
  now: new Date("2026-08-17T00:00:00Z")
};

const prepared = prepareProductionDatabaseUrl(base);
const parsed = new URL(prepared.databaseUrl);
assert.equal(parsed.searchParams.get("application_name"), "medmanager");
assert.equal(parsed.searchParams.get("sslmode"), "verify-full");
assert.equal(parsed.searchParams.get("sslrootcert"), certificatePath);
assert.equal(parsed.searchParams.has("uselibpqcompat"), false);
assert.equal(prepared.certificateSha256, SUPABASE_PRODUCTION_CA_DER_SHA256);

const rejected = [
  ["missing URL", { databaseUrl: "" }],
  ["invalid URL", { databaseUrl: "not-a-url" }],
  ["wrong protocol", { databaseUrl: "https://db.project.supabase.co/postgres" }],
  ["loopback", { databaseUrl: "postgresql://audit:secret@localhost/postgres" }],
  ["other provider", { databaseUrl: "postgresql://audit:secret@db.example.com/postgres" }],
  ["relative CA", { certificatePath: "relative.crt" }],
  ["invalid CA", { certificateBytes: Buffer.from("not a certificate") }],
  ["wrong CA", { expectedCertificateSha256: "0".repeat(64) }],
  ["expired CA", { now: new Date("2032-01-01T00:00:00Z") }]
];
for (const [label, changes] of rejected) {
  assert.throws(
    () => prepareProductionDatabaseUrl({ ...base, ...changes }),
    ProductionDatabaseTlsError,
    label
  );
}

const directory = await mkdtemp(join(tmpdir(), "medmanager-production-db-tls-"));
try {
  const githubEnvironmentPath = join(directory, "github-env");
  await writeFile(githubEnvironmentPath, "", { mode: 0o600 });
  const previous = process.env.GITHUB_ACTIONS;
  process.env.GITHUB_ACTIONS = "true";
  try {
    await writeGitHubEnvironment({
      githubEnvironmentPath,
      databaseUrl: prepared.databaseUrl,
      certificatePath
    });
  } finally {
    if (previous === undefined) delete process.env.GITHUB_ACTIONS;
    else process.env.GITHUB_ACTIONS = previous;
  }
  const exported = await readFile(githubEnvironmentPath, "utf8");
  assert.match(exported, /DIRECT_URL<<MED_MANAGER_DATABASE_[0-9a-f]{24}/);
  assert.match(exported, /DATABASE_URL<<MED_MANAGER_DATABASE_[0-9a-f]{24}/);
  assert.match(exported, /ANDROID_PRODUCTION_DATABASE_CA_PATH<</);
  assert.equal(exported.split(prepared.databaseUrl).length - 1, 2);
  assert.equal(exported.split(certificatePath).length - 1, 1);
} finally {
  await rm(directory, { recursive: true, force: true });
}

console.log(
  `Android production database TLS contract passed: accepted=1 rejected=${rejected.length} ` +
    `githubEnvironment=passed valuesExposed=0`
);
