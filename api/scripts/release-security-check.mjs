import { config } from "dotenv";

config({ path: ".env" });

const required = [
  "DATABASE_URL",
  "DIRECT_URL",
  "SUPABASE_URL",
  "SUPABASE_ANON_KEY",
  "SUPABASE_SERVICE_ROLE_KEY",
  "FCM_SERVICE_ACCOUNT_JSON"
];

const recommended = [
  "PUSH_DEVICE_ENVIRONMENT",
  "PATIENT_SESSION_TTL_DAYS",
  "APPLE_ROOT_CA_PEM",
  "PREMIUM_PRODUCT_ID",
  "ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS"
];

function isSet(name) {
  return typeof process.env[name] === "string" && process.env[name].trim().length > 0;
}

const missingRequired = required.filter((name) => !isSet(name));
const missingRecommended = recommended.filter((name) => !isSet(name));

for (const name of required) {
  console.log(`${name}=${isSet(name) ? "set" : "missing"}`);
}
for (const name of recommended) {
  console.log(`${name}=${isSet(name) ? "set" : "missing"}`);
}

if (process.env.BILLING_API_ENABLED === "true") {
  if (!isSet("PREMIUM_PRODUCT_ID") || process.env.PREMIUM_PRODUCT_ID.includes("yourcompany")) {
    missingRequired.push("PREMIUM_PRODUCT_ID");
  }
}

if (missingRecommended.length) {
  console.warn(`recommended_missing=${missingRecommended.join(",")}`);
}

if (missingRequired.length) {
  console.error(`required_missing=${missingRequired.join(",")}`);
  process.exit(1);
}

console.log("release_security_env=ok");
