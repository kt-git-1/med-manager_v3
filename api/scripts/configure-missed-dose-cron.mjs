import pg from "pg";

const databaseUrl = process.env.DIRECT_URL;
const endpoint = process.env.MISSED_DOSE_CRON_ENDPOINT;
const cronSecret = process.env.CRON_SECRET;

if (!databaseUrl) throw new Error("DIRECT_URL is required");
if (!endpoint) throw new Error("MISSED_DOSE_CRON_ENDPOINT is required");
if (!cronSecret || cronSecret.length < 16) {
  throw new Error("CRON_SECRET must be at least 16 characters");
}

const parsedEndpoint = new URL(endpoint);
if (parsedEndpoint.protocol !== "https:") {
  throw new Error("MISSED_DOSE_CRON_ENDPOINT must use https");
}

const client = new pg.Client({
  connectionString: databaseUrl,
  ssl: { rejectUnauthorized: false }
});

async function upsertVaultSecret(name, value, description) {
  const existing = await client.query("select id from vault.secrets where name = $1", [name]);
  if (existing.rowCount) {
    await client.query("select vault.update_secret($1, $2, $3, $4)", [
      existing.rows[0].id,
      value,
      name,
      description
    ]);
    return;
  }
  await client.query("select vault.create_secret($1, $2, $3)", [value, name, description]);
}

try {
  await client.connect();
  await client.query('create extension if not exists pg_cron with schema "pg_catalog"');
  await client.query("create extension if not exists pg_net with schema extensions");

  await upsertVaultSecret(
    "missed_dose_cron_endpoint",
    endpoint,
    "Vercel endpoint for caregiver missed-dose notifications"
  );
  await upsertVaultSecret(
    "missed_dose_cron_secret",
    cronSecret,
    "Authorization secret for caregiver missed-dose notification cron"
  );

  await client.query("select cron.unschedule(jobid) from cron.job where jobname = $1", [
    "missed-dose-notifications"
  ]);

  const command = `
    select net.http_get(
      url := (select decrypted_secret from vault.decrypted_secrets where name = 'missed_dose_cron_endpoint'),
      headers := jsonb_build_object(
        'Authorization',
        'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'missed_dose_cron_secret')
      ),
      timeout_milliseconds := 50000
    ) as request_id;
  `;
  await client.query("select cron.schedule($1, $2, $3)", [
    "missed-dose-notifications",
    "*/5 * * * *",
    command
  ]);

  console.log(`Configured missed-dose cron every five minutes for ${parsedEndpoint.host}`);
} finally {
  await client.end();
}
