import { randomBytes } from "node:crypto";
import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

import pg from "pg";

const { Client } = pg;

const MIGRATION_URL = new URL(
  "../prisma/migrations/20260817090000_android_mutation_idempotency/migration.sql",
  import.meta.url
);

export const EXPECTED_MIGRATION_STATEMENTS = [
  'ALTER TABLE "prn_dose_records" ADD COLUMN "clientMutationId" TEXT',
  'ALTER TABLE "MedicationInventoryAdjustment" ADD COLUMN "clientMutationId" TEXT',
  'CREATE UNIQUE INDEX "prn_dose_records_patientId_clientMutationId_key" ON "prn_dose_records"("patientId", "clientMutationId")',
  'CREATE UNIQUE INDEX "MedicationInventoryAdjustment_patientId_clientMutationId_key" ON "MedicationInventoryAdjustment"("patientId", "clientMutationId")'
];

function normalizeStatement(statement) {
  return statement.replace(/\s+/g, " ").trim();
}

export function migrationStatements(sql) {
  const withoutComments = sql
    .split("\n")
    .filter((line) => !line.trimStart().startsWith("--"))
    .join("\n");
  return withoutComments.split(";").map(normalizeStatement).filter(Boolean);
}

export function validateMigrationSql(sql) {
  const statements = migrationStatements(sql);
  if (
    statements.length !== EXPECTED_MIGRATION_STATEMENTS.length ||
    statements.some((statement, index) => statement !== EXPECTED_MIGRATION_STATEMENTS[index])
  ) {
    throw new Error("Android mutation migration is not the exact reviewed additive contract");
  }
  return statements;
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function requireLocalDatabase(databaseUrl) {
  const parsed = new URL(databaseUrl);
  const localHosts = new Set(["localhost", "127.0.0.1", "[::1]", "::1"]);
  if (!localHosts.has(parsed.hostname) && process.env.ALLOW_REMOTE_MIGRATION_CONTRACT !== "1") {
    throw new Error("Migration contract refuses a non-local database");
  }
}

async function verifyColumns(client, schema) {
  const result = await client.query(
    `SELECT table_name, column_name, data_type, is_nullable, column_default
       FROM information_schema.columns
      WHERE table_schema = $1
        AND (table_name, column_name) IN (
          ('prn_dose_records', 'clientMutationId'),
          ('MedicationInventoryAdjustment', 'clientMutationId')
        )
      ORDER BY table_name`,
    [schema]
  );
  assert(result.rows.length === 2, "Expected both clientMutationId columns");
  for (const row of result.rows) {
    assert(row.column_name === "clientMutationId", "Unexpected migration column name");
    assert(row.data_type === "text", "Mutation identifier must remain text");
    assert(row.is_nullable === "YES", "Legacy requests require a nullable mutation identifier");
    assert(row.column_default === null, "Migration column must not rewrite rows with a default");
  }
}

async function verifyIndexes(client, schema) {
  const result = await client.query(
    `SELECT index_class.relname AS index_name,
            index_meta.indisunique AS is_unique,
            array_agg(attribute.attname::text ORDER BY key_column.ordinality) AS columns
       FROM pg_class AS table_class
       JOIN pg_namespace AS namespace ON namespace.oid = table_class.relnamespace
       JOIN pg_index AS index_meta ON index_meta.indrelid = table_class.oid
       JOIN pg_class AS index_class ON index_class.oid = index_meta.indexrelid
       CROSS JOIN LATERAL unnest(index_meta.indkey)
         WITH ORDINALITY AS key_column(attnum, ordinality)
       JOIN pg_attribute AS attribute
         ON attribute.attrelid = table_class.oid AND attribute.attnum = key_column.attnum
      WHERE namespace.nspname = $1
        AND index_class.relname = ANY($2::text[])
      GROUP BY index_class.relname, index_meta.indisunique
      ORDER BY index_class.relname`,
    [
      schema,
      [
        "prn_dose_records_patientId_clientMutationId_key",
        "MedicationInventoryAdjustment_patientId_clientMutationId_key"
      ]
    ]
  );
  const expected = new Map([
    ["prn_dose_records_patientId_clientMutationId_key", ["patientId", "clientMutationId"]],
    [
      "MedicationInventoryAdjustment_patientId_clientMutationId_key",
      ["patientId", "clientMutationId"]
    ]
  ]);
  assert(result.rows.length === expected.size, "Expected both patient-scoped unique indexes");
  for (const row of result.rows) {
    assert(row.is_unique === true, "Mutation index must remain unique");
    assert(
      JSON.stringify(row.columns) === JSON.stringify(expected.get(row.index_name)),
      "Mutation index column order drifted"
    );
  }
}

async function verifyLegacyRows(client) {
  const prn = await client.query(
    `SELECT id, "patientId", "medicationId", "quantityTaken", "clientMutationId"
       FROM "prn_dose_records" ORDER BY id`
  );
  assert(prn.rows.length === 3, "PRN legacy row count changed during migration");
  assert(
    prn.rows.every((row) => row.clientMutationId === null),
    "PRN legacy rows must remain null"
  );
  assert(
    prn.rows.map((row) => row.id).join(",") === "prn-1,prn-2,prn-3",
    "PRN legacy identities changed"
  );

  const inventory = await client.query(
    `SELECT id, "patientId", "medicationId", delta, "clientMutationId"
       FROM "MedicationInventoryAdjustment" ORDER BY id`
  );
  assert(inventory.rows.length === 3, "Inventory legacy row count changed during migration");
  assert(
    inventory.rows.every((row) => row.clientMutationId === null),
    "Inventory legacy rows must remain null"
  );
  assert(
    inventory.rows.map((row) => row.id).join(",") === "inventory-1,inventory-2,inventory-3",
    "Inventory legacy identities changed"
  );
}

async function expectDuplicate(client, insertSql, values, constraint) {
  await client.query("SAVEPOINT duplicate_attempt");
  try {
    await client.query(insertSql, values);
  } catch (error) {
    assert(error?.code === "23505", "Duplicate mutation must fail with unique violation");
    assert(error?.constraint === constraint, "Duplicate mutation failed on the wrong constraint");
    await client.query("ROLLBACK TO SAVEPOINT duplicate_attempt");
    await client.query("RELEASE SAVEPOINT duplicate_attempt");
    return;
  }
  throw new Error("Duplicate patient mutation identifier unexpectedly succeeded");
}

async function verifyPrnUniqueness(client) {
  const insertSql = `INSERT INTO "prn_dose_records"
    (id, "patientId", "medicationId", "clientMutationId", "takenAt", "quantityTaken", "actorType", "createdAt")
    VALUES ($1, $2, $3, $4, now(), 1, 'PATIENT', now())`;
  const mutationId = "00000000-0000-4000-8000-000000000001";
  await client.query(insertSql, ["prn-new-1", "patient-1", "medication-1", mutationId]);
  await client.query(insertSql, ["prn-null-1", "patient-1", "medication-1", null]);
  await client.query(insertSql, ["prn-null-2", "patient-1", "medication-1", null]);
  await client.query(insertSql, ["prn-other-patient", "patient-2", "medication-1", mutationId]);
  await expectDuplicate(
    client,
    insertSql,
    ["prn-duplicate", "patient-1", "medication-1", mutationId],
    "prn_dose_records_patientId_clientMutationId_key"
  );
}

async function verifyInventoryUniqueness(client) {
  const insertSql = `INSERT INTO "MedicationInventoryAdjustment"
    (id, "patientId", "medicationId", "clientMutationId", delta, reason, "actorType", "createdAt")
    VALUES ($1, $2, $3, $4, 10, 'REFILL', 'CAREGIVER', now())`;
  const mutationId = "00000000-0000-4000-8000-000000000002";
  await client.query(insertSql, ["inventory-new-1", "patient-1", "medication-1", mutationId]);
  await client.query(insertSql, ["inventory-null-1", "patient-1", "medication-1", null]);
  await client.query(insertSql, ["inventory-null-2", "patient-1", "medication-1", null]);
  await client.query(insertSql, [
    "inventory-other-patient",
    "patient-2",
    "medication-1",
    mutationId
  ]);
  await expectDuplicate(
    client,
    insertSql,
    ["inventory-duplicate", "patient-1", "medication-1", mutationId],
    "MedicationInventoryAdjustment_patientId_clientMutationId_key"
  );
}

async function createLegacyFixture(client) {
  await client.query(`CREATE TABLE "prn_dose_records" (
    id TEXT PRIMARY KEY,
    "patientId" TEXT NOT NULL,
    "medicationId" TEXT NOT NULL,
    "takenAt" TIMESTAMPTZ NOT NULL,
    "quantityTaken" DOUBLE PRECISION NOT NULL,
    "actorType" TEXT NOT NULL,
    "createdAt" TIMESTAMPTZ NOT NULL
  )`);
  await client.query(`CREATE TABLE "MedicationInventoryAdjustment" (
    id TEXT PRIMARY KEY,
    "patientId" TEXT NOT NULL,
    "medicationId" TEXT NOT NULL,
    delta DOUBLE PRECISION NOT NULL,
    reason TEXT NOT NULL,
    "actorType" TEXT NOT NULL,
    "actorId" TEXT,
    "createdAt" TIMESTAMPTZ NOT NULL
  )`);
  await client.query(`INSERT INTO "prn_dose_records"
    (id, "patientId", "medicationId", "takenAt", "quantityTaken", "actorType", "createdAt") VALUES
    ('prn-1', 'patient-1', 'medication-1', now(), 1, 'PATIENT', now()),
    ('prn-2', 'patient-1', 'medication-1', now(), 2, 'CAREGIVER', now()),
    ('prn-3', 'patient-2', 'medication-2', now(), 1, 'PATIENT', now())`);
  await client.query(`INSERT INTO "MedicationInventoryAdjustment"
    (id, "patientId", "medicationId", delta, reason, "actorType", "createdAt") VALUES
    ('inventory-1', 'patient-1', 'medication-1', 10, 'REFILL', 'CAREGIVER', now()),
    ('inventory-2', 'patient-1', 'medication-1', -1, 'CORRECTION', 'CAREGIVER', now()),
    ('inventory-3', 'patient-2', 'medication-2', 5, 'REFILL', 'CAREGIVER', now())`);
}

export async function verifyMigrationUpgrade(databaseUrl, sql) {
  requireLocalDatabase(databaseUrl);
  const statements = validateMigrationSql(sql);
  const schema = `c97_android_mutation_${process.pid}_${randomBytes(6).toString("hex")}`;
  const quotedSchema = `"${schema}"`;
  const client = new Client({ connectionString: databaseUrl });
  await client.connect();
  let schemaCreated = false;
  try {
    await client.query(`CREATE SCHEMA ${quotedSchema}`);
    schemaCreated = true;
    await client.query(`SET search_path TO ${quotedSchema}, public`);
    await createLegacyFixture(client);
    for (const statement of statements) await client.query(statement);
    await verifyColumns(client, schema);
    await verifyIndexes(client, schema);
    await verifyLegacyRows(client);

    await client.query("BEGIN");
    try {
      await verifyPrnUniqueness(client);
      await verifyInventoryUniqueness(client);
    } finally {
      await client.query("ROLLBACK");
    }
  } finally {
    if (schemaCreated) {
      await client.query("RESET search_path").catch(() => undefined);
      await client.query(`DROP SCHEMA IF EXISTS ${quotedSchema} CASCADE`).catch(() => undefined);
    }
    await client.end();
  }
  return { statements: statements.length, legacyRows: 6, uniqueIndexes: 2 };
}

async function main() {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) throw new Error("DATABASE_URL is required");
  const sql = await readFile(MIGRATION_URL, "utf8");
  const result = await verifyMigrationUpgrade(databaseUrl, sql);
  console.log(
    `Android mutation migration upgrade passed: statements=${result.statements} ` +
      `legacyRows=${result.legacyRows} uniqueIndexes=${result.uniqueIndexes} cleanup=passed`
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(`Android mutation migration upgrade failed: ${error.message}`);
    process.exitCode = 1;
  });
}
