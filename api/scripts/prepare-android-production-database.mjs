import { createHash, randomBytes, X509Certificate } from "node:crypto";
import { appendFile, readFile } from "node:fs/promises";
import { isAbsolute } from "node:path";
import { pathToFileURL } from "node:url";

export const SUPABASE_PRODUCTION_CA_URL =
  "https://supabase-downloads.s3-ap-southeast-1.amazonaws.com/prod/ssl/prod-ca-2021.crt";
export const SUPABASE_PRODUCTION_CA_DER_SHA256 =
  "807025ad50d4ed219d2c9c7d299c004f824eb00cf7f65afef607d07b72e6cafa";

const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]", "::1"]);
const SSL_QUERY_KEYS = ["ssl", "sslmode", "sslrootcert", "sslcert", "sslkey", "uselibpqcompat"];

export class ProductionDatabaseTlsError extends Error {}

function assert(condition, message) {
  if (!condition) throw new ProductionDatabaseTlsError(message);
}

function isSupabaseDatabaseHost(hostname) {
  return hostname.endsWith(".supabase.co") || hostname.endsWith(".supabase.com");
}

export function prepareProductionDatabaseUrl({
  databaseUrl,
  certificatePath,
  certificateBytes,
  expectedCertificateSha256 = SUPABASE_PRODUCTION_CA_DER_SHA256,
  now = new Date()
}) {
  assert(typeof databaseUrl === "string" && databaseUrl.length > 0, "Database URL is required");
  assert(
    typeof certificatePath === "string" &&
      isAbsolute(certificatePath) &&
      !certificatePath.includes("\n"),
    "Database CA path must be an absolute single-line path"
  );

  let parsed;
  try {
    parsed = new URL(databaseUrl);
  } catch {
    throw new ProductionDatabaseTlsError("Database URL must be valid");
  }
  assert(
    parsed.protocol === "postgresql:" || parsed.protocol === "postgres:",
    "Database URL must use PostgreSQL"
  );
  assert(!LOCAL_HOSTS.has(parsed.hostname), "Production database must be remote");
  assert(isSupabaseDatabaseHost(parsed.hostname), "Production database must use Supabase");

  let certificate;
  try {
    certificate = new X509Certificate(certificateBytes);
  } catch {
    throw new ProductionDatabaseTlsError("Production database CA is not a valid certificate");
  }
  assert(certificate.ca, "Production database certificate must be a CA");
  const certificateSha256 = createHash("sha256").update(certificate.raw).digest("hex");
  assert(
    certificateSha256 === expectedCertificateSha256,
    "Production database CA fingerprint drifted"
  );
  const currentTime = now.getTime();
  assert(
    currentTime >= new Date(certificate.validFrom).getTime() &&
      currentTime < new Date(certificate.validTo).getTime(),
    "Production database CA is outside its validity window"
  );

  for (const key of SSL_QUERY_KEYS) parsed.searchParams.delete(key);
  parsed.searchParams.set("sslmode", "verify-full");
  parsed.searchParams.set("sslrootcert", certificatePath);
  return { databaseUrl: parsed.toString(), certificateSha256 };
}

export async function writeGitHubEnvironment({
  githubEnvironmentPath,
  databaseUrl,
  certificatePath
}) {
  assert(process.env.GITHUB_ACTIONS === "true", "GitHub environment export is CI-only");
  assert(
    typeof githubEnvironmentPath === "string" && isAbsolute(githubEnvironmentPath),
    "GITHUB_ENV must be an absolute path"
  );
  const delimiter = `MED_MANAGER_DATABASE_${randomBytes(12).toString("hex")}`;
  assert(!databaseUrl.includes(delimiter), "Generated environment delimiter collided");
  await appendFile(
    githubEnvironmentPath,
    `DIRECT_URL<<${delimiter}\n${databaseUrl}\n${delimiter}\n` +
      `DATABASE_URL<<${delimiter}\n${databaseUrl}\n${delimiter}\n` +
      `ANDROID_PRODUCTION_DATABASE_CA_PATH<<${delimiter}\n${certificatePath}\n${delimiter}\n`,
    { encoding: "utf8", mode: 0o600 }
  );
}

async function main() {
  const sourceUrl = process.env.ANDROID_PRODUCTION_DIRECT_URL;
  const certificatePath = process.env.ANDROID_PRODUCTION_DATABASE_CA_PATH;
  assert(sourceUrl, "ANDROID_PRODUCTION_DIRECT_URL is required");
  assert(certificatePath, "ANDROID_PRODUCTION_DATABASE_CA_PATH is required");
  const certificateBytes = await readFile(certificatePath);
  const prepared = prepareProductionDatabaseUrl({
    databaseUrl: sourceUrl,
    certificatePath,
    certificateBytes
  });
  await writeGitHubEnvironment({
    githubEnvironmentPath: process.env.GITHUB_ENV,
    databaseUrl: prepared.databaseUrl,
    certificatePath
  });
  console.log(`::add-mask::${prepared.databaseUrl}`);
  console.log(
    `Android production database TLS prepared: mode=verify-full ` +
      `caSha256=${prepared.certificateSha256} valuesExposed=0`
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    if (error instanceof ProductionDatabaseTlsError) {
      console.error(`Android production database TLS rejected: ${error.message}`);
    } else {
      console.error("Android production database TLS preparation failed safely.");
    }
    process.exitCode = 1;
  });
}
