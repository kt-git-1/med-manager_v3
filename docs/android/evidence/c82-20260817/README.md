# C82 bounded cross-API connected UI matrix — 2026-08-17

## Scope

- Branch: `android-dev`
- Starting checkpoint: `35037ee` (C81)
- Published parity baseline: iOS 1.0.6 Build 51, `main@432b34c`
- Change under verification: reproducible, disposable-target connected UI shard runner
- Production API/data: not accessed or mutated

## Runner contract

Run from `android/`:

```sh
scripts/run-connected-ui-shards.sh [adb-serial]
```

The script:

- resolves `adb` from an explicit override, Android SDK environment or `local.properties`;
- requires one ready target unless an adb serial is supplied;
- validates a shard count from 1 through 16;
- optionally reruns one zero-based shard with `ANDROID_UI_TEST_SHARD_INDEX`;
- refuses to overwrite an existing app or test-package installation;
- always removes the app and test packages on success, failure or interruption.

The default four AndroidJUnitRunner shards contain 66, 59, 79 and 76 tests.

## Results

| Target | Shards | Total | Failed | Skipped |
| --- | --- | ---: | ---: | ---: |
| Android 8.0 / API 26 AVD | 66 + 59 + 79 + 76 | 280 | 0 | 0 |
| Android 13 / API 33 AVD | 66 + 59 + 79 + 76 | 280 | 0 | 0 |
| Android 15 / API 35 AVD | 66 + 59 + 79 + 76 | 280 | 0 | 0 |
| **Cross-API total** | 12 shards | **840** | **0** | **0** |

All AVDs used no-window, no-audio, no-boot-animation, no-snapshot-load and no-snapshot-save startup. App and test packages were absent after every completed target.

One API-26 Gradle daemon stopped making progress before APK installation on shard four. No test had started and no package was present. The daemon was stopped, then only shard four was resumed through the script's single-shard option and passed 76/76. API 33 and 35 completed without the stall. This was runner infrastructure recovery, not a test failure.

## Post-matrix quality gate

```sh
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:assembleRelease
```

- Debug JVM: 216/216, 0 failed, 0 skipped
- Release JVM: 213/213, 0 failed, 0 skipped
- Android Lint: pass
- Release assembly and release vital lint: pass
- Script syntax/help/invalid-argument checks: pass

## Acceptance boundary

This closes the current API-level emulator compatibility rerun and makes it repeatable. It does not promote any result to old-supported physical hardware, Google/reference hardware, assisted spoken TalkBack, real-finger operation, physical notification/FCM delivery, destructive interruption, signed AAB or Play Console acceptance.
