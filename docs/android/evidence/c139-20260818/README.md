# C139 Release artifact policy verification sweep — 2026-08-18

- Working branch: `android-dev`
- Commit: `04b3064`
- Working directory: `android/`

## Scope

Local policy and manifest integrity gates were re-run on the current release artifacts. No source changes were made.

## Commands and results

Executed:

1. `./gradlew :app:verifyReleaseSdkPolicy :app:verifyReleaseApkCompatibility :app:verifyReleaseBundlePolicyContract :app:verifyReleaseEvidencePolicyContract :app:verifyReleaseBundleInstallSurface`

### Results

- `verifyReleaseSdkPolicyContract`: PASS
- `verifyReleaseSdkPolicy`: PASS (UP-TO-DATE)
- `verifyReleaseBundlePolicyContract`: PASS
- `verifyReleaseEvidencePolicyContract`: PASS (`4 accepted, 14 rejected`)
- `verifyReleaseApkCompatibility`: PASS
  - package: `com.afterlifearchive.medmanager`
  - permissions=6
  - exported=3
  - authLinks=2
  - 16KB zip/native-ELF alignment: PASS
  - SHA-256: `e6dad2f2e64634243bee74be5a47acf9943ab7a2f1bfbeba1fdfb392c1634115`
- `verifyReleaseBundleContent`: PASS
  - module/dex/lib: `base`, `4`, `8`
  - AAB SHA-256: `94f6c0b9ca43c9f0c15e1677505dd354e41fcdd245e02646c052f82f03e297dd`
- `verifyReleaseBundleInstallSurface`: PASS
  - universal output: `/Users/kaito/workspace/med-manager_v3-android-worktree/android/app/build/outputs/bundle-install-surface/universal-test-only.apk`
  - SHA-256: `2513d03c8b7bb2395d1d9aec8c94250eb9367b10bede1e82a3017ece0ff268d9`

## Notes

- Command emitted a known gradle warning: `bundletoolCli` resolved during configuration time.
- No functional/runtime settings were changed in this run; this is artifact-policy validation evidence on the already-clean branch.
