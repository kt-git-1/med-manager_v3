# C33 Status-focused Caregiver Today

## Authoritative current contract

`main@1cf8aef` `CaregiverTodayView.swift` is authoritative. It removes the former next-dose hero, medicine list, top bulk action and orange “next” timeline treatment. The checked-in `api/public/screenshots/caregiver-today.png` still shows that old hierarchy, so it is retained as historical evidence and is not used to override current runtime source.

Current order reproduced by Android:

1. Patient avatar/name and `今日の服薬` header.
2. Optional missed-dose alert.
3. `今日の服薬状況` progress ring, `%d/%d回分 記録済み` and missed/pending/done summary.
4. Optional PRN entry.
5. `今日の予定` and four slot-colored timeline rows; each eligible row owns its bulk action.

The single missed-dose alert now includes the exact slot and configured time. Pending rows use `未記録`; no production resource or component retains `次にすること`, `次に記録`, `今日の進み具合`, the old top action, or the special orange next-row surface.

## Captures

| Artifact | SHA-256 |
|---|---|
| `android-ui-201-status-first-light.png` | `e427793a5bb1a425c4c3b2f3c854f0918e1dbe5de7eec831e5c4d6c517157b07` |
| `android-ui-201-timeline-light.png` | `83bb6fc4fcad43b79097390710411aaaa506612efba5a7efbeec46d5b008bb40` |

Both are direct API-35 device screenshots of the production Compose component tree at 1080 x 2400.

## Automated results

- `CaregiverTodayScreenTest`: 13/13 on API 35, including absence of the legacy hero, exact progress/missed/pending copy, row-level bulk action, PRN, stale refresh and mutation behavior.
- `CaregiverLargeTextUiTest`, `CaregiverAdaptiveUiTest`, `CaregiverAccessibilityTest`: combined 17/17 on API 35.
- Compile gates pass after deleting the obsolete component and resources.

## Remaining release evidence

- C37 captures the current iOS runtime directly and pairs it with Android light and dark/maximum-text states under `../c37-20260716/`; the stale repository public screenshot remains historical only.
- Physical-device TalkBack remains in Gate I.
