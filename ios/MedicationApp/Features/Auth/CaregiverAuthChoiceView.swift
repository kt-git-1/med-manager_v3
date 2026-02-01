import SwiftUI

struct CaregiverAuthChoiceView: View {
    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text(NSLocalizedString("caregiver.auth.title", comment: "Caregiver auth title"))
                    .font(.title2)
                NavigationLink(NSLocalizedString("caregiver.auth.login", comment: "Login choice")) {
                    CaregiverLoginView()
                }
                NavigationLink(NSLocalizedString("caregiver.auth.signup", comment: "Signup choice")) {
                    CaregiverSignupView()
                }
            }
            .padding()
            .accessibilityIdentifier("CaregiverAuthChoiceView")
        }
    }
}
