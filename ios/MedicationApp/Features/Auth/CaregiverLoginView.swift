import SwiftUI

struct CaregiverLoginView: View {
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
                    Image(systemName: "person.circle.fill")
                        .font(.system(size: 52))
                        .foregroundStyle(.tint)
                        .symbolRenderingMode(.hierarchical)
                    Text(NSLocalizedString("caregiver.login.title", comment: "Caregiver login title"))
                        .font(.title.weight(.bold))
                }

                // Form fields
                VStack(spacing: 12) {
                    HStack(spacing: 12) {
                        Image(systemName: "envelope.fill")
                            .foregroundStyle(.secondary)
                            .frame(width: 20)
                        TextField(NSLocalizedString("caregiver.login.email", comment: "Email label"), text: $email)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.emailAddress)
                            .accessibilityLabel(NSLocalizedString("a11y.email", comment: "Email"))
                    }
                    .padding(14)
                    .background(.fill.quaternary)
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    HStack(spacing: 12) {
                        Image(systemName: "lock.fill")
                            .foregroundStyle(.secondary)
                            .frame(width: 20)
                        SecureField(NSLocalizedString("caregiver.login.password", comment: "Password label"), text: $password)
                            .accessibilityLabel(NSLocalizedString("a11y.password", comment: "Password"))
                    }
                    .padding(14)
                    .background(.fill.quaternary)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                if let errorMessage {
                    ErrorStateView(message: errorMessage)
                }

                // Login button
                Button {
                    Task { await login() }
                } label: {
                    Group {
                        if isLoading {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Text(NSLocalizedString("caregiver.login.button", comment: "Login button"))
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
                .accessibilityLabel(NSLocalizedString("a11y.login", comment: "Login"))
            }
            .padding(28)
            .frame(maxWidth: .infinity)
            .glassEffect(.regular, in: .rect(cornerRadius: 24))
            .padding(.horizontal, 24)

            Spacer()
        }
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
