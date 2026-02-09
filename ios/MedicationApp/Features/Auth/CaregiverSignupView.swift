import SwiftUI

struct CaregiverSignupView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @State private var email = ""
    @State private var password = ""
    @State private var errorMessage: String?
    @State private var isLoading = false

    private let authService = AuthService()

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            VStack(spacing: 28) {
                // Header
                VStack(spacing: 12) {
                    Image(systemName: "person.badge.plus.fill")
                        .font(.system(size: 52))
                        .foregroundStyle(.tint)
                        .symbolRenderingMode(.hierarchical)
                    Text(NSLocalizedString("caregiver.signup.title", comment: "Caregiver signup title"))
                        .font(.title.weight(.bold))
                }

                // Form fields
                VStack(spacing: 12) {
                    HStack(spacing: 12) {
                        Image(systemName: "envelope.fill")
                            .foregroundStyle(.secondary)
                            .frame(width: 20)
                        TextField(NSLocalizedString("caregiver.signup.email", comment: "Email label"), text: $email)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.emailAddress)
                            .accessibilityLabel("メールアドレス")
                    }
                    .padding(14)
                    .background(.fill.quaternary)
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    HStack(spacing: 12) {
                        Image(systemName: "lock.fill")
                            .foregroundStyle(.secondary)
                            .frame(width: 20)
                        SecureField(NSLocalizedString("caregiver.signup.password", comment: "Password label"), text: $password)
                            .accessibilityLabel("パスワード")
                    }
                    .padding(14)
                    .background(.fill.quaternary)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                if let errorMessage {
                    ErrorStateView(message: errorMessage)
                }

                // Signup button
                Button {
                    Task { await signup() }
                } label: {
                    Group {
                        if isLoading {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Text(NSLocalizedString("caregiver.signup.button", comment: "Signup button"))
                        }
                    }
                    .font(.headline)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                }
                .disabled(isLoading || email.isEmpty || password.isEmpty)
                .opacity(email.isEmpty || password.isEmpty ? 0.5 : 1)
                .accessibilityLabel("サインアップ")
            }
            .padding(28)
            .frame(maxWidth: .infinity)
            .glassEffect(.regular, in: .rect(cornerRadius: 24))
            .padding(.horizontal, 24)

            Spacer()
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
                errorMessage = NSLocalizedString("caregiver.signup.confirm.email", comment: "Email confirmation required")
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
