import SwiftUI

struct PatientReadOnlyView: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        NavigationStack {
            MedicationListView(sessionStore: sessionStore)
                .navigationTitle(NSLocalizedString("patient.readonly.title", comment: "Patient list title"))
                .navigationBarTitleDisplayMode(.inline)
        }
        .accessibilityIdentifier("PatientReadOnlyView")
    }
}
