import assert from "node:assert/strict";
import { createHash, randomBytes } from "node:crypto";
import { readFile } from "node:fs/promises";

import pg from "pg";

import {
  auditAndroidMutationDeployment,
  DeploymentAuditError,
  validateDeploymentAuditOptions
} from "./verify-android-mutation-deployment.mjs";
import { migrationStatements, validateMigrationSql } from "./verify-android-mutation-migration.mjs";

const { Client } = pg;
const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error("DATABASE_URL is required");

const migrationUrl = new URL(
  "../prisma/migrations/20260817090000_android_mutation_idempotency/migration.sql",
  import.meta.url
);
const migrationBuffer = await readFile(migrationUrl);
const migrationSql = migrationBuffer.toString("utf8");
const statements = validateMigrationSql(migrationSql);
const checksum = createHash("sha256").update(migrationBuffer).digest("hex");
const client = new Client({ connectionString: databaseUrl });
await client.connect();

function quoted(identifier) {
  return `"${identifier}"`;
}

async function createBaseFixture(schema) {
  const namespace = quoted(schema);
  await client.query(`CREATE SCHEMA ${namespace}`);
  await client.query(`CREATE TABLE ${namespace}."_prisma_migrations" (
    id VARCHAR(36) PRIMARY KEY,
    checksum VARCHAR(64) NOT NULL,
    finished_at TIMESTAMPTZ,
    migration_name VARCHAR(255) NOT NULL,
    logs TEXT,
    rolled_back_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_steps_count INTEGER NOT NULL DEFAULT 0
  )`);
  await client.query(`CREATE TABLE ${namespace}."prn_dose_records" (
    id TEXT PRIMARY KEY,
    "patientId" TEXT NOT NULL,
    "medicationId" TEXT NOT NULL,
    "takenAt" TIMESTAMPTZ NOT NULL,
    "quantityTaken" DOUBLE PRECISION NOT NULL,
    "actorType" TEXT NOT NULL,
    "createdAt" TIMESTAMPTZ NOT NULL
  )`);
  await client.query(`CREATE TABLE ${namespace}."MedicationInventoryAdjustment" (
    id TEXT PRIMARY KEY,
    "patientId" TEXT NOT NULL,
    "medicationId" TEXT NOT NULL,
    delta DOUBLE PRECISION NOT NULL,
    reason TEXT NOT NULL,
    "actorType" TEXT NOT NULL,
    "createdAt" TIMESTAMPTZ NOT NULL
  )`);
  await client.query(`INSERT INTO ${namespace}."prn_dose_records"
    (id, "patientId", "medicationId", "takenAt", "quantityTaken", "actorType", "createdAt") VALUES
    ('prn-1', 'patient-1', 'medication-1', now(), 1, 'PATIENT', now()),
    ('prn-2', 'patient-2', 'medication-2', now(), 1, 'PATIENT', now())`);
  await client.query(`INSERT INTO ${namespace}."MedicationInventoryAdjustment"
    (id, "patientId", "medicationId", delta, reason, "actorType", "createdAt") VALUES
    ('inventory-1', 'patient-1', 'medication-1', 10, 'REFILL', 'CAREGIVER', now()),
    ('inventory-2', 'patient-2', 'medication-2', 5, 'REFILL', 'CAREGIVER', now())`);
}

async function applyMigration(schema, selectedStatements = statements) {
  await client.query(`SET search_path TO ${quoted(schema)}, public`);
  try {
    for (const statement of selectedStatements) await client.query(statement);
  } finally {
    await client.query("RESET search_path");
  }
}

async function recordMigration(schema, overrides = {}) {
  const values = {
    checksum,
    finishedAt: new Date(),
    rolledBackAt: null,
    steps: 1,
    ...overrides
  };
  await client.query(
    `INSERT INTO ${quoted(schema)}."_prisma_migrations"
      (id, checksum, finished_at, migration_name, rolled_back_at, applied_steps_count)
     VALUES ($1, $2, $3, $4, $5, $6)`,
    [
      randomBytes(16).toString("hex"),
      values.checksum,
      values.finishedAt,
      "20260817090000_android_mutation_idempotency",
      values.rolledBackAt,
      values.steps
    ]
  );
}

async function withFixture(label, body) {
  const schema = `c98_${label}_${process.pid}_${randomBytes(4).toString("hex")}`;
  await createBaseFixture(schema);
  try {
    return await body(schema);
  } finally {
    await client.query(`DROP SCHEMA IF EXISTS ${quoted(schema)} CASCADE`);
  }
}

function localOptions(schema, mode) {
  return { databaseUrl, schema, mode, confirmation: mode };
}

function rejectedPolicy(options, pattern) {
  assert.throws(
    () => validateDeploymentAuditOptions(options),
    (error) => error instanceof DeploymentAuditError && pattern.test(error.message)
  );
}

