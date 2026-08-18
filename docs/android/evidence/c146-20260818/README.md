# C146 Release manifest policy check against actual merged manifest — 2026-08-18

**Date:** 2026-08-18

**Branch:** `android-dev`  
**Commit:** `d436e10`

## Scope

Validated the actual merged Release AndroidManifest against the strict release security/privacy policy.

## Command

```bash
cd android
python3 scripts/verify-release-manifest-policy.py \
  app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml
```

## Result

```
Release manifest security/privacy policy verification passed.
package=com.afterlifearchive.medmanager permissions=6 exported=3 authLinks=2
```

## Notes

- Confirms release manifest contract for package name, permissions, exported components, auth links, backup/extraction policy, and receiver/service/provider protection settings in the current build output.
