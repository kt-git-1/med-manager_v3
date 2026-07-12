import SwiftUI

struct HistoryRetentionLockView: View {
    let mode: AppMode
    let cutoffDate: String
    let entitlementStore: EntitlementStore
    let onDismiss: () -> Void
    var onRefresh: (() -> Void)?

    @State private var showPaywall = false

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "lock.fill")
                .font(.system(size: 48))
                .foregroundStyle(Color.readableSecondaryText)

            Text(titleText)
                .font(.title3.weight(.semibold))
                .multilineTextAlignment(.center)

            Text(bodyText)
                .font(.body)
                .foregroundStyle(Color.readableSecondaryText)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            Spacer()

            VStack(spacing: 12) {
                if mode == .caregiver {
                    caregiverButtons
                } else {
                    patientButtons
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AppTheme.screenBackground)
        .accessibilityIdentifier("HistoryRetentionLockView")
        .sheet(isPresented: $showPaywall) {
            PaywallView(
                entitlementStore: entitlementStore,
                feature: .unlimitedHistory,
                surface: .history
            )
        }
        .onAppear {
            guard mode == .caregiver else { return }
            AnalyticsService.shared.logFeatureInterest(.unlimitedHistory, surface: .history)
            AnalyticsService.shared.logPremiumNeed(.unlimitedHistory, surface: .history)
        }
    }

    // MARK: - Text

    private var titleText: String {
        if mode == .caregiver {
            return NSLocalizedString(
                "history.retention.lock.caregiver.title",
                comment: "Caregiver retention lock title"
            )
        } else {
            return NSLocalizedString(
                "history.retention.lock.patient.title",
                comment: "Patient retention lock title"
            )
        }
    }

    private var bodyText: String {
        if mode == .caregiver {
            return NSLocalizedString(
                "history.retention.lock.caregiver.body",
                comment: "Caregiver retention lock body"
            )
        } else {
            return NSLocalizedString(
                "history.retention.lock.patient.body",
                comment: "Patient retention lock body"
            )
        }
    }

    // MARK: - Caregiver Buttons

    private var caregiverButtons: some View {
        VStack(spacing: 12) {
            if AppConstants.billingEnabled {
                Button {
                    showPaywall = true
                } label: {
                    Text(NSLocalizedString(
                        "history.retention.lock.upgrade",
                        comment: "Upgrade button"
                    ))
                    .font(.headline)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                }
                .accessibilityIdentifier("history.retention.lock.upgrade")

                Button {
                    Task { await entitlementStore.restore() }
                } label: {
                    Text(NSLocalizedString(
                        "history.retention.lock.restore",
                        comment: "Restore button"
                    ))
                    .font(.subheadline)
                    .foregroundStyle(.tint)
                }
                .accessibilityIdentifier("history.retention.lock.restore")
            }

            Button {
                onDismiss()
            } label: {
                Text(NSLocalizedString(
                    "history.retention.lock.close",
                    comment: "Close button"
                ))
                .font(.subheadline)
                .foregroundStyle(Color.readableSecondaryText)
            }
            .accessibilityIdentifier("history.retention.lock.close")
        }
    }

    // MARK: - Patient Buttons

    private var patientButtons: some View {
        VStack(spacing: 12) {
            if let onRefresh {
                Button {
                    onRefresh()
                } label: {
                    Text(NSLocalizedString(
                        "history.retention.lock.refresh",
                        comment: "Refresh button"
                    ))
                    .font(.headline)
                    .foregroundStyle(.tint)
                }
                .accessibilityIdentifier("history.retention.lock.refresh")
            }

            Button {
                onDismiss()
            } label: {
                Text(NSLocalizedString(
                    "history.retention.lock.close",
                    comment: "Close button"
                ))
                .font(.subheadline)
                .foregroundStyle(Color.readableSecondaryText)
            }
            .accessibilityIdentifier("history.retention.lock.close")
        }
    }
}
