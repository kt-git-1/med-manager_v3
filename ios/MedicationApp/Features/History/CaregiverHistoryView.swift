import SwiftUI

struct CaregiverHistoryView: View {
    private let sessionStore: SessionStore
    private let onOpenPatients: () -> Void

    init(sessionStore: SessionStore, onOpenPatients: @escaping () -> Void) {
        self.sessionStore = sessionStore
        self.onOpenPatients = onOpenPatients
    }

    var body: some View {
        Group {
            if sessionStore.currentPatientId == nil {
                VStack(spacing: 12) {
                    Spacer(minLength: 0)
                    VStack(spacing: 12) {
                        Text(NSLocalizedString("caregiver.history.empty.title", comment: "Caregiver history empty title"))
                            .font(.title3.weight(.semibold))
                            .multilineTextAlignment(.center)
                        Text(NSLocalizedString("caregiver.history.empty.message", comment: "Caregiver history empty message"))
                            .font(.body)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .accessibilityIdentifier("CaregiverHistoryEmptyState")
                        Button(NSLocalizedString("caregiver.patients.open", comment: "Open patients tab")) {
                            onOpenPatients()
                        }
                        .buttonStyle(.borderedProminent)
                        .font(.headline)
                        .padding(.top, 4)
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 16)
                    .frame(maxWidth: .infinity)
                    .glassEffect(.regular, in: .rect(cornerRadius: 20))
                    .padding(.horizontal, 24)
                    Spacer(minLength: 0)
                }
            } else {
                HistoryMonthView(sessionStore: sessionStore)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .accessibilityIdentifier("CaregiverHistoryView")
    }
}
