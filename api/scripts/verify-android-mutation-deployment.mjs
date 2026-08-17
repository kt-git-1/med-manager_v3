import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { isAbsolute } from "node:path";
import { pathToFileURL } from "node:url";

import pg from "pg";

const { Client } = pg;

const MIGRATION_NAME = "20260817090000_android_mutation_idempotency";
const MIGRATION_URL = new URL(
  `../prisma/migrations/${MIGRATION_NAME}/migration.sql`,
  import.meta.url
);
const MODES = new Set(["preflight", "postdeploy"]);
const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]", "::1"]);
const TABLES = ["prn_dose_records", "MedicationInventoryAdjustment"];
const INDEXES = new Map([
  ["prn_dose_records_patientId_clientMutationId_key", "prn_dose_records"],
  ["MedicationInventoryAdjustment_patientId_clientMutationId_key", "MedicationInventoryAdjustment"]
]);

export class DeploymentAuditError extends Error {}

function assert(condition, message) {
  if (!condition) throw new DeploymentAuditError(message);
}

function parseDatabaseUrl(databaseUrl) {
  let parsed;
  try {
    parsed = new URL(databaseUrl);
  } catch {
    throw new DeploymentAuditError("DIRECT_URL must be a valid PostgreSQL URL");
  }
  assert(
    parsed.protocol === "postgresql:" || parsed.protocol === "postgres:",
    "DIRECT_URL must use PostgreSQL"
  );
  return parsed;
}

export function validateDeploymentAuditOptions({
  databaseUrl,
  mode,
  confirmation,
  allowRemote = false,
  schema = "public"
}) {
  assert(MODES.has(mode), "Audit mode must be preflight or postdeploy");
  assert(confirmation === mode, "ANDROID_MUTATION_DEPLOYMENT_AUDIT must exactly match the mode");
  assert(/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(schema), "Audit schema is invalid");
  const parsed = parseDatabaseUrl(databaseUrl);
  const remote = !LOCAL_HOSTS.has(parsed.hostname);
  if (remote) {
    assert(allowRemote, "Remote audit requires ALLOW_REMOTE_ANDROID_MUTATION_AUDIT=1");
    const sslMode = parsed.searchParams.get("sslmode");
    assert(sslMode === "verify-full", "Remote audit URL must use sslmode=verify-full");
    const rootCertificate = parsed.searchParams.get("sslrootcert");
    assert(
      rootCertificate && isAbsolute(rootCertificate),
      "Remote audit URL must use an absolute sslrootcert path"
    );
    assert(
      !parsed.searchParams.has("uselibpqcompat"),
      "Remote verify-full audit must not enable libpq compatibility mode"
    );
    assert(schema === "public", "Remote audit is restricted to the public schema");
  }
  return { remote, schema };
}

function quoteIdentifier(identifier) {
  assert(/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(identifier), "Unsafe SQL identifier");
  return `"${identifier}"`;
}

async function select(client, sql, params = []) {
  const normalized = sql.trimStart().toUpperCase();
  assert(
    normalized.startsWith("SELECT") || normalized.startsWith("SHOW"),
    "Deployment audit data statements must be SELECT or SHOW"
  );
  return client.query(sql, params);
}

async function verifyReadOnlyTransaction(client) {
  const result = await select(client, "SHOW transaction_read_only");
  assert(result.rows[0]?.transaction_read_only === "on", "Audit transaction is not read-only");
}

async function verifyRequiredRelations(client, schema) {
  const result = await select(
    client,
    `SELECT relation.relname
       FROM pg_class AS relation
       JOIN pg_namespace AS namespace ON namespace.oid = relation.relnamespace
      WHERE namespace.nspname = $1
        AND relation.relkind = 'r'
        AND relation.relname = ANY($2::text[])
      ORDER BY relation.relname`,
    [schema, ["_prisma_migrations", ...TABLES]]
  );
  assert(result.rows.length === 3, "Required migration audit tables are missing");
}

