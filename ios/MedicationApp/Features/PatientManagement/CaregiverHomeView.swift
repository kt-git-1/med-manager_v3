import SwiftUI

struct CaregiverHomeView: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        TabView {
            CaregiverMedicationPlaceholderView()
                .tabItem {
                    Label(
                        NSLocalizedString("caregiver.tabs.medications", comment: "Medications tab"),
                        systemImage: "pills"
                    )
                }
            PatientManagementView(sessionStore: sessionStore)
                .tabItem {
                    Label(
                        NSLocalizedString("caregiver.tabs.patients", comment: "Patients tab"),
                        systemImage: "person.2"
                    )
                }
        }
    }
}

struct CaregiverMedicationPlaceholderView: View {
    var body: some View {
        VStack(spacing: 8) {
            Text(NSLocalizedString("caregiver.medications.placeholder.title", comment: "Medications placeholder"))
                .font(.headline)
            Text(NSLocalizedString("caregiver.medications.placeholder.message", comment: "Medications placeholder message"))
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .padding()
        .accessibilityIdentifier("CaregiverMedicationPlaceholderView")
    }
}
