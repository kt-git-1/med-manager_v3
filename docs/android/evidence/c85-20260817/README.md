# C85 signed AAB artifact verification — 2026-08-17

## Finding

The release task validated runtime values, signing inputs, Release APK compatibility and Play assets before generating an AAB, but the generated AAB itself was verified only by a manual runbook command. The task did not fail automatically for an unsigned/partially signed artifact or a certificate different from the Play-registered upload certificate.

The first full synthetic integration also exposed a separate path bug: `verifyReleaseApkCompatibility` assumed `app-release-unsigned.apk`, while a configured signing block produces `app-release.apk`. Therefore the production-shaped signed bundle graph could not reach AAB generation even with otherwise complete inputs.

## Correction

- `verifyReleaseApkCompatibility` receives the exact signed or unsigned Release APK path from Gradle and the standalone script refuses an ambiguous default.
- `PLAY_UPLOAD_CERT_SHA256` is a required, normalized signing input.
- `verifySignedReleaseBundle` depends on production runtime, production signing, Release APK compatibility, Play assets and `bundleRelease`.
- The generated AAB must contain `BundleConfig.pb` and `base/manifest/AndroidManifest.xml`.
- `jarsigner` must verify the JAR signature and report no unsigned entries.
- `keytool` must expose exactly one signing-certificate SHA-256 and it must match the expected Play upload fingerprint.
- `bundleSignedRelease` succeeds only through `verifySignedReleaseBundle` and reports the AAB SHA-256 plus upload-certificate SHA-256.

## Synthetic contract evidence

The JDK-only contract creates an isolated temporary bundle and disposable synthetic certificate. It proves:

| Case | Result |
| --- | --- |
| Required AAB structure + complete signature + expected fingerprint | PASS |
| Correctly signed AAB + different expected fingerprint | Rejected |
| Unsigned AAB | Rejected |
| Signed AAB with an entry appended after signing | Rejected |

The complete Gradle `bundleSignedRelease` graph was then run with synthetic runtime values and a synthetic PKCS12 file under `app/build` only. Production-runtime safety, signing input, signed APK compatibility, AAB generation, JAR signature and certificate match all passed. A missing fingerprint and a mismatched fingerprint both failed closed. `./gradlew clean` removed the synthetic certificate, APK and AAB; no generated signing artifact remains.

## Regression gates

- Signed-AAB verifier shell syntax and synthetic contract: pass
- Debug JVM: 216/216, 0 failed, 0 skipped
- Release JVM: 213/213, 0 failed, 0 skipped
- Lint: pass
- Unsigned Release APK compatibility after cleanup: pass
- Play listing assets: pass
- Incomplete production `bundleSignedRelease`: rejected before AAB generation
- Tracked secret-pattern scan: no real credential, keystore or certificate value

## Hosted CI

- Android CI run #143 on implementation commit `5eafc99`: `PASS`
  - Firebase runtime: success
  - Runtime credential safety: success
  - Signed bundle verifier contract: success
  - Connected shard runner contract: success
  - Debug and Release JVM tests: success
  - Debug build: success
  - Lint: success
  - Release APK compatibility: success
  - Play listing assets: success

Run #143 proves the synthetic signed-bundle verifier and all retained downstream CI gates. The synthetic certificate is not an upload key and the synthetic AAB is not a release artifact. `XP-010` remains `PARTIAL` until the release owner supplies the approved upload key/fingerprint and production runtime, the exact verified AAB is uploaded to Play, and Play signing/install/Console/device evidence passes.
