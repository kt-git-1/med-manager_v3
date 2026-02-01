import SwiftUI

struct CaregiverLoginView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @State private var email = ""
    @State private var password = ""
    @State private var errorMessage: String?
    @State private var isLoading = false

    private let authService = AuthService()

    var body: some View {
        VStack(spacing: 12) {
            Text(NSLocalizedString("caregiver.login.title", comment: "Caregiver login title"))
                .font(.title2)
            TextField(NSLocalizedString("caregiver.login.email", comment: "Email label"), text: $email)
                .textInputAutocapitalization(.never)
                .keyboardType(.emailAddress)
                .accessibilityLabel("メールアドレス")
            SecureField(NSLocalizedString("caregiver.login.password", comment: "Password label"), text: $password)
                .accessibilityLabel("パスワード")
            if let errorMessage {
                ErrorStateView(message: errorMessage)
            }
            Button(
                isLoading
                    ? NSLocalizedString("caregiver.login.button.loading", comment: "Logging in")
                    : NSLocalizedString("caregiver.login.button", comment: "Login button")
            ) {
                Task { await login() }
            }
            .disabled(isLoading)
            .accessibilityLabel("ログイン")
        }
        .padding()
        .accessibilityIdentifier("CaregiverLoginView")
    }

    @MainActor
    private func login() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let token = try await authService.login(email: email, password: password)
            sessionStore.saveCaregiverToken(token)
        } catch {
            if let apiError = error as? LocalizedError, let message = apiError.errorDescription {
                errorMessage = message
            } else {
                errorMessage = NSLocalizedString("common.error.login", comment: "Login failed")
            }
        }
    }
}
