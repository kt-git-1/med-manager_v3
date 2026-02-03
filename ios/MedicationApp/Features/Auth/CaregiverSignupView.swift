import SwiftUI

struct CaregiverSignupView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @State private var email = ""
    @State private var password = ""
    @State private var errorMessage: String?
    @State private var isLoading = false

    private let authService = AuthService()

    var body: some View {
        VStack {
            Spacer(minLength: 0)
            VStack(spacing: 16) {
                Text(NSLocalizedString("caregiver.signup.title", comment: "Caregiver signup title"))
                    .font(.title2.weight(.semibold))
                VStack(spacing: 12) {
                    TextField(NSLocalizedString("caregiver.signup.email", comment: "Email label"), text: $email)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .textFieldStyle(.roundedBorder)
                        .font(.body)
                        .accessibilityLabel("メールアドレス")
                    SecureField(NSLocalizedString("caregiver.signup.password", comment: "Password label"), text: $password)
                        .textFieldStyle(.roundedBorder)
                        .font(.body)
                        .accessibilityLabel("パスワード")
                }
                if let errorMessage {
                    ErrorStateView(message: errorMessage)
                }
                Button(
                    isLoading
                        ? NSLocalizedString("caregiver.signup.button.loading", comment: "Signing up")
                        : NSLocalizedString("caregiver.signup.button", comment: "Signup button")
                ) {
                    Task { await signup() }
                }
                .buttonStyle(.borderedProminent)
                .font(.headline)
                .disabled(isLoading)
                .accessibilityLabel("サインアップ")
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
        .accessibilityIdentifier("CaregiverSignupView")
    }

    @MainActor
    private func signup() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let token = try await authService.signup(email: email, password: password)
            if token.isEmpty {
                errorMessage = "Check your email to confirm your account."
            } else {
                sessionStore.saveCaregiverToken(token)
            }
        } catch {
            if let apiError = error as? LocalizedError, let message = apiError.errorDescription {
                errorMessage = message
            } else {
                errorMessage = NSLocalizedString("common.error.signup", comment: "Signup failed")
            }
        }
    }
}
