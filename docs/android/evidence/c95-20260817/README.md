# C95 production and Play-installed App Links gate — 2026-08-17

## Result

C95 closes the locally automatable Digital Asset Links and installed package-manager acceptance boundary. It does **not** mark production App Links complete: the canonical live endpoint is still HTTP 404, the endpoint commit is not on `origin/main`, no release-owner Play app-signing certificate was supplied, and no Med Manager package is currently installed from Play.

## Source/deployment finding

- `api/app/.well-known/assetlinks.json/route.ts` is introduced by `eab2718` and is contained by `origin/android-dev`, not `origin/main@432b34c`.
- A 2026-08-17 read-only fetch of `https://www.okusuri-mimamori.com/.well-known/assetlinks.json` returned Vercel HTTP 404, `text/html`, with no redirect. `verifyProductionAppLinks` rejected it before inspecting any certificate value.
- The apex well-known URL redirects to `www`. Android's website-association contract forbids redirects and requires a file for every manifest host. Production API and email callback configuration already use canonical `www`, so C95 removes only the apex host from the verified Release intent filter and its artifact policy. The callback parser remains defensive for legacy/external input.

## Implemented gates

### Public production surface

`verifyProductionAppLinks` requires:

- the exact HTTPS `www` well-known URL, with redirects disabled;
- HTTP 200, `application/json`, exactly `public, max-age=300`, and at most 64 KiB;
- exactly one association with no extra fields;
- only `delegate_permission/common.handle_all_urls` for `com.afterlifearchive.medmanager`;
- uppercase colon-separated, duplicate-free SHA-256 values exactly equal to `PLAY_APP_SIGNING_CERT_SHA256_FINGERPRINTS`.

The Play app-signing identity is intentionally separate from `PLAY_UPLOAD_CERT_SHA256`. Neither value is a private key, but the verifier does not print either fingerprint.

### Play-installed physical surface

`verifyPlayInstalledAppLinks` reruns the production gate, then requires:

- an explicitly selected, authorized API-31+ physical device;
- the exact package installed by `com.android.vending`;
- package-manager signatures exactly equal to the same Play app-signing set;
- exactly `www.okusuri-mimamori.com: verified`, with no apex, extra domain or alternate state.

It requests package-manager re-verification but never launches the app or reads/mutates app data. ADB is resolved from `ADB`, PATH, `ANDROID_SDK_ROOT`, `ANDROID_HOME`, or ignored `local.properties` `sdk.dir`.

## Verification

- Public verifier pure contract: 2 accepted / 17 rejected.
- Play-installed verifier pure contract: 3 accepted / 13 rejected.
- API Digital Asset Links integration test: 3/3 passed.
- Current live production check: expected rejection, HTTP 404.
- Connected A302SH/API 35 preflight: expected rejection because the Med Manager package is absent; package remained absent and was never launched.
- Release manifest policy unit contract: 12/12 passed.
- Debug JVM: 216/216 passed; Release JVM: 213/213 passed.
- Debug build, Lint, Play assets and Release APK compatibility passed.
- Exact current Release APK/AAB/universal/API 26/33/35 split surfaces each passed with six permissions, three reviewed exported components, `authLinks=2`, advertising exclusion and 16 KiB ZIP/native alignment.
- A clean synthetic upload-key `verifySignedReleaseBundle` passed 78 tasks (76 executed, 2 up-to-date), including pre-build key identity, APK, AAB, universal, three device-split surfaces and final signer identity. Its key, certificate and all generated artifacts were removed and are not production evidence.

One initial monolithic local invocation failed at AGP `packageRelease` without a policy error. The exact APK task then passed alone, the AAB/install-surface group passed, and a subsequent clean full signed graph passed. Hosted CI is retained as the independent clean-machine result rather than treating the retry alone as sufficient.

## Hosted CI

- Implementation commit: `c8985bbc99d9`
- Android CI: [run 31980648536](https://github.com/kt-git-1/med-manager_v3/actions/runs/31980648536) — 26/26 workflow steps passed, including the new App Links contract, both JVM variants, Lint, APK, AAB, universal/device-split and Play asset gates.
- The workflow's new App Links step runs only the five accepted/thirty rejected pure contracts; it does not make a live request or fabricate external completion.

## Residual external gate

1. Final-rebaseline and approve the `android-dev` merge without overwriting newer iOS/API work.
2. Enable Play App Signing and independently read its app-signing SHA-256, distinct from the upload key.
3. Configure the same set in production `ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS`, deploy the merged API and pass `verifyProductionAppLinks`.
4. Install the exact Internal-test artifact from Play on the selected physical device and pass `verifyPlayInstalledAppLinks` plus the two external auth-path behavior checks.

Until all four are evidenced, `AU-006` remains implemented but production-verification pending and `XP-010` remains `PARTIAL`.
