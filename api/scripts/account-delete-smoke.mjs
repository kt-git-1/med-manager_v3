import { randomBytes, randomUUID } from "crypto";
import { config } from "dotenv";
import pg from "pg";

config({ path: ".env" });

const { Pool } = pg;
const baseUrl = (process.env.API_BASE_URL || "https://www.okusuri-mimamori.com").replace(/\/$/, "");
const supabaseUrl = process.env.SUPABASE_URL?.replace(/\/$/, "");
const anonKey = process.env.SUPABASE_ANON_KEY;

if (!process.env.DATABASE_URL || !supabaseUrl || !anonKey) {
  throw new Error("Missing DATABASE_URL, SUPABASE_URL, or SUPABASE_ANON_KEY");
}

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

async function supabase(method, path, body) {
  const response = await fetch(`${supabaseUrl}${path}`, {
    method,
    headers: {
      "content-type": "application/json",
      apikey: anonKey,
      authorization: `Bearer ${anonKey}`
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error(`Supabase ${method} ${path} failed with ${response.status}`);
  }
  return { status: response.status, payload };
}

async function api(method, path, accessToken, body) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      authorization: `Bearer caregiver-${accessToken}`,
      ...(body === undefined ? {} : { "content-type": "application/json" })
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const error = new Error(`${method} ${path} failed with ${response.status}`);
    error.payload = payload;
    throw error;
  }
  return { status: response.status, payload };
}

async function findAuthUserId(email) {
  const { rows } = await pool.query(
    "select id::text from auth.users where email = $1 order by created_at desc limit 1",
    [email]
  );
  return rows[0]?.id;
}

async function confirmAuthUser(userId, email) {
  await pool.query(
    `update auth.users
       set email_confirmed_at = coalesce(email_confirmed_at, now()),
           updated_at = now()
     where id = $1::uuid and email = $2`,
    [userId, email]
  );
}

async function deleteAuthUser(userId) {
  if (!userId) return 0;
  await pool.query("delete from auth.identities where user_id = $1::uuid", [userId]);
  const result = await pool.query("delete from auth.users where id = $1::uuid", [userId]);
  return result.rowCount;
}

async function cleanupSmokeUsers() {
  const { rows } = await pool.query(
    "select id::text from auth.users where email like 'codex-account-delete-smoke-%@example.com'"
  );
  let deleted = 0;
  for (const row of rows) {
    deleted += await deleteAuthUser(row.id);
  }
  return deleted;
}

let smokeUserId;
try {
  const preCleanup = await cleanupSmokeUsers();
  const runId = `${Date.now()}-${randomUUID().slice(0, 8)}`;
  const email = `codex-account-delete-smoke-${runId}@example.com`;
  const password = `${randomBytes(18).toString("base64url")}aA1!`;
  const result = [];

  const signup = await supabase("POST", "/auth/v1/signup", { email, password });
  const userId = signup.payload?.user?.id || (await findAuthUserId(email));
  if (!userId) {
    throw new Error("signup missing auth user id");
  }
  smokeUserId = userId;
  result.push({ step: "auth_signup", status: signup.status });

  await confirmAuthUser(userId, email);
  result.push({ step: "auth_confirm_via_db", status: 200 });

  const login = await supabase("POST", "/auth/v1/token?grant_type=password", { email, password });
  const accessToken = login.payload?.access_token;
  if (!accessToken) {
    throw new Error("login missing access token");
  }
  result.push({ step: "auth_login", status: login.status });

  const patient = await api("POST", "/api/patients", accessToken, {
    displayName: `Codex Account Delete Smoke ${runId}`
  });
  result.push({ step: "patient_create", status: patient.status });

  const deleted = await api("DELETE", "/api/me", accessToken);
  result.push({ step: "account_delete", status: deleted.status, deleted: deleted.payload?.data?.deleted });

  const leftovers = await pool.query(
    `select
       (select count(*)::int from auth.users where id = $1::uuid) as auth_users,
       (select count(*)::int from public."Patient" where "caregiverId" = $1::text) as patients,
       (select count(*)::int from public."CaregiverPatientLink" where "caregiverId" = $1::text) as links`,
    [userId]
  );

  console.log(
    JSON.stringify(
      {
        ok: true,
        baseUrl,
        preCleanup,
        result,
        leftovers: leftovers.rows[0]
      },
      null,
      2
    )
  );
} catch (error) {
  if (smokeUserId) {
    const { rows } = await pool.query(
      `select id from public."Patient"
       where "caregiverId" = $1::text
          or "displayName" like 'Codex Account Delete Smoke %'`,
      [smokeUserId]
    );
    const patientIds = rows.map((row) => row.id);
    if (patientIds.length > 0) {
      await pool.query(
        `delete from public."InventoryAlertEvent" where "patientId" = any($1::text[]);
         delete from public."MedicationInventoryAdjustment" where "patientId" = any($1::text[]);
         delete from public."prn_dose_records" where "patientId" = any($1::text[]);
         delete from public."DoseRecord" where "patientId" = any($1::text[]);
         delete from public."DoseRecordEvent" where "patientId" = any($1::text[]);
         delete from public."Regimen" where "patientId" = any($1::text[]);
         delete from public."Medication" where "patientId" = any($1::text[]);
         delete from public."PatientSession" where "patientId" = any($1::text[]);
         delete from public."LinkingCode" where "patientId" = any($1::text[]);
         delete from public."LinkingAttempt" where "patientId" = any($1::text[]);
         delete from public."CaregiverPatientLink" where "patientId" = any($1::text[]);
         delete from public."Patient" where id = any($1::text[]);`,
        [patientIds]
      );
    }
    await pool.query(`delete from public."CaregiverEntitlement" where "caregiverId" = $1::text`, [
      smokeUserId
    ]);
    await deleteAuthUser(smokeUserId);
  }
  console.error(
    JSON.stringify(
      {
        ok: false,
        baseUrl,
        error: error.message,
        detail: error.detail,
        position: error.position,
        where: error.where,
        payload: error.payload
      },
      null,
      2
    )
  );
  process.exitCode = 1;
} finally {
  await pool.end();
}