async function migrationRows(client, schema) {
  const qualifiedMigrations = `${quoteIdentifier(schema)}."_prisma_migrations"`;
  return select(
    client,
    `SELECT checksum, finished_at, rolled_back_at, applied_steps_count
       FROM ${qualifiedMigrations}
      WHERE migration_name = $1
      ORDER BY started_at`,
    [MIGRATION_NAME]
  );
}

async function mutationColumns(client, schema) {
  return select(
    client,
    `SELECT table_name, column_name, data_type, is_nullable, column_default
       FROM information_schema.columns
      WHERE table_schema = $1
        AND table_name = ANY($2::text[])
        AND column_name = 'clientMutationId'
      ORDER BY table_name`,
    [schema, TABLES]
  );
}

async function mutationIndexes(client, schema) {
  return select(
    client,
    `SELECT index_class.relname AS index_name,
            table_class.relname AS table_name,
            index_meta.indisunique AS is_unique,
            index_meta.indisvalid AS is_valid,
            index_meta.indisready AS is_ready,
            index_meta.indpred IS NULL AS has_no_predicate,
            index_meta.indexprs IS NULL AS has_no_expression,
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
      GROUP BY index_class.relname, table_class.relname, index_meta.indisunique,
               index_meta.indisvalid, index_meta.indisready, index_meta.indpred,
               index_meta.indexprs
      ORDER BY index_class.relname`,
    [schema, [...INDEXES.keys()]]
  );
}

async function tableCounts(client, schema, includeMutationKeys) {
  const result = {};
  for (const table of TABLES) {
    const qualifiedTable = `${quoteIdentifier(schema)}.${quoteIdentifier(table)}`;
    if (!includeMutationKeys) {
      const count = await select(
        client,
        `SELECT count(*)::bigint AS total_rows FROM ${qualifiedTable}`
      );
      result[table] = { totalRows: Number(count.rows[0].total_rows), keyedRows: 0 };
      continue;
    }
    const count = await select(
      client,
      `SELECT count(*)::bigint AS total_rows,
              count("clientMutationId")::bigint AS keyed_rows
         FROM ${qualifiedTable}`
    );
    const duplicate = await select(
      client,
      `SELECT count(*)::bigint AS duplicate_groups
         FROM (
           SELECT "patientId", "clientMutationId"
             FROM ${qualifiedTable}
            WHERE "clientMutationId" IS NOT NULL
            GROUP BY "patientId", "clientMutationId"
           HAVING count(*) > 1
         ) AS duplicates`
    );
    assert(
      Number(duplicate.rows[0].duplicate_groups) === 0,
      `${table} contains duplicate patient mutation groups`
    );
    result[table] = {
      totalRows: Number(count.rows[0].total_rows),
      keyedRows: Number(count.rows[0].keyed_rows)
    };
  }
  return result;
}

function verifyColumns(rows) {
  assert(rows.length === 2, "Both mutation columns must exist after deployment");
  for (const row of rows) {
    assert(TABLES.includes(row.table_name), "Unexpected mutation column table");
    assert(row.data_type === "text", "Mutation column type drifted");
    assert(row.is_nullable === "YES", "Mutation column must remain nullable");
    assert(row.column_default === null, "Mutation column must remain default-free");
  }
}

function verifyIndexes(rows) {
  assert(rows.length === 2, "Both mutation unique indexes must exist after deployment");
  for (const row of rows) {
    assert(INDEXES.get(row.index_name) === row.table_name, "Mutation index table drifted");
    assert(row.is_unique === true, "Mutation index must remain unique");
    assert(row.is_valid === true && row.is_ready === true, "Mutation index is not valid and ready");
    assert(row.has_no_predicate === true, "Mutation index must not be partial");
    assert(row.has_no_expression === true, "Mutation index must not use expressions");
    assert(
      JSON.stringify(row.columns) === JSON.stringify(["patientId", "clientMutationId"]),
      "Mutation index column order drifted"
    );
  }
}

