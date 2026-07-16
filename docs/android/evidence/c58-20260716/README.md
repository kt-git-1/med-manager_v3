# C58 current-main dose-feedback rebaseline

## Source checkpoint

- Previous pin: `main@1cf8aef`
- Current pin: `main@3e52fb2f5367052bae8e664eee2c1043de76c377`
- Android merge checkpoint: `2fb4a9f`
- Reviewed delta: eight API/iOS files covering dose-write side-effect concurrency, Patient/Caregiver Today post-record behavior and iOS build 40.

The merge was performed in `/Users/kaito/workspace/med-manager_v3-android-worktree` on `android-dev`. No iOS source was edited by the Android implementation.

## Reproduced contract

- Patient individual, positive slot-bulk and PRN success updates the visible state first, then runs authoritative Today reconciliation without setting the blocking refresh overlay.
- Failed individual/PRN writes and zero-update slot-bulk results do not start a false success reconciliation.
- Caregiver individual, delete, PRN and complete slot-bulk success remains interactive during same-patient reconciliation. Partial slot-bulk retains visible refresh feedback because server inventory results are authoritative.
- Caregiver delete restores `MISSED` when the scheduled instant is more than one hour past and `PENDING` before the boundary, pending server reconciliation.
- API history/event and inventory side effects may execute concurrently, but caregiver push remains after both. Android wire models and server-authoritative inventory behavior are unchanged.

## Verification

| Gate | Result |
|---|---|
| Focused Patient/Caregiver JVM plus UI | 44/44 UI, zero failed/skipped |
| Complete Android JVM suite | 189/189, zero failed/skipped |
| Complete API 35 instrumentation suite | 259/259, zero failed/skipped, 7m04s |
| API Vitest suite | 315/315 |
| API TypeScript typecheck | pass |
| Android `lintDebug` | pass |
| Android Debug assembly | pass |
| Android Release assembly | pass |

C57 immediately preceding this checkpoint remains the compact/standard cross-version baseline at 259/259 on API 26, 33 and 35 (777/777). C58 changes repository reconciliation policy rather than API-level UI compatibility; its complete API 35 run plus deterministic delayed-response JVM coverage is the affected-contract gate. Physical FCM, Doze/process death, full TalkBack, OEM lifecycle, browser/Sharesheet and release signing remain Gate I evidence rather than emulator claims.
