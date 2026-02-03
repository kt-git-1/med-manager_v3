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
                    EmptyStateView(
                        title: NSLocalizedString("caregiver.history.empty.title", comment: "Caregiver history empty title"),
                        message: NSLocalizedString("caregiver.history.empty.message", comment: "Caregiver history empty message")
                    )
                    Button(NSLocalizedString("caregiver.history.empty.action", comment: "Caregiver history empty action")) {
                        onOpenPatients()
                    }
                    .buttonStyle(.borderedProminent)
                    .font(.headline)
                }
                .padding(24)
                .frame(maxWidth: .infinity)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
                .padding(.horizontal, 24)
            } else {
                HistoryMonthView(sessionStore: sessionStore)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .accessibilityIdentifier("CaregiverHistoryView")
    }
}
