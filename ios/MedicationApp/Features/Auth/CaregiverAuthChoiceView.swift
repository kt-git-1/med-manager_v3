import SwiftUI

struct CaregiverAuthChoiceView: View {
    var body: some View {
        NavigationStack {
            VStack {
                Spacer(minLength: 0)
                VStack(spacing: 20) {
                    Text(NSLocalizedString("caregiver.auth.title", comment: "Caregiver auth title"))
                        .font(.title2.weight(.semibold))
                    VStack(spacing: 14) {
                        NavigationLink(NSLocalizedString("caregiver.auth.login", comment: "Login choice")) {
                            CaregiverLoginView()
                        }
                        .buttonStyle(.borderedProminent)
                        .font(.headline)
                        NavigationLink(NSLocalizedString("caregiver.auth.signup", comment: "Signup choice")) {
                            CaregiverSignupView()
                        }
                        .buttonStyle(.bordered)
                        .font(.headline)
                    }
                }
                .padding(24)
                .frame(maxWidth: .infinity)
                .glassEffect(.regular, in: .rect(cornerRadius: 20))
                .padding(.horizontal, 24)
                Spacer(minLength: 0)
            }
            .accessibilityIdentifier("CaregiverAuthChoiceView")
        }
    }
}
