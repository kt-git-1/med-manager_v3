# Quickstart: Billing Foundation (Premium Unlock)

## Scope

- Caregiver mode gets a Premium Unlock paywall, restore, and entitlement status in Settings.
- Server receives claim and returns entitlement status for caregiver accounts.
- Patient mode remains free with zero billing entry points.
- FeatureGate map is defined for premium-gated capabilities (implementations in future features).

## Key Endpoints

- `POST /api/iap/claim` — submit signed transaction to register premium entitlement.
- `GET /api/me/entitlements` — read caregiver premium status and entitlement records.

## How to Purchase Premium (Caregiver, Sandbox)

1) Open the app in caregiver mode.
2) Navigate to Settings tab.
3) Tap "プレミアムにアップグレード" in the Premium section.
4) Paywall displays with product price and description.
5) Tap "購入する" and complete the Sandbox purchase flow.
6) Full-screen "更新中" overlay appears during processing.
7) On success, Settings shows "Premium: 有効".

## How to Restore Purchase (Caregiver, Sandbox)

1) Open the app in caregiver mode.
2) Navigate to Settings tab.
3) Tap "購入を復元" in the Premium section.
4) Full-screen "更新中" overlay appears during AppStore sync.
5) On success, premium status refreshes and shows "Premium: 有効".

## Auto-Refresh Behavior

Entitlement state automatically re-evaluates on:
- App launch
- App returns to foreground
- After caregiver login completes

During auto-refresh, the full-screen overlay blocks interaction until evaluation completes.

## Patient Mode

- No billing UI elements appear in patient mode.
- Navigating Settings, Today, History, and all other tabs shows zero billing entry points.
- Patient mode is free and remains unaffected by premium state changes.

## FeatureGate Definitions

| Gate               | Tier    | Description                                    |
|--------------------|---------|------------------------------------------------|
| multiplePatients   | premium | 2nd+ patient registration                     |
| extendedHistory    | premium | History beyond 30-day free limit               |
| pdfExport          | premium | PDF export of records                          |
| enhancedAlerts     | premium | Enhanced caregiver alerts (low inventory push) |
| escalationPush     | pro     | Missed-dose escalation push (future Pro)       |

Gate implementations are delivered in subsequent features. In 008, gates return locked/unlocked based on entitlement state.

## Constraints

- Only one Non-Consumable product: Premium Unlock.
- Escalation push is excluded from Premium (reserved for future Pro tier).
- Server-side JWS verification in MVP validates structure and payload fields but defers full Apple root cert chain verification.
- All billing network operations use the full-screen "更新中" overlay.

## App Review Checklist

- **Restore**: "購入を復元" is accessible from Settings > Premium section and from the Paywall screen.
- **Patient mode free**: Patient mode has no billing UI; the app is fully usable without purchase.
- **Premium benefits**: Paywall clearly describes what Premium unlocks.
- **Non-Consumable**: Single one-time purchase; no subscription.

## Local Development

### API

```bash
cd api
npm test
```

### iOS

```bash
xcodebuild -project "ios/MedicationApp/MedicationApp.xcodeproj" -scheme "MedicationApp" -destination "platform=iOS Simulator,name=iPhone 17 Pro" test
```

### Prisma v7.3 Note

This project uses Prisma v7.3 with the `@prisma/adapter-pg` driver adapter and a `pg.Pool` connection (see `api/src/repositories/prisma.ts`). Key differences from earlier Prisma versions:

- Client initialization: `new PrismaClient({ adapter: new PrismaPg(pool) })` instead of passing `url` in datasource.
- Migration command: `npx prisma migrate dev --name <name>` (same workflow, but `prisma.config.ts` is used for configuration).
- The `CaregiverEntitlement` migration is at `api/prisma/migrations/20260210120000_caregiver_entitlements/`.
- Run `npx prisma generate` after schema changes to regenerate the client.

### Sandbox Testing Tips

- Use a Sandbox Apple ID configured in App Store Connect.
- On Simulator, StoreKit Testing configuration files can mock purchases without a real Sandbox account.
- Verify restore works by deleting the app, reinstalling, and tapping "購入を復元".
- Verify lifecycle refresh by backgrounding and foregrounding the app after purchase.

### Implemented File Locations

| Component | File |
|-----------|------|
| Prisma Schema | `api/prisma/schema.prisma` |
| Entitlement Repo | `api/src/repositories/entitlementRepo.ts` |
| IAP Validator | `api/src/validators/iapValidator.ts` |
| Entitlement Service | `api/src/services/entitlementService.ts` |
| Claim Route | `api/app/api/iap/claim/route.ts` |
| Entitlements Route | `api/app/api/me/entitlements/route.ts` |
| EntitlementStore | `ios/MedicationApp/Features/Billing/EntitlementStore.swift` |
| FeatureGate | `ios/MedicationApp/Features/Billing/FeatureGate.swift` |
| PaywallView | `ios/MedicationApp/Features/Billing/PaywallView.swift` |
| PaywallViewModel | `ios/MedicationApp/Features/Billing/PaywallViewModel.swift` |
| Entitlement DTOs | `ios/MedicationApp/Networking/DTOs/EntitlementDTO.swift` |
| Settings Premium | `ios/MedicationApp/Features/PatientManagement/PatientManagementView.swift` |
| Localization | `ios/MedicationApp/Resources/Localizable.strings` |
