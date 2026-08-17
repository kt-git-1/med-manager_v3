import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

export class PlayPolicyReadinessError extends Error {}

const REQUIRED_FILES = {
  deletion: "api/app/account-deletion/page.tsx",
  privacy: "api/app/privacy/page.tsx",
  support: "api/app/support/page.tsx",
  footer: "api/app/SiteFooter.tsx",
  manifest: "android/app/src/main/AndroidManifest.xml",
  sessionStorage:
    "android/app/src/main/java/com/afterlifearchive/medmanager/data/session/AndroidSessionStorage.kt",
  sessionRepository:
    "android/app/src/main/java/com/afterlifearchive/medmanager/data/session/SessionRepository.kt",
  access: "docs/android/play-review-access.json"
};

function fail(message) {
  throw new PlayPolicyReadinessError(message);
}

function read(root, path) {
  try {
    return readFileSync(resolve(root, path), "utf8");
  } catch {
    fail(`required policy source is unavailable: ${path}`);
  }
}

function requireText(source, expected, label) {
  if (!source.includes(expected)) fail(`${label} is missing required text: ${expected}`);
}

function parseAccess(root) {
  const path = REQUIRED_FILES.access;
  let value;
  try {
    value = JSON.parse(read(root, path));
  } catch (error) {
    if (error instanceof PlayPolicyReadinessError) throw error;
    fail(`${path} must be valid JSON`);
  }
  const exactTopLevel = [
    "accountDeletionUrl",
    "appName",
    "checkpoint",
    "developerOrganization",
    "reviewAccess",
    "schemaVersion"
  ];
  if (JSON.stringify(Object.keys(value).sort()) !== JSON.stringify(exactTopLevel)) {
    fail("review-access top-level schema drifted");
  }
  return value;
}

function verifyAccessSource(access) {
  if (access.schemaVersion !== 1 || access.checkpoint !== "C106") {
    fail("review-access schema/checkpoint drifted");
  }
  if (access.appName !== "お薬見守り") fail("review-access app name drifted");
  if (access.accountDeletionUrl !== "https://www.okusuri-mimamori.com/account-deletion") {
    fail("dedicated account-deletion URL drifted");
  }

  const organization = access.developerOrganization;
  if (
    !organization ||
    !["PENDING_PLAY_ORGANIZATION_VERIFICATION", "VERIFIED"].includes(organization.status)
  ) {
    fail("developer organization status is invalid");
  }
  if (
    organization.status === "PENDING_PLAY_ORGANIZATION_VERIFICATION" &&
    organization.legalName !== null
  ) {
    fail("unverified organization name must not be published as verified");
  }
  if (
    organization.status === "VERIFIED" &&
    (typeof organization.legalName !== "string" || organization.legalName.trim().length < 2)
  ) {
    fail("verified organization requires its legal name");
  }

  const review = access.reviewAccess;
  const exactReviewKeys = [
    "credentialMethod",
    "credentialsLastVerifiedAt",
    "credentialsStorage",
    "loginReusable",
    "oneTimeLinkCodeRequired",
    "patientFixture",
    "playArtifactVersionCode",
    "regionIndependent",
    "secretMaterialStoredInRepository",
    "status"
  ];
  if (!review || JSON.stringify(Object.keys(review).sort()) !== JSON.stringify(exactReviewKeys)) {
    fail("review-access credential schema drifted");
  }
  const expected = {
    credentialMethod: "caregiver_email_password",
    credentialsStorage: "external_release_owner_secret_store",
    secretMaterialStoredInRepository: false,
    loginReusable: true,
    oneTimeLinkCodeRequired: false,
    patientFixture: "retained_dedicated_production_qa_patient"
  };
  for (const [key, value] of Object.entries(expected)) {
    if (review[key] !== value) fail(`review-access ${key} drifted`);
  }
  if (!["PENDING_FINAL_PLAY_ARTIFACT_VERIFICATION", "VERIFIED"].includes(review.status)) {
    fail("review-access status is invalid");
  }
  if (
    review.status === "PENDING_FINAL_PLAY_ARTIFACT_VERIFICATION" &&
    (review.credentialsLastVerifiedAt !== null || review.playArtifactVersionCode !== null)
  ) {
    fail("pending review access must not claim final artifact verification");
  }
}

