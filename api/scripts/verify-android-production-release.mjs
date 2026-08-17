import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const FULL_SHA = /^[0-9a-f]{40}$/;
const MODES = new Set(["preflight", "deploy", "release"]);
const CONFIRMATIONS = new Map([
  ["preflight", "PREFLIGHT_ANDROID_API_PRODUCTION"],
  ["deploy", "DEPLOY_ANDROID_API_PRODUCTION"],
  ["release", "RELEASE_ANDROID_API_PRODUCTION"]
]);
const EXPECTED_SECRETS = new Set([
  "ANDROID_PRODUCTION_DIRECT_URL",
  "PLAY_APP_SIGNING_CERT_SHA256_FINGERPRINTS",
  "VERCEL_TOKEN"
]);
const EXPECTED_VARIABLES = new Set([
  "ANDROID_API_PRODUCTION_RELEASE_ENABLED",
  "ANDROID_API_PRODUCTION_REVIEWERS_CONFIGURED",
  "VERCEL_ORG_ID",
  "VERCEL_PROJECT_ID"
]);

export class ProductionReleaseError extends Error {}

function assert(condition, message) {
  if (!condition) throw new ProductionReleaseError(message);
}

function exactKeys(value, expected, label) {
  assert(value && typeof value === "object" && !Array.isArray(value), `${label} must be an object`);
  const keys = Object.keys(value).sort();
  assert(JSON.stringify(keys) === JSON.stringify([...expected].sort()), `${label} fields drifted`);
}

export function validateVercelReleaseConfig(config) {
  exactKeys(config, ["$schema", "git"], "vercel config");
  assert(config.$schema === "https://openapi.vercel.sh/vercel.json", "Vercel schema drifted");
  exactKeys(config.git, ["deploymentEnabled"], "vercel git config");
  exactKeys(config.git.deploymentEnabled, ["android-dev", "main"], "deployment policy");
  assert(
    config.git.deploymentEnabled["android-dev"] === false,
    "android-dev auto deploy must be off"
  );
  assert(config.git.deploymentEnabled.main === false, "main auto deploy must be off");
  return true;
}

export function validateReleaseDispatch({
  releaseCommit,
  actualCommit,
  ref,
  mode,
  confirmation,
  releaseEnabled,
  reviewersConfigured,
  vercelConfig
}) {
  assert(FULL_SHA.test(releaseCommit), "release_commit must be a lowercase full Git SHA");
  assert(FULL_SHA.test(actualCommit), "GITHUB_SHA must be a lowercase full Git SHA");
  assert(
    releaseCommit === actualCommit,
    "release_commit must exactly match the checked-out commit"
  );
  assert(ref === "refs/heads/main", "Production release workflow must run from main");
  assert(MODES.has(mode), "mode must be preflight, deploy or release");
  assert(confirmation === CONFIRMATIONS.get(mode), "confirmation does not match the selected mode");
  if (mode !== "preflight") {
    assert(releaseEnabled === "true", "Production release is not armed");
    assert(reviewersConfigured === "true", "Required-reviewer configuration is not attested");
  }
  validateVercelReleaseConfig(vercelConfig);
  return { mode, writeEnabled: mode !== "preflight", commit: releaseCommit.slice(0, 12) };
}

function references(text, namespace) {
  const pattern = new RegExp(`\\$\\{\\{\\s*${namespace}\\.([A-Z0-9_]+)\\s*\\}\\}`, "g");
  return new Set([...text.matchAll(pattern)].map((match) => match[1]));
}

function stepBlock(text, fragment) {
  const index = text.indexOf(fragment);
  assert(index >= 0, `Workflow is missing: ${fragment}`);
  const start = text.lastIndexOf("\n      - name:", index);
  const end = text.indexOf("\n      - name:", index + fragment.length);
  assert(start >= 0, `Workflow command is not inside a named step: ${fragment}`);
  return text.slice(start, end >= 0 ? end : text.length);
}

