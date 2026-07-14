# C06 Patient and Caregiver Adaptive UI Evidence — 2026-07-14 to 2026-07-15

## Environment

- Branch: `android-dev`
- Device: API 35 emulator, 1080 x 2400 pixels, 420 dpi
- Locale/time zone: Japanese / `ja_JP`, `Asia/Tokyo`
- Production Compose screens with deterministic preview arguments; no real identity, medication, dose or authentication data

## Captures

| Contract | Variant | Evidence |
|---|---|---|
| UI-101 Patient Today | Dark, font scale 1.0 | [`android-ui-101-patient-today-dark.png`](android-ui-101-patient-today-dark.png) |
| UI-104 Patient History | Dark, font scale 1.0 | [`android-ui-104-patient-history-dark.png`](android-ui-104-patient-history-dark.png) |
| UI-106 Patient Settings | Dark, font scale 1.0 | [`android-ui-106-patient-settings-dark.png`](android-ui-106-patient-settings-dark.png) |
| UI-101 Patient Today | Light, font scale 2.0 initial viewport | [`android-ui-101-patient-today-font-2.0.png`](android-ui-101-patient-today-font-2.0.png) |
| Caregiver Today | Dark, font scale 2.0, medication action reached | [`android-caregiver-today-dark-font-2.0.png`](android-caregiver-today-dark-font-2.0.png) |
| Caregiver medication form | Dark, font scale 2.0, save action reached | [`android-caregiver-medication-form-dark-font-2.0.png`](android-caregiver-medication-form-dark-font-2.0.png) |
| Caregiver inventory detail | Dark, font scale 2.0, correction action reached | [`android-caregiver-inventory-detail-dark-font-2.0.png`](android-caregiver-inventory-detail-dark-font-2.0.png) |
| Caregiver History | Dark, font scale 2.0, calendar state | [`android-caregiver-history-dark-font-2.0.png`](android-caregiver-history-dark-font-2.0.png) |

The 200% capture records the initial viewport only. Automated scrolling assertions are the acceptance evidence that content below that viewport remains reachable.

## Automated acceptance

`PatientAdaptiveUiTest` launches production Patient Today, History and Settings at font scale 2.0 and verifies these lower-screen targets can be reached through the real lazy lists:

- Today primary bulk action and planned section
- History recent-record section and today's week row
- Settings analytics toggle and logout action

`PatientAccessibilityTest` verifies each History week cell exposes one localized weekday/date/status announcement, for example `月 7月13日 忘れ`, alongside the existing heading and summary semantics.

The 2026-07-15 extension runs the three Patient reachability scenarios at both 1.3 and 2.0 font scale. `CaregiverAdaptiveUiTest` verifies at 1.3 that Today's medication entry, the medication form save action, inventory detail settings and exact History day sheet remain reachable, while Caregiver Analytics consent and logout remain reachable at 2.0. `CaregiverLargeTextUiTest` exercises the real scroll paths to Today medication entry, medication save, inventory correction and History day detail at 200% in both light and dark themes. Its dark parameter writes the four production-component PNGs listed above; the History day sheet remains an interaction assertion because Compose's separate modal window cannot be reliably composited by `captureToImage`. `PatientConfigurationUiTest` launches the real `MainActivity` production preview, recreates the Activity, rotates portrait to landscape and returns to portrait while asserting the primary Today content after every transition. `CaregiverConfigurationUiTest` performs the same production-Activity transition sequence after selecting Inventory and asserts that the selected tab and its content remain restored. `CaregiverAccessibilityTest` verifies one localized calendar-day node and checks that Today record/cancel, medication edit and inventory-detail actions include the relevant medication name. The complete API 26/33/35 matrix passes 303/303.

The first Caregiver Today dark capture exposed black inherited header text on the dark screen background. `MedicationAppTheme` now provides `onBackground` as the default content color inside the Material theme, while Cards and other Material surfaces continue to override it with their own semantic content colors. `ThemeTokenTest` locks both light and dark inherited content colors, and the regenerated Today/History captures confirm readable light foreground content.

The latest complete command below passes 101/101 API-35 instrumentation tests, all JVM tests, Debug/Release assembly and Android Lint. The identical instrumentation suite also passes 101/101 on API 26 and API 33, for 303/303 total:

```sh
./gradlew test assembleDebug assembleRelease lint connectedDebugAndroidTest
```

## Residual before C06 verification

C06 remains open. Primary Patient and Caregiver screens now have explicit 1.3/2.0 action reachability coverage, including Caregiver Settings/tutorial; the four Caregiver primary surfaces have dark-plus-200% PNG evidence and action-path coverage; both production role previews pass Activity recreation plus portrait/landscape transitions; and Patient week/Caregiver calendar state semantics are merged for TalkBack. Full TalkBack traversal on every state, matched iOS dark/large-text variants, additional compact/large device-class evidence and physical-device verification remain. Those residuals are intentionally not inferred from emulator automation.
