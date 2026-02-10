import SwiftUI

struct CaregiverHistoryView: View {
    private let sessionStore: SessionStore
    private let entitlementStore: EntitlementStore?
    private let onOpenPatients: () -> Void

    init(sessionStore: SessionStore, entitlementStore: EntitlementStore? = nil, onOpenPatients: @escaping () -> Void) {
        self.sessionStore = sessionStore
        self.entitlementStore = entitlementStore
        self.onOpenPatients = onOpenPatients
    }

    var body: some View {
        Group {
            if sessionStore.currentPatientId == nil {
                VStack(spacing: 12) {
                    Spacer(minLength: 0)
                    VStack(spacing: 16) {
                        Image(systemName: "clock.badge.questionmark")
                            .font(.system(size: 44))
                            .foregroundStyle(.secondary)
                        Text(NSLocalizedString("caregiver.history.empty.title", comment: "Caregiver history empty title"))
                            .font(.title3.weight(.semibold))
                            .multilineTextAlignment(.center)
                        Text(NSLocalizedString("caregiver.history.empty.message", comment: "Caregiver history empty message"))
                            .font(.body)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .accessibilityIdentifier("CaregiverHistoryEmptyState")
                        Button {
                            onOpenPatients()
                        } label: {
                            Text(NSLocalizedString("caregiver.patients.open", comment: "Open patients tab"))
                                .font(.headline)
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                        }
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity)
                    .glassEffect(.regular, in: .rect(cornerRadius: 20))
                    .padding(.horizontal, 24)
                    Spacer(minLength: 0)
                }
            } else {
                HistoryMonthView(sessionStore: sessionStore, entitlementStore: entitlementStore)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .accessibilityIdentifier("CaregiverHistoryView")
    }
}