export function verifyPlayPolicyReadiness(root, { release = false } = {}) {
  const deletion = read(root, REQUIRED_FILES.deletion);
  const privacy = read(root, REQUIRED_FILES.privacy);
  const support = read(root, REQUIRED_FILES.support);
  const footer = read(root, REQUIRED_FILES.footer);
  const manifest = read(root, REQUIRED_FILES.manifest);
  const sessionStorage = read(root, REQUIRED_FILES.sessionStorage);
  const sessionRepository = read(root, REQUIRED_FILES.sessionRepository);
  const access = parseAccess(root);

  for (const text of [
    "お薬見守り",
    "家族モードでログイン",
    "アプリを使わずに削除を依頼する",
    "削除されるデータ",
    "保持される場合があるデータ",
    "support@okusuri-mimamori.com",
    "パスワードや確認コードを尋ねることはありません"
  ])
    requireText(deletion, text, "account-deletion page");
  requireText(privacy, 'title: "安全管理"', "privacy policy");
  requireText(privacy, "HTTPS/TLS", "privacy policy");
  requireText(privacy, "Android Keystore", "privacy policy");
  requireText(privacy, 'href="/account-deletion"', "privacy policy");
  requireText(support, 'href="/account-deletion"', "support page");
  requireText(footer, 'href="/account-deletion"', "site footer");
  requireText(manifest, 'android:usesCleartextTraffic="false"', "Android manifest");
  requireText(sessionStorage, 'KeyStore.getInstance("AndroidKeyStore")', "Android session storage");
  requireText(
    sessionStorage,
    'const val TRANSFORMATION = "AES/GCM/NoPadding"',
    "Android session storage"
  );
  requireText(
    sessionRepository,
    "loginCaregiver(email: String, password: String)",
    "caregiver review login"
  );
  verifyAccessSource(access);

  if (release) {
    const organization = access.developerOrganization;
    const review = access.reviewAccess;
    if (organization.status !== "VERIFIED")
      fail("release requires verified Play organization identity");
    if (review.status !== "VERIFIED") fail("release requires verified reusable review access");
    if (review.regionIndependent !== true)
      fail("release requires region-independent review access");
    if (
      !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/.test(
        review.credentialsLastVerifiedAt ?? ""
      )
    ) {
      fail("release requires an ISO UTC review-credential verification time");
    }
    if (!Number.isInteger(review.playArtifactVersionCode) || review.playArtifactVersionCode <= 0) {
      fail("release requires the verified Play artifact versionCode");
    }
    requireText(privacy, organization.legalName, "privacy policy developer identity");
  }

  return {
    mode: release ? "release" : "source",
    accountDeletionUrl: access.accountDeletionUrl,
    developerOrganizationStatus: access.developerOrganization.status,
    reviewAccessStatus: access.reviewAccess.status,
    checks: 27 + (release ? 6 : 0)
  };
}

function argumentsFor(argv) {
  const argumentsList = [...argv];
  const release = argumentsList.includes("--release");
  const rootIndex = argumentsList.indexOf("--repository-root");
  const root =
    rootIndex >= 0 ? argumentsList[rootIndex + 1] : resolve(import.meta.dirname, "../..");
  if (!root || (rootIndex >= 0 && root.startsWith("--")))
    fail("--repository-root requires a value");
  return { root, release };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const { root, release } = argumentsFor(process.argv.slice(2));
    const result = verifyPlayPolicyReadiness(root, { release });
    console.log(JSON.stringify(result));
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  }
}
