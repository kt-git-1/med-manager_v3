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
                            .foregroundColor(.secondary)
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
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
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