rejectedPolicy({ databaseUrl, schema: "public", mode: "invalid", confirmation: "invalid" }, /mode/);
rejectedPolicy(
  { databaseUrl, schema: "public", mode: "preflight", confirmation: "postdeploy" },
  /exactly match/
);
rejectedPolicy(
  { databaseUrl, schema: "bad-name", mode: "preflight", confirmation: "preflight" },
  /schema/
);
rejectedPolicy(
  {
    databaseUrl: "postgresql://audit:audit@db.example.invalid/database?sslmode=require",
    schema: "public",
    mode: "preflight",
    confirmation: "preflight"
  },
  /ALLOW_REMOTE/
);
rejectedPolicy(
  {
    databaseUrl: "postgresql://audit:audit@db.example.invalid/database",
    schema: "public",
    mode: "preflight",
    confirmation: "preflight",
    allowRemote: true
  },
  /verify-full/
);
rejectedPolicy(
  {
    databaseUrl:
      "postgresql://audit:audit@db.example.invalid/database?sslmode=verify-full&sslrootcert=/tmp/prod-ca.crt",
    schema: "private_fixture",
    mode: "preflight",
    confirmation: "preflight",
    allowRemote: true
  },
  /public schema/
);
rejectedPolicy(
  {
    databaseUrl: "postgresql://audit:audit@db.example.invalid/database?sslmode=verify-full",
    schema: "public",
    mode: "preflight",
    confirmation: "preflight",
    allowRemote: true
  },
  /sslrootcert/
);
rejectedPolicy(
  {
    databaseUrl:
      "postgresql://audit:audit@db.example.invalid/database?sslmode=require&uselibpqcompat=true&sslrootcert=/tmp/prod-ca.crt",
    schema: "public",
    mode: "preflight",
    confirmation: "preflight",
    allowRemote: true
  },
  /verify-full/
);
rejectedPolicy(
  {
    databaseUrl:
      "postgresql://audit:audit@db.example.invalid/database?sslmode=verify-full&sslrootcert=relative.crt",
    schema: "public",
    mode: "preflight",
    confirmation: "preflight",
    allowRemote: true
  },
  /absolute sslrootcert/
);
assert.deepEqual(
  validateDeploymentAuditOptions({
    databaseUrl:
      "postgresql://audit:audit@db.example.invalid/database?sslmode=verify-full&sslrootcert=/tmp/prod-ca.crt",
    schema: "public",
    mode: "postdeploy",
    confirmation: "postdeploy",
    allowRemote: true
  }),
  { remote: true, schema: "public" }
);

let accepted = 0;
let rejected = 9;

await withFixture("preflight_ok", async (schema) => {
  const result = await auditAndroidMutationDeployment(localOptions(schema, "preflight"));
  assert.equal(result.counts.prn_dose_records.totalRows, 2);
  assert.equal(result.counts.MedicationInventoryAdjustment.totalRows, 2);
  accepted += 1;
});

await withFixture("postdeploy_ok", async (schema) => {
  await applyMigration(schema);
  await recordMigration(schema);
  await client.query(
    `UPDATE ${quoted(schema)}."prn_dose_records"
        SET "clientMutationId" = '00000000-0000-4000-8000-000000000001'
      WHERE id = 'prn-1'`
  );
  const result = await auditAndroidMutationDeployment(localOptions(schema, "postdeploy"));
  assert.equal(result.counts.prn_dose_records.keyedRows, 1);
  assert.equal(result.counts.MedicationInventoryAdjustment.keyedRows, 0);
  accepted += 1;
});

async function rejectedFixture(label, setup, mode, pattern) {
  await withFixture(label, async (schema) => {
    await setup(schema);
    await assert.rejects(
      auditAndroidMutationDeployment(localOptions(schema, mode)),
      (error) => error instanceof DeploymentAuditError && pattern.test(error.message)
    );
    rejected += 1;
  });
}

await rejectedFixture(
  "preflight_recorded",
  (schema) => recordMigration(schema),
  "preflight",
  /already recorded/
);
await rejectedFixture(
  "preflight_partial",
  (schema) => applyMigration(schema, migrationStatements(migrationSql).slice(0, 1)),
  "preflight",
  /columns already exist/
);
await rejectedFixture("postdeploy_missing", async () => undefined, "postdeploy", /exactly one/);
await rejectedFixture(
  "postdeploy_checksum",
  async (schema) => {
    await applyMigration(schema);
    await recordMigration(schema, { checksum: "0".repeat(64) });
  },
  "postdeploy",
  /checksum/
);
await rejectedFixture(
  "postdeploy_rollback",
  async (schema) => {
    await applyMigration(schema);
    await recordMigration(schema, { rolledBackAt: new Date() });
  },
  "postdeploy",
  /rolled back/
);
await rejectedFixture(
  "postdeploy_index",
  async (schema) => {
    await applyMigration(schema, statements.slice(0, 3));
    await recordMigration(schema);
  },
  "postdeploy",
  /Both mutation unique indexes/
);

await client.end();
assert.equal(accepted, 2);
assert.equal(rejected, 15);
console.log(
  `Android mutation deployment audit contract passed: accepted=${accepted} rejected=${rejected} cleanup=passed`
);
