import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

import {
  EXPECTED_MIGRATION_STATEMENTS,
  validateMigrationSql,
  verifyMigrationUpgrade
} from "./verify-android-mutation-migration.mjs";

const migrationUrl = new URL(
  "../prisma/migrations/20260817090000_android_mutation_idempotency/migration.sql",
  import.meta.url
);
const validSql = await readFile(migrationUrl, "utf8");

function rejected(label, sql) {
  assert.throws(
    () => validateMigrationSql(sql),
    /exact reviewed additive contract/,
    `${label} unexpectedly passed`
  );
}

assert.deepEqual(validateMigrationSql(validSql), EXPECTED_MIGRATION_STATEMENTS);

const cases = [
  [
    "missing statement",
    validSql.replace(/CREATE UNIQUE INDEX "MedicationInventoryAdjustment[\s\S]*?;\s*$/, "")
  ],
  ["extra statement", `${validSql}\nUPDATE "prn_dose_records" SET "quantityTaken" = 0;\n`],
  ["destructive drop", validSql.replace("ADD COLUMN", "DROP COLUMN")],
  [
    "required legacy field",
    validSql.replace('"clientMutationId" TEXT;', '"clientMutationId" TEXT NOT NULL;')
  ],
  [
    "row-rewriting default",
    validSql.replace('"clientMutationId" TEXT;', "\"clientMutationId\" TEXT DEFAULT 'unknown';")
  ],
  ["wrong column type", validSql.replace('"clientMutationId" TEXT;', '"clientMutationId" UUID;')],
  ["non-unique index", validSql.replace("CREATE UNIQUE INDEX", "CREATE INDEX")],
  [
    "global rather than patient scope",
    validSql.replace('("patientId", "clientMutationId")', '("clientMutationId")')
  ],
  [
    "reordered scope",
    validSql.replace('("patientId", "clientMutationId")', '("clientMutationId", "patientId")')
  ],
  ["duplicate statement", `${validSql}\n${EXPECTED_MIGRATION_STATEMENTS[0]};\n`]
];

for (const [label, sql] of cases) rejected(label, sql);

await assert.rejects(
  verifyMigrationUpgrade(
    "postgresql://fixture:fixture@production.example.invalid/database",
    validSql
  ),
  /refuses a non-local database/
);

console.log(
  `Android mutation migration SQL contract passed: accepted=1 rejected=${cases.length + 1}`
);
