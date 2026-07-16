# C50 UI-202 Caregiver Medication List parity

## Baseline and method

- iOS reference: `staging@2b7d1fe`, rendered from the production `MedicationListView` and shared caregiver state components with deterministic local preview data.
- Android candidate: `android-dev@d79615b` plus the C50 working changes, rendered by production Compose screens on API 35.
- No API request or persisted user data was used. The temporary iOS capture route was isolated in a disposable worktree and removed after capture.
- Each pair includes raw platform images, height-normalized images, a side-by-side comparison and a 50% overlay.

## Matrix

| State | Comparison |
| --- | --- |
| Initial loading | [side-by-side](compare-ui-202-caregiver-medications-loading-light-side-by-side.png) · [overlay](compare-ui-202-caregiver-medications-loading-light-overlay-50.png) |
| Initial failure and recovery | [side-by-side](compare-ui-202-caregiver-medications-error-light-side-by-side.png) · [overlay](compare-ui-202-caregiver-medications-error-light-overlay-50.png) |
| No linked patient | [side-by-side](compare-ui-202-caregiver-medications-no-patient-light-side-by-side.png) · [overlay](compare-ui-202-caregiver-medications-no-patient-light-overlay-50.png) |
| Patient selection required | [side-by-side](compare-ui-202-caregiver-medications-selection-light-side-by-side.png) · [overlay](compare-ui-202-caregiver-medications-selection-light-overlay-50.png) |
| Empty, light | [side-by-side](compare-ui-202-caregiver-medications-empty-light-side-by-side.png) · [overlay](compare-ui-202-caregiver-medications-empty-light-overlay-50.png) |
| Populated, light | [side-by-side](compare-ui-202-caregiver-medications-populated-light-side-by-side.png) · [overlay](compare-ui-202-caregiver-medications-populated-light-overlay-50.png) |
| Ended filter with no result | [side-by-side](compare-ui-202-caregiver-medications-ended-light-side-by-side.png) · [overlay](compare-ui-202-caregiver-medications-ended-light-overlay-50.png) |
| Empty, dark | [side-by-side](compare-ui-202-caregiver-medications-empty-dark-side-by-side.png) · [overlay](compare-ui-202-caregiver-medications-empty-dark-overlay-50.png) |
| Populated, dark | [side-by-side](compare-ui-202-caregiver-medications-populated-dark-side-by-side.png) · [overlay](compare-ui-202-caregiver-medications-populated-dark-overlay-50.png) |
| Empty, adaptive | [side-by-side](compare-ui-202-caregiver-medications-empty-adaptive-side-by-side.png) · [overlay](compare-ui-202-caregiver-medications-empty-adaptive-overlay-50.png) |
| Populated, adaptive | [side-by-side](compare-ui-202-caregiver-medications-populated-adaptive-side-by-side.png) · [overlay](compare-ui-202-caregiver-medications-populated-adaptive-overlay-50.png) |

## Closed residuals

- Initial loading now uses the same neutral message-bearing hierarchy as current iOS.
- Initial failure provides Retry and return-to-login; no-patient and no-selection states provide their current iOS registration/settings actions.
- The empty state no longer duplicates the header Add action and now matches the large pills identity, exact three illustrated setup rows and full-width primary action.
- A filter with no matching rows stays blank below metrics and filters, matching current iOS instead of showing Android-only explanatory copy.
- The Home integration test verifies that the no-patient action opens Settings at the patient-name registration field.

## Accepted platform differences and remaining verification

- SF Symbols and Material icons keep native geometry while preserving meaning, color and hierarchy.
- Current iOS caps Dynamic Type at `.xLarge`; the Android adaptive pair deliberately verifies true 200% font scale and scroll reachability.
- Physical TalkBack traversal, OEM rendering and real-API behavior remain Gate I work. They are not inferred from emulator evidence.

## Verification

- Focused `CaregiverMedicationScreenTest`: 19/19.
- Focused `CaregiverHomeScreenTest`: 17/17.
- Full API 35 instrumentation: 223/223, zero failures and skips.
- JVM unit tests: 186/186, zero failures and skips.
- Debug assembly and Android Lint: pass.