export function validateProductionWorkflow(text) {
  assert(typeof text === "string" && text.length > 0, "Production workflow is empty");
  assert(text.includes("name: Android API Production Release"), "Workflow name drifted");
  assert(text.includes("  workflow_dispatch:"), "Workflow must be manual dispatch only");
  assert(
    !/^  (push|pull_request|schedule|repository_dispatch|workflow_run):/m.test(text),
    "Production workflow must not have an automatic trigger"
  );
  const required = [
    "default: preflight",
    "actions/checkout@11d5960a326750d5838078e36cf38b85af677262",
    "actions/setup-node@49933ea5288caeca8642d1e84afbd3f7d6820020",
    "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02",
    "node-version: 22",
    "permissions:\n  contents: read",
    "cancel-in-progress: false",
    "environment:\n      name: android-api-production",
    "timeout-minutes: 30",
    "type: choice",
    "- preflight",
    "- deploy",
    "- release",
    "node scripts/verify-android-production-release.mjs",
    "node scripts/test-verify-android-production-control-plane.mjs",
    "node scripts/verify-api-node-runtime.mjs",
    "node scripts/test-prepare-android-production-database.mjs",
    "node scripts/prepare-android-production-database.mjs",
    "npm audit --audit-level=high",
    "git -C .. fetch --no-tags origin main",
    '"$(git -C .. rev-parse origin/main)" != "$GITHUB_SHA"',
    'test -z "$(git -C .. status --porcelain)"',
    "node scripts/verify-android-mutation-deployment.mjs --mode preflight",
    "node scripts/verify-android-mutation-deployment.mjs --mode postdeploy",
    "curl --proto '=https' --tlsv1.2 --fail --silent --show-error --location",
    "https://supabase-downloads.s3-ap-southeast-1.amazonaws.com/prod/ssl/prod-ca-2021.crt",
    "ANDROID_PRODUCTION_DATABASE_CA_PATH: ${{ runner.temp }}/supabase-prod-ca-2021.crt",
    "npx prisma migrate deploy",
    "npx --yes vercel@59.1.3 pull --yes --environment=production",
    "npx --yes vercel@59.1.3 build --prod",
    "npx --yes vercel@59.1.3 deploy --prebuilt --prod",
    "python3 ../android/scripts/verify-production-app-links.py",
    "https://www.okusuri-mimamori.com/api/health"
  ];
  for (const fragment of required)
    assert(text.includes(fragment), `Workflow is missing: ${fragment}`);
  assert(!text.includes("vercel@latest"), "Vercel CLI must be pinned");
  assert(!text.includes("continue-on-error: true"), "Production checks must not continue on error");
  assert(!text.includes("--force"), "Production workflow must not force state changes");
  assert(!/uses:\s+[^\s#]+@(v\d+|main|master)\b/.test(text), "Actions must use full commit pins");
  assert(
    JSON.stringify([...references(text, "secrets")].sort()) ===
      JSON.stringify([...EXPECTED_SECRETS].sort()),
    "Production secret references drifted"
  );
  assert(
    JSON.stringify([...references(text, "vars")].sort()) ===
      JSON.stringify([...EXPECTED_VARIABLES].sort()),
    "Production variable references drifted"
  );
  assert(
    text.split("secrets.ANDROID_PRODUCTION_DIRECT_URL").length - 1 === 1,
    "Production database secret must be read exactly once by the TLS preparation step"
  );

  const tlsPreparation = stepBlock(text, "node scripts/prepare-android-production-database.mjs");
  assert(
    tlsPreparation.includes(
      "ANDROID_PRODUCTION_DIRECT_URL: ${{ secrets.ANDROID_PRODUCTION_DIRECT_URL }}"
    ),
    "TLS preparation must own the production database secret"
  );
  assert(
    tlsPreparation.includes('--output "$ANDROID_PRODUCTION_DATABASE_CA_PATH"'),
    "Pinned database CA must be written only to its ephemeral path"
  );

  const migration = stepBlock(text, "npx prisma migrate deploy");
  const postdeploy = stepBlock(
    text,
    'node scripts/verify-android-mutation-deployment.mjs --mode postdeploy | tee "$RUNNER_TEMP/android-api-postdeploy.txt"'
  );
  const deployment = stepBlock(text, "npx --yes vercel@59.1.3 deploy --prebuilt --prod");
  const health = stepBlock(text, "https://www.okusuri-mimamori.com/api/health");
  const appLinks = stepBlock(text, "python3 ../android/scripts/verify-production-app-links.py");
  for (const block of [migration, postdeploy, deployment, health, appLinks]) {
    assert(block.includes("if: inputs.mode != 'preflight'"), "Write step is not mode-gated");
  }
  const preflight = stepBlock(
    text,
    "node scripts/verify-android-mutation-deployment.mjs --mode preflight"
  );
  assert(
    preflight.includes("if: inputs.mode == 'preflight' || inputs.mode == 'deploy'"),
    "Preflight state audit mode guard drifted"
  );
  for (const block of [preflight, migration, postdeploy]) {
    assert(
      !block.includes("secrets.ANDROID_PRODUCTION_DIRECT_URL"),
      "Database operations must consume only the prepared verify-full URL"
    );
  }
  const cleanup = stepBlock(text, "process.env.ANDROID_PRODUCTION_DATABASE_CA_PATH");
  assert(cleanup.includes("if: always()"), "Database CA cleanup must always run");
  assert(
    cleanup.includes(
      "ANDROID_PRODUCTION_DATABASE_CA_PATH: ${{ runner.temp }}/supabase-prod-ca-2021.crt"
    ),
    "Database CA cleanup must know the path even when preparation fails"
  );
  const orderedFragments = [
    "node scripts/prepare-android-production-database.mjs",
    "--mode preflight | tee",
    "npx prisma migrate deploy",
    '--mode postdeploy | tee "$RUNNER_TEMP/android-api-postdeploy.txt"',
    "vercel@59.1.3 pull",
    "vercel@59.1.3 build",
    "vercel@59.1.3 deploy",
    "https://www.okusuri-mimamori.com/api/health",
    "verify-production-app-links.py"
  ];
  const positions = orderedFragments.map((fragment) => text.indexOf(fragment));
  assert(
    positions.every((position, index) => index === 0 || position > positions[index - 1]),
    "Production release operation order drifted"
  );
  return true;
}

async function main() {
  const configPath = new URL("../vercel.json", import.meta.url);
  const config = JSON.parse(await readFile(configPath, "utf8"));
  const result = validateReleaseDispatch({
    releaseCommit: process.env.RELEASE_COMMIT ?? "",
    actualCommit: process.env.GITHUB_SHA ?? "",
    ref: process.env.GITHUB_REF ?? "",
    mode: process.env.RELEASE_MODE ?? "",
    confirmation: process.env.RELEASE_CONFIRMATION ?? "",
    releaseEnabled: process.env.RELEASE_ENABLED ?? "",
    reviewersConfigured: process.env.REVIEWERS_CONFIGURED ?? "",
    vercelConfig: config
  });
  console.log(
    `Android API production dispatch verified: mode=${result.mode} ` +
      `writeEnabled=${result.writeEnabled} commit=${result.commit}`
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    if (error instanceof ProductionReleaseError || error instanceof SyntaxError) {
      console.error(`Android API production dispatch rejected: ${error.message}`);
    } else {
      console.error("Android API production dispatch failed safely.");
    }
    process.exitCode = 1;
  });
}
