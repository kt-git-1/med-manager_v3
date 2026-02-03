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
                .background(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(Color(.systemBackground))
                )
                .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
                .padding(.horizontal, 24)
                Spacer(minLength: 0)
            }
            .accessibilityIdentifier("CaregiverAuthChoiceView")
        }
    }
}
