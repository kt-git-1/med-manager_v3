# C60 physical-device gate definition

**Date:** 2026-07-16
**Starting commit:** `41709ba`
**Reference:** `main@3e52fb2`

## Why this checkpoint exists

Gate I, `XP-005/006/008/010` and the Play runbook required a physical-device matrix, but the repository previously had no executable row-level procedure or evidence schema. That made the final release gate dependent on ad hoc interpretation even though emulator implementation evidence was mature.

C60 adds `docs/android/physical-device-matrix.md` and binds it from the master documentation, ordered backlog and Play runbook.

## Coverage defined

- old-supported API 26–28 physical behavior;
- current Google/reference API 35+ behavior;
- current non-Google OEM background, permission, browser/share, TalkBack and IME behavior;
- fresh install, Play update, uninstall/reinstall, backup/device transfer and role/session isolation;
- Patient notification permission, timing, Doze, task removal/process reclamation, exact-slot tap and reminder-plan retention;
- Caregiver FCM initialization, token refresh, generic privacy-safe display, taken/missed routing, Doze, duplicate/invalid payloads, actor exclusion, offline unregister and deletion cleanup;
- every reachable UI-001–106/UI-200–208 TalkBack surface plus 200% font, dark theme, display scale, keyboard and orientation;
- browser, clipboard, Sharesheet, private PDF URI/cache, billing-off reachability;
- offline/high-latency/double-tap and server-first destructive behavior;
- explicit `PASS/FAIL/BLOCKED/NOT_RUN` evidence rows tied to exact device and Play artifact.

The procedure distinguishes background, task removal, process reclamation and Android force-stop. It also includes mandatory Doze/App Standby cleanup commands so a failed test cannot leave the device in a misleading power state.

## Current execution state

`adb devices -l` reported no attached physical target during C59/C60. No row is marked passed from documentation or emulator evidence. Execution remains pending the three required target categories, production-shaped Firebase configuration and the exact Play Internal signed AAB.
