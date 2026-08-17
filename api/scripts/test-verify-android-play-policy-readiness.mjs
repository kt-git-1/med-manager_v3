import assert from "node:assert/strict";
import { cpSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import {
  PlayPolicyReadinessError,
  verifyPlayPolicyReadiness
} from "./verify-android-play-policy-readiness.mjs";

const repositoryRoot = resolve(import.meta.dirname, "../..");
const paths = [
  "api/app/account-deletion/page.tsx",
  "api/app/privacy/page.tsx",
  "api/app/support/page.tsx",
  "api/app/SiteFooter.tsx",
  "android/app/src/main/AndroidManifest.xml",
  "android/app/src/main/java/com/afterlifearchive/medmanager/data/session/AndroidSessionStorage.kt",
  "android/app/src/main/java/com/afterlifearchive/medmanager/data/session/SessionRepository.kt",
  "docs/android/play-review-access.json"
];

function fixture() {
  const root = mkdtempSync(resolve(tmpdir(), "play-policy-readiness-"));
  for (const path of paths) {
    const target = resolve(root, path);
    mkdirSync(dirname(target), { recursive: true });
    cpSync(resolve(repositoryRoot, path), target);
  }
  return root;
}

function replace(root, path, from, to) {
  const target = resolve(root, path);
  const source = readFileSync(target, "utf8");
  assert.ok(source.includes(from), `fixture source missing: ${from}`);
  writeFileSync(target, source.replaceAll(from, to));
}

function reject(label, mutate, options = {}) {
  const root = fixture();
  try {
    mutate(root);
    assert.throws(() => verifyPlayPolicyReadiness(root, options), PlayPolicyReadinessError, label);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

const accepted = fixture();
try {
  const result = verifyPlayPolicyReadiness(accepted);
  assert.equal(result.mode, "source");
  assert.equal(result.developerOrganizationStatus, "PENDING_PLAY_ORGANIZATION_VERIFICATION");
  assert.equal(result.reviewAccessStatus, "PENDING_FINAL_PLAY_ARTIFACT_VERIFICATION");
} finally {
  rmSync(accepted, { recursive: true, force: true });
}

reject("missing app identity", (root) => replace(root, paths[0], "お薬見守り", "別アプリ"));
reject("missing external deletion path", (root) =>
  replace(root, paths[0], "アプリを使わずに削除を依頼する", "別の手続き")
);
reject("missing retention disclosure", (root) =>
  replace(root, paths[0], "保持される場合があるデータ", "その他")
);
reject("missing privacy security section", (root) =>
  replace(root, paths[1], 'title: "安全管理"', 'title: "管理"')
);
reject("support link drift", (root) =>
  replace(root, paths[2], 'href="/account-deletion"', 'href="/support"')
);
reject("footer link drift", (root) =>
  replace(root, paths[3], 'href="/account-deletion"', 'href="/support"')
);
reject("cleartext enabled", (root) =>
  replace(
    root,
    paths[4],
    'android:usesCleartextTraffic="false"',
    'android:usesCleartextTraffic="true"'
  )
);
reject("session encryption drift", (root) =>
  replace(root, paths[5], "AES/GCM/NoPadding", "AES/ECB/PKCS5Padding")
);
reject("review login drift", (root) =>
  replace(
    root,
    paths[6],
    "loginCaregiver(email: String, password: String)",
    "loginCaregiver(email: String)"
  )
);
reject("deletion URL drift", (root) => {
  const target = resolve(root, paths[7]);
  const value = JSON.parse(readFileSync(target, "utf8"));
  value.accountDeletionUrl = "https://www.okusuri-mimamori.com/support#section-3";
  writeFileSync(target, JSON.stringify(value));
});
reject("repository secret claim", (root) => {
  const target = resolve(root, paths[7]);
  const value = JSON.parse(readFileSync(target, "utf8"));
  value.reviewAccess.secretMaterialStoredInRepository = true;
  writeFileSync(target, JSON.stringify(value));
});
reject("one-time review code claim", (root) => {
  const target = resolve(root, paths[7]);
  const value = JSON.parse(readFileSync(target, "utf8"));
  value.reviewAccess.oneTimeLinkCodeRequired = true;
  writeFileSync(target, JSON.stringify(value));
});
reject("unverified organization name", (root) => {
  const target = resolve(root, paths[7]);
  const value = JSON.parse(readFileSync(target, "utf8"));
  value.developerOrganization.legalName = "Unverified Organization";
  writeFileSync(target, JSON.stringify(value));
});
reject("pending final review marked with artifact", (root) => {
  const target = resolve(root, paths[7]);
  const value = JSON.parse(readFileSync(target, "utf8"));
  value.reviewAccess.playArtifactVersionCode = 51;
  writeFileSync(target, JSON.stringify(value));
});
reject("release refuses pending external evidence", () => {}, { release: true });

console.log(JSON.stringify({ accepted: 1, rejected: 15 }));