async function verifyPreflight(client, schema) {
  const [migrations, columns, indexes] = await Promise.all([
    migrationRows(client, schema),
    mutationColumns(client, schema),
    mutationIndexes(client, schema)
  ]);
  assert(migrations.rows.length === 0, "Target migration is already recorded; use postdeploy mode");
  assert(columns.rows.length === 0, "Mutation columns already exist before deployment");
  assert(indexes.rows.length === 0, "Mutation indexes already exist before deployment");
  return tableCounts(client, schema, false);
}

async function verifyPostdeploy(client, schema, expectedChecksum) {
  const [migrations, columns, indexes] = await Promise.all([
    migrationRows(client, schema),
    mutationColumns(client, schema),
    mutationIndexes(client, schema)
  ]);
  assert(migrations.rows.length === 1, "Target migration must have exactly one Prisma record");
  const migration = migrations.rows[0];
  assert(migration.finished_at !== null, "Target migration is not finished");
  assert(migration.rolled_back_at === null, "Target migration was rolled back");
  assert(Number(migration.applied_steps_count) === 1, "Target migration step count drifted");
  assert(
    migration.checksum === expectedChecksum,
    "Deployed migration checksum differs from source"
  );
  verifyColumns(columns.rows);
  verifyIndexes(indexes.rows);
  return tableCounts(client, schema, true);
}

export async function auditAndroidMutationDeployment({
  databaseUrl,
  mode,
  confirmation,
  allowRemote = false,
  schema = "public"
}) {
  const policy = validateDeploymentAuditOptions({
    databaseUrl,
    mode,
    confirmation,
    allowRemote,
    schema
  });
  const migrationSql = await readFile(MIGRATION_URL);
  const expectedChecksum = createHash("sha256").update(migrationSql).digest("hex");
  const client = new Client({
    connectionString: databaseUrl,
    application_name: "medmanager_android_mutation_audit"
  });
  await client.connect();
  try {
    await client.query("BEGIN READ ONLY");
    await client.query("SET LOCAL statement_timeout = '10s'");
    await client.query("SET LOCAL lock_timeout = '2s'");
    await verifyReadOnlyTransaction(client);
    await verifyRequiredRelations(client, policy.schema);
    const counts =
      mode === "preflight"
        ? await verifyPreflight(client, policy.schema)
        : await verifyPostdeploy(client, policy.schema, expectedChecksum);
    await client.query("ROLLBACK");
    return { mode, remote: policy.remote, counts };
  } catch (error) {
    await client.query("ROLLBACK").catch(() => undefined);
    throw error;
  } finally {
    await client.end();
  }
}

function parseMode(argv) {
  if (argv.length !== 2 || argv[0] !== "--mode") {
    throw new DeploymentAuditError(
      "Usage: verify-android-mutation-deployment.mjs --mode <preflight|postdeploy>"
    );
  }
  return argv[1];
}

async function main() {
  const databaseUrl = process.env.DIRECT_URL;
  assert(databaseUrl, "DIRECT_URL is required");
  const mode = parseMode(process.argv.slice(2));
  const result = await auditAndroidMutationDeployment({
    databaseUrl,
    mode,
    confirmation: process.env.ANDROID_MUTATION_DEPLOYMENT_AUDIT,
    allowRemote: process.env.ALLOW_REMOTE_ANDROID_MUTATION_AUDIT === "1"
  });
  const prn = result.counts.prn_dose_records;
  const inventory = result.counts.MedicationInventoryAdjustment;
  console.log(
    `Android mutation deployment ${result.mode} passed: remote=${result.remote} ` +
      `prnRows=${prn.totalRows} prnKeyed=${prn.keyedRows} ` +
      `inventoryRows=${inventory.totalRows} inventoryKeyed=${inventory.keyedRows} ` +
      `valuesExposed=0 readOnly=passed`
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    if (error instanceof DeploymentAuditError) {
      console.error(`Android mutation deployment audit rejected: ${error.message}`);
    } else {
      const code = typeof error?.code === "string" ? ` code=${error.code}` : "";
      console.error(`Android mutation deployment audit failed safely.${code}`);
    }
    process.exitCode = 1;
  });
}
