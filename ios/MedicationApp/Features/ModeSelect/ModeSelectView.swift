import SwiftUI

struct ModeSelectView: View {
    @EnvironmentObject private var sessionStore: SessionStore
    private let patientTint = PatientUI.teal
    private let caregiverTint = CaregiverUI.orange

    var body: some View {
        ZStack {
            ModeSelectBackground()

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    header
                        .padding(.top, 52)

                    VStack(spacing: 14) {
                        modeCard(
                            illustration: .patient,
                            title: NSLocalizedString("mode.select.patient", comment: "Patient mode"),
                            subtitle: NSLocalizedString("mode.select.patient.subtitle", comment: "Patient subtitle"),
                            detail: NSLocalizedString("mode.select.patient.detail", comment: "Patient detail"),
                            symbol: "checkmark.seal.fill",
                            tint: patientTint
                        ) {
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                sessionStore.setMode(.patient)
                            }
                        }

                        modeCard(
                            illustration: .family,
                            title: NSLocalizedString("mode.select.caregiver", comment: "Caregiver mode"),
                            subtitle: NSLocalizedString("mode.select.caregiver.subtitle", comment: "Caregiver subtitle"),
                            detail: NSLocalizedString("mode.select.caregiver.detail", comment: "Caregiver detail"),
                            symbol: "person.2.fill",
                            tint: caregiverTint
                        ) {
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                sessionStore.setMode(.caregiver)
                            }
                        }
                    }
                    .padding(.bottom, 34)
                }
                .padding(.horizontal, 22)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Image(systemName: "pills.fill")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(patientTint)
                    .frame(width: 28, height: 28)
                    .background(patientTint.opacity(0.12), in: Circle())

                Text(NSLocalizedString("mode.select.appName", comment: "App name"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.readableSecondaryText)
            }

            Text(NSLocalizedString("mode.select.title", comment: "Mode selection title"))
                .font(.system(size: 38, weight: .bold, design: .rounded))
                .foregroundStyle(.primary)
                .multilineTextAlignment(.leading)
                .lineSpacing(3)
                .lineLimit(2)
                .minimumScaleFactor(0.82)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func modeCard(
        illustration: RoleIllustration,
        title: String,
        subtitle: String,
        detail: String,
        symbol: String,
        tint: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 18) {
                HStack(alignment: .top, spacing: 16) {
                    RoleIllustrationView(kind: illustration, tint: tint)
                        .frame(width: 112, height: 112)

                    VStack(alignment: .leading, spacing: 10) {
                        Label {
                            Text(detail)
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(tint)
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                        } icon: {
                            Image(systemName: symbol)
                                .font(.caption.weight(.bold))
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 7)
                        .background(tint.opacity(0.10), in: Capsule())

                        Text(title)
                            .font(.system(size: 24, weight: .bold, design: .rounded))
                            .foregroundStyle(.primary)
                            .lineLimit(2)
                            .minimumScaleFactor(0.82)

                        Text(subtitle)
                            .font(.body.weight(.medium))
                            .foregroundStyle(Color.readableSecondaryText)
                            .lineLimit(2)
                            .minimumScaleFactor(0.78)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                HStack {
                    Text(NSLocalizedString("mode.select.start", comment: "Start"))
                        .font(.headline.weight(.bold))
                        .foregroundStyle(tint)

                    Spacer()

                    Image(systemName: "arrow.right")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 38, height: 38)
                        .background(tint, in: Circle())
                        .shadow(color: tint.opacity(0.25), radius: 12, x: 0, y: 6)
                }
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(ModeSelectUI.cardBackground)
                    .shadow(color: tint.opacity(0.14), radius: 18, x: 0, y: 10)
                    .shadow(color: Color.black.opacity(0.04), radius: 8, x: 0, y: 3)
            )
            .overlay(alignment: .topTrailing) {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(tint.opacity(0.16), lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
    }
}

private enum RoleIllustration {
    case patient
    case family

    var assetName: String {
        switch self {
        case .patient:
            return "RolePatient"
        case .family:
            return "RoleFamily"
        }
    }
}

private struct RoleIllustrationView: View {
    let kind: RoleIllustration
    let tint: Color

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [tint.opacity(0.14), tint.opacity(0.06)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

            Image(kind.assetName)
                .resizable()
                .scaledToFill()
                .padding(kind == .patient ? 7 : 2)
                .clipShape(RoundedRectangle(cornerRadius: 21, style: .continuous))
        }
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(tint.opacity(0.18), lineWidth: 1)
        )
    }
}

private struct ModeSelectBackground: View {
    var body: some View {
        ModeSelectUI.background
        .ignoresSafeArea()
    }
}

private enum ModeSelectUI {
    static let background = AppTheme.screenBackground
    static let cardBackground = AppTheme.cardBackground
}
