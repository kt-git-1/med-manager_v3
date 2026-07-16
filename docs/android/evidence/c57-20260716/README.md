# C57 expanded API 26/33/35 compatibility matrix

## Final matrix

The complete current instrumentation suite was run from the Android-only worktree after C56 and the compact-viewport corrections described below.

| AVD | Android | Viewport | Final result | Final runtime |
|---|---|---|---|---|
| `MedicationApp_API_26` | 8.0 / API 26 | 1080 x 1920, 420 dpi | 259/259 | 3m41s |
| `MedicationApp_API_33` | 13 / API 33 | 1080 x 2400, 420 dpi | 259/259 | 7m42s |
| `MedicationApp_API_35` | 15 / API 35 | 1080 x 2400, 420 dpi | 259/259 | 7m08s |
| **Total** | — | — | **777/777** | — |

Every final run had zero failed and zero skipped tests. The command for each isolated AVD was:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The AVDs use Google APIs ARM64 system images. Emulator version: `36.6.11.0` build `15507667`.

## API-26 finding and correction

The first expanded API-26 run found seven compact-viewport failures in existing adaptive tests. They were not missing actions or production navigation failures: the 1080 x 1920 viewport correctly disposes off-screen `LazyColumn` children, while the newer tests had assumed those children remained in the semantics tree as on the taller API-33/35 viewport.

- Caregiver auth choice surfaces now expose stable login/signup semantics tags; the 200% test scrolls the choice, form actions and first navigation item through their real lists.
- Patient link exposes stable list/back tags so the long-error test scrolls the production list to the final action.
- Caregiver medication add, scheduled/PRN/weekly and save checks scroll the real list before interaction.
- Patient Today inventory, primary action, planned section and PRN checks scroll the production list to each target.

No layout, copy, validation or API contract was narrowed. The corrections make the tests exercise the same gestures required on a compact supported device. The five affected classes then passed 86 tests apart from the final two isolated items; those two passed together after their lazy-list corrections, followed by the clean 259/259 API-26 rerun recorded above.

## Non-instrumentation gate

- JVM: 186/186, zero failed/skipped.
- `lintDebug`: pass.
- `assembleDebug`: pass.
- `assembleRelease`: pass.

This remains emulator evidence. Physical notification delivery/Doze/process death, TalkBack, clipboard/Sharesheet/browser behavior, OEM lifecycle and device transfer remain Gate I requirements.
