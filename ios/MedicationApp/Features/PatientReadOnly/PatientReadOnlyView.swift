import SwiftUI

struct PatientReadOnlyView: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        TabView {
            NavigationStack {
                MedicationListView(sessionStore: sessionStore)
                    .navigationTitle(NSLocalizedString("patient.readonly.today.title", comment: "Today title"))
                    .navigationBarTitleDisplayMode(.inline)
            }
            .tabItem {
                Label(
                    NSLocalizedString("patient.readonly.today.tab", comment: "Today tab"),
                    systemImage: "calendar"
                )
            }
            PatientHistoryPlaceholderView()
                .tabItem {
                    Label(
                        NSLocalizedString("patient.readonly.history.tab", comment: "History tab"),
                        systemImage: "clock"
                    )
                }
        }
        .accessibilityIdentifier("PatientReadOnlyView")
    }
}

struct PatientHistoryPlaceholderView: View {
    var body: some View {
        VStack(spacing: 8) {
            Text(NSLocalizedString("patient.readonly.history.title", comment: "History title"))
                .font(.headline)
            Text(NSLocalizedString("patient.readonly.history.message", comment: "History message"))
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .padding()
        .accessibilityIdentifier("PatientHistoryPlaceholderView")
    }
}
