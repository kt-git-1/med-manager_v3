# C04 Patient History evidence — 2026-07-14

Current source baseline: `ios/MedicationApp/Features/History/HistoryMonthView.swift` patient-mode branch and `ios/MedicationApp/Resources/Localizable.strings` at the current main rebaseline.

| Evidence | Purpose | Result |
|---|---|---|
| `android-ui-104-patient-history-light.png` | Fixed-date production History preview | Matches current hierarchy: title/subtitle, orange partial-progress ring/card, teal current-week card with Monday-first status row, Recent Records heading and selected History tab |

Deterministic fixture: 2026-07-14 JST with one of three current slots recorded, Monday recorded, and remaining week states visible.

Automated verification:

- Today progress covers partial, no-schedule and missed-priority copy.
- Week rendering covers Monday-first dates, fully-recorded-day count and status icons.
- Recent rows cover today/yesterday titles and active-slot summaries.
- Retention lock, retained day scheduled/PRN rows and recorder attribution remain covered.
- Full gate: `./gradlew test assembleDebug assembleRelease lint connectedDebugAndroidTest` — success, 36/36 API-35 instrumentation tests.

Residual C01/C06/V1 evidence: paired iOS capture, dark mode, font 1.3/2.0, TalkBack, compact/large phones and physical-device behavior.
