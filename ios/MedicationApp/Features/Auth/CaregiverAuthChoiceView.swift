import SwiftUI

struct CaregiverAuthChoiceView: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        NavigationStack {
            CaregiverScreenBackground {
                ScrollView {
                    VStack(spacing: 22) {
                        CaregiverPatientHeader(
                            title: NSLocalizedString("caregiver.auth.title", comment: "Caregiver auth title"),
                            patientName: nil,
                            systemImage: "person.badge.shield.checkmark.fill",
                            subtitle: NSLocalizedString("caregiver.auth.subtitle", comment: "Auth subtitle"),
                            subtitleLineLimit: 2
                        )
                        .padding(.top, 48)

                        VStack(spacing: 14) {
                            NavigationLink {
                                CaregiverLoginView()
                            } label: {
                                authCard(
                                    icon: "arrow.right.circle.fill",
                                    title: NSLocalizedString("caregiver.auth.login", comment: "Login choice"),
                                    subtitle: NSLocalizedString("caregiver.auth.login.subtitle", comment: "Login subtitle"),
                                    tint: CaregiverUI.teal
                                )
                            }
                            .buttonStyle(.plain)
                            .simultaneousGesture(TapGesture().onEnded {
                                AnalyticsService.shared.logAuth(.loginMethodSelected, method: .email)
                            })

                            NavigationLink {
                                CaregiverSignupView()
                            } label: {
                                authCard(
                                    icon: "person.badge.plus.fill",
                                    title: NSLocalizedString("caregiver.auth.signup", comment: "Signup choice"),
                                    subtitle: NSLocalizedString("caregiver.auth.signup.subtitle", comment: "Signup subtitle"),
                                    tint: CaregiverUI.orange
                                )
                            }
                            .buttonStyle(.plain)
                            .simultaneousGesture(TapGesture().onEnded {
                                AnalyticsService.shared.logAuth(.signupMethodSelected, method: .email)
                            })
                        }

                        Button {
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                sessionStore.resetMode()
                            }
                        } label: {
                            Label(NSLocalizedString("caregiver.auth.back", comment: "Back to mode select"), systemImage: "chevron.left")
                                .font(.headline.weight(.semibold))
                                .foregroundStyle(CaregiverUI.teal)
                                .frame(maxWidth: .infinity)
                                .frame(height: 52)
                                .background(CaregiverUI.cardBackground.opacity(0.75), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                                .overlay {
                                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                                        .stroke(CaregiverUI.teal.opacity(0.18), lineWidth: 1)
                                }
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 32)
                }
            }
            .navigationDestination(isPresented: $sessionStore.shouldNavigateToCaregiverLogin) {
                CaregiverLoginView()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityIdentifier("CaregiverAuthChoiceView")
            .onAppear {
                AnalyticsService.shared.logScreenViewed(.caregiverAuthChoice)
            }
        }
    }

    private func authCard(icon: String, title: String, subtitle: String, tint: Color) -> some View {
        CaregiverCard(accent: tint) {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(tint)
                    .frame(width: 54, height: 54)
                    .background(tint.opacity(0.12), in: Circle())

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.title2.weight(.bold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.82)
                    Text(subtitle)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                        .minimumScaleFactor(0.78)
                }

                Spacer(minLength: 0)

                Image(systemName: "chevron.right")
                    .font(.headline.weight(.bold))
                    .foregroundStyle(tint)
                    .frame(width: 34, height: 34)
                    .background(tint.opacity(0.10), in: Circle())
            }
        }
    }
}
