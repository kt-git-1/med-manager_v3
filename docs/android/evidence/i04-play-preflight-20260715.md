# I04 Play preflight evidence — 2026-07-15

## Scope

This is local, secret-free Gate I evidence. It proves that the repository can reject an incomplete Play artifact and that the current unsigned Release APK satisfies locally inspectable compatibility rules. It is not a substitute for a release-owner-signed AAB, Play Console scanning or physical-device testing.

## Production configuration audit

The audit reported only configured/missing state and never printed values.

| Input | Local status |
|---|---|
| Production API base URL | Uses the checked-in canonical HTTPS default |
| Supabase URL / anon key | Configured in Git-ignored local properties |
| Firebase app ID / API key / project ID / sender ID | Missing |
| Email confirmation redirect | Uses the checked-in canonical HTTPS default |
| Billing | Uses the checked-in `false` default |
| Upload keystore / store password / alias / key password | Missing |
| Attached Android targets | API-35 emulator only; no physical device |

`:app:verifyProductionRuntime` fails with only the four missing/malformed Firebase input names. A synthetic non-secret valid-shape configuration passes the same task, proving the validator is not unconditionally failing. `:app:verifyProductionSigning` fails with the four required signing input names. `:app:bundleSignedRelease` stops at production-runtime validation before producing a Play artifact in the current incomplete environment.

## Release APK compatibility gate

Command:

```bash
cd android
./gradlew :app:verifyReleaseApkCompatibility
```

Result:

- application ID: `com.afterlifearchive.medmanager`
- min SDK: 26
- target SDK: 35
- 16 KB APK ZIP alignment: pass
- every packaged native library ELF `LOAD` alignment is at least `2**14`: pass
- advertising ID, AdServices ID/attribution/topics and Install Referrer permissions: absent
- inspected unsigned Release APK SHA-256: `0519b908c1cd438757bef72681e2b725ea24f07738144a4fb26e67fa86959316`

The APK includes AndroidX native libraries for arm64-v8a, armeabi-v7a, x86 and x86_64. The gate inspects all packaged `.so` files rather than assuming a Kotlin-only application has no native dependency.

`.github/workflows/android-ci.yml` now runs Lint and this Release compatibility gate after unit tests/Debug assembly on every scoped `android-dev` push and Android pull request. Dependency updates can therefore no longer silently reintroduce a forbidden permission or non-16-KB native library while local Gate I work continues.

## Current official-policy recheck

- Google Play's current target-API page still requires mobile new apps and updates to target API 35 or newer. The project currently complies at target 35; this must be rechecked at upload time because the policy changes over time.
- API-35+ Play submissions must support 16 KB page sizes. The local APK checks pass, while the final signed AAB must still pass Play Console's artifact inspection.
- The Health apps declaration remains mandatory for closed/open/production publication and `Medication and Treatment Management` remains the applicable feature category.
- Data safety remains the developer's responsibility and must include SDK-transmitted data. The worksheet remains a draft until the exact signed AAB and production Firebase settings are reviewed.
- The live privacy, terms and support routes returned HTTP 200. `https://www.okusuri-mimamori.com/support#section-3` is the nominated Play account-deletion URL because it identifies the app, makes deletion prominent and offers an email request path without requiring app access. A guessed `/account-deletion` route returned 404 and must not be entered in Play Console.
- The Android manifest now declares legacy-density, round and API-26 adaptive launcher icons derived from the shipping iOS app-icon source. `aapt dump badging` resolves the Release APK application label to `お薬見守り` and its icon to the packaged adaptive-icon XML.
- `play-store-listing-ja.md` contains Play-ready Japanese metadata (name 5, short description 36, full description 554 characters), declarations/URLs, alt text and upload order. Eight synthetic Android screenshots are exported as JPEG 1350 x 2400 (9:16), and the store icon is a 512 x 512 8-bit RGBA PNG under 1,024 KB.

## Remaining external evidence

1. Supply the four production Android Firebase values and complete privacy-reviewed DebugView, Realtime, Events and Explore checks.
2. Select and back up the release-owner upload key, then build and verify the signed AAB.
3. Obtain the Play app-signing certificate SHA-256, configure/deploy Digital Asset Links and verify a Play-installed App Link.
4. Run the physical-device matrix, including notification permission, local alarm timing, FCM/Doze/process death, TalkBack, browser/Sharesheet, session restore and destructive flows.
5. Submit and archive the exact Data safety, Health apps, account-deletion (`/support#section-3`) and store-listing declarations in Play Console.
6. Produce and approve the 1024 x 500 feature graphic, then verify the icon, feature graphic and prepared screenshots in the Play listing preview.
