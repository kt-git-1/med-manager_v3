import SwiftUI

struct CaregiverAuthChoiceView: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Spacer()

                // Header
                VStack(spacing: 16) {
                    Image(systemName: "person.badge.shield.checkmark.fill")
                        .font(.system(size: 56))
                        .foregroundStyle(.tint)
                        .symbolRenderingMode(.hierarchical)

                    VStack(spacing: 8) {
                        Text(NSLocalizedString("caregiver.auth.title", comment: "Caregiver auth title"))
                            .font(.largeTitle.weight(.bold))
                        Text(NSLocalizedString("caregiver.auth.subtitle", comment: "Auth subtitle"))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                }

                Spacer()
                    .frame(maxHeight: 48)

                // Auth options
                VStack(spacing: 14) {
                    NavigationLink {
                        CaregiverLoginView()
                    } label: {
                        authCard(
                            icon: "arrow.right.circle.fill",
                            title: NSLocalizedString("caregiver.auth.login", comment: "Login choice"),
                            subtitle: NSLocalizedString("caregiver.auth.login.subtitle", comment: "Login subtitle"),
                            filled: true
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        CaregiverSignupView()
                    } label: {
                        authCard(
                            icon: "person.badge.plus.fill",
                            title: NSLocalizedString("caregiver.auth.signup", comment: "Signup choice"),
                            subtitle: NSLocalizedString("caregiver.auth.signup.subtitle", comment: "Signup subtitle"),
                            filled: false
                        )
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 24)

                Spacer()

                // Back to mode select
                Button {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                        sessionStore.resetMode()
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "chevron.left")
                            .font(.subheadline.weight(.medium))
                        Text(NSLocalizedString("caregiver.auth.back", comment: "Back to mode select"))
                            .font(.subheadline)
                    }
                    .foregroundStyle(.secondary)
                }
                .padding(.bottom, 24)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityIdentifier("CaregiverAuthChoiceView")
        }
    }

    private func authCard(icon: String, title: String, subtitle: String, filled: Bool) -> some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(filled ? Color.white : Color.accentColor)
                .frame(width: 48, height: 48)
                .background(filled ? Color.accentColor : Color.accentColor.opacity(0.12))
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
    }
}
