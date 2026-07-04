import SwiftUI

struct CaregiverSignupView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    @EnvironmentObject private var toastPresenter: ToastPresenter
    @State private var email = ""
    @State private var password = ""
    @State private var passwordConfirmation = ""
    @State private var errorMessage: String?
    @State private var infoMessage: String?
    @State private var canResendConfirmationEmail = false
    @State private var isLoading = false
    @State private var isResending = false
    @State private var resendCooldownRemainingSeconds = 0

    private let authService = AuthService()
    private let resendCooldownSeconds = 60
    private var trimmedEmail: String {
        email.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var isFormReady: Bool {
        !trimmedEmail.isEmpty && !password.isEmpty && !passwordConfirmation.isEmpty
    }

    private var isResendCooldownActive: Bool {
        resendCooldownRemainingSeconds > 0
    }

    private var isEmailFormatValid: Bool {
        Self.isValidEmail(trimmedEmail)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Spacer(minLength: 52)

                VStack(spacing: 28) {
                    // Header
                    VStack(spacing: 12) {
                        Image(systemName: "person.badge.plus.fill")
                            .font(.system(size: 52))
                            .foregroundStyle(.tint)
                            .symbolRenderingMode(.hierarchical)
                        Text(NSLocalizedString("caregiver.signup.title", comment: "Caregiver signup title"))
                            .font(.title.weight(.bold))
                            .multilineTextAlignment(.center)
                        Text(NSLocalizedString("caregiver.signup.subtitle", comment: "Caregiver signup subtitle"))
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
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
                                .accessibilityLabel(NSLocalizedString("a11y.email", comment: "Email"))
                        }
                        .padding(14)
                        .background(.fill.quaternary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))

                        HStack(spacing: 12) {
                            Image(systemName: "lock.fill")
                                .foregroundStyle(.secondary)
                                .frame(width: 20)
                            SecureField(NSLocalizedString("caregiver.signup.password", comment: "Password label"), text: $password)
                                .accessibilityLabel(NSLocalizedString("a11y.password", comment: "Password"))
                        }
                        .padding(14)
                        .background(.fill.quaternary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))

                        HStack(spacing: 12) {
                            Image(systemName: "lock.rotation")
                                .foregroundStyle(.secondary)
                                .frame(width: 20)
                            SecureField(
                                NSLocalizedString("caregiver.signup.passwordConfirmation", comment: "Password confirmation label"),
                                text: $passwordConfirmation
                            )
                            .accessibilityLabel(NSLocalizedString("a11y.password.confirmation", comment: "Password confirmation"))
                        }
                        .padding(14)
                        .background(.fill.quaternary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    if let errorMessage {
                        ErrorStateView(message: errorMessage)
                    }

                    if let infoMessage {
                        SignupInfoView(message: infoMessage)
                    }

                    if canResendConfirmationEmail {
                        Button {
                            Task { await resendConfirmationEmail() }
                        } label: {
                            Group {
                                if isResending {
                                    ProgressView()
                                } else if isResendCooldownActive {
                                    Label(
                                        String(
                                            format: NSLocalizedString(
                                                "caregiver.signup.resend.cooldown",
                                                comment: "Confirmation email resend cooldown"
                                            ),
                                            resendCooldownRemainingSeconds
                                        ),
                                        systemImage: "clock.fill"
                                    )
                                } else {
                                    Label(
                                        NSLocalizedString("caregiver.signup.resend.button", comment: "Resend confirmation email"),
                                        systemImage: "envelope.arrow.triangle.branch"
                                    )
                                }
                            }
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(isResendCooldownActive ? .secondary : Color.accentColor)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(.fill.quaternary, in: RoundedRectangle(cornerRadius: 14))
                        }
                        .disabled(isLoading || isResending || isResendCooldownActive)
                        .accessibilityLabel(NSLocalizedString("a11y.signup.resend", comment: "Resend confirmation email"))
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
                    .disabled(isLoading || !isFormReady)
                    .opacity(isFormReady ? 1 : 0.5)
                    .accessibilityLabel(NSLocalizedString("a11y.signup", comment: "Signup"))
                }
                .padding(28)
                .frame(maxWidth: .infinity)
                .glassEffect(.regular, in: .rect(cornerRadius: 24))
                .padding(.horizontal, 24)

                Spacer(minLength: 52)
            }
        }
        .scrollIndicators(.hidden)
        .onReceive(Timer.publish(every: 1, on: .main, in: .common).autoconnect()) { _ in
            guard resendCooldownRemainingSeconds > 0 else { return }
            resendCooldownRemainingSeconds -= 1
        }
        .onChange(of: email) { _, _ in clearTransientMessages() }
        .onChange(of: password) { _, _ in clearTransientMessages() }
        .onChange(of: passwordConfirmation) { _, _ in clearTransientMessages() }
        .accessibilityIdentifier("CaregiverSignupView")
    }

    @MainActor
    private func signup() async {
        errorMessage = nil
        infoMessage = nil
        canResendConfirmationEmail = false
        resendCooldownRemainingSeconds = 0

        guard isEmailFormatValid else {
            errorMessage = NSLocalizedString("auth.error.invalidEmail", comment: "Invalid email")
            return
        }

        guard password.count >= 6 else {
            errorMessage = NSLocalizedString("auth.error.weakPassword", comment: "Weak password")
            return
        }

        guard password == passwordConfirmation else {
            errorMessage = NSLocalizedString("auth.error.passwordMismatch", comment: "Password mismatch")
            return
        }

        isLoading = true
        defer { isLoading = false }
        do {
            let session = try await authService.signup(email: trimmedEmail, password: password)
            if !session.hasAccessToken {
                infoMessage = NSLocalizedString("caregiver.signup.confirm.email", comment: "Email confirmation required")
                canResendConfirmationEmail = true
                startResendCooldown()
            } else {
                sessionStore.saveCaregiverSession(session)
            }
        } catch {
            if let apiError = error as? LocalizedError, let message = apiError.errorDescription {
                errorMessage = message
            } else {
                errorMessage = NSLocalizedString("common.error.signup", comment: "Signup failed")
            }
        }
    }

    @MainActor
    private func resendConfirmationEmail() async {
        errorMessage = nil
        infoMessage = nil

        guard !isResendCooldownActive else { return }

        guard isEmailFormatValid else {
            errorMessage = NSLocalizedString("auth.error.invalidEmail", comment: "Invalid email")
            return
        }

        isResending = true
        defer { isResending = false }
        do {
            try await authService.resendSignupConfirmation(email: trimmedEmail)
            infoMessage = NSLocalizedString("caregiver.signup.resend.sent", comment: "Confirmation email resent")
            startResendCooldown()
            toastPresenter.show(
                NSLocalizedString("caregiver.signup.resend.toast", comment: "Confirmation email resend toast"),
                kind: .success
            )
        } catch {
            if let apiError = error as? LocalizedError, let message = apiError.errorDescription {
                errorMessage = message
                if message == NSLocalizedString(
                    "caregiver.signup.resend.tooManyRequests",
                    comment: "Too many confirmation email resend requests"
                ) {
                    startResendCooldown()
                }
            } else {
                errorMessage = NSLocalizedString("caregiver.signup.resend.error", comment: "Resend failed")
            }
        }
    }

    private func startResendCooldown() {
        resendCooldownRemainingSeconds = resendCooldownSeconds
    }

    private func clearTransientMessages() {
        if errorMessage != nil {
            errorMessage = nil
        }
        if infoMessage != nil {
            infoMessage = nil
        }
    }

    private static func isValidEmail(_ value: String) -> Bool {
        let pattern = #"^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$"#
        return value.range(of: pattern, options: [.regularExpression, .caseInsensitive]) != nil
    }
}

private struct SignupInfoView: View {
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "envelope.badge.fill")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(.blue)
            Text(message)
                .font(.headline.weight(.semibold))
                .foregroundStyle(.primary)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
        }
        .frame(maxWidth: .infinity)
        .padding(22)
        .glassEffect(.regular, in: .rect(cornerRadius: 18))
        .padding(.horizontal, 20)
        .accessibilityLabel(message)
    }
}
