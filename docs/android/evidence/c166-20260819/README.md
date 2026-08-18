# C166 local fixture contract sweep (2026-08-19)

- Date: 2026-08-19
- Branch: `android-dev`
- Commit head at run: `3d42fad`
- Command bundle:
  - `for f in scripts/test-verify-*.py scripts/test-prepare-play-release-handoff.py scripts/test-generate-release-evidence.py; do python3 "$f"; done`

## Summary

- Android CI runtime: PASS (1 accepted, 23 rejected)
- Device split-set: PASS (2 accepted, 10 rejected; atomic report passed)
- Main merge surface: PASS (accepted=1 rejected=11)
- Play downloaded base APK receipt: PASS (6 accepted/idempotent, 46 rejected, real-sdk=0)
- Play generated APK receipt: PASS (4 accepted/idempotent, 36 rejected)
- Play-installed App Links: PASS (accepted=3 rejected=13)
- Play-installed package receipt: PASS (accepted=7 rejected=40)
- Play Internal track receipt: PASS (4 accepted/idempotent, 48 rejected)
- Play store listing: PASS (accepted=1 rejected=20)
- Play upload receipt: PASS (4 accepted/idempotent, 21 rejected)
- Production App Links: PASS (accepted=2 rejected=17)
- Release gates: PASS (accepted=1 rejected=70)
- Release manifest policy: PASS (12 tests)
- Play release handoff: PASS (3 accepted/idempotent, 17 rejected)
- Release evidence policy: PASS (4 accepted, 14 rejected; generated-ledger handoff and atomic JSON passed)

## Notes

- These are fixture-based/contract checks and do not perform real production/Play/Firebase operations.
- No source changes were made.
