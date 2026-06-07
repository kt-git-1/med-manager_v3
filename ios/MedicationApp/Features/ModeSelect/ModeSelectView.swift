import SwiftUI

struct ModeSelectView: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                header
                    .padding(.top, 50)

                VStack(spacing: 18) {
                    modeCard(
                        illustration: .patient,
                        title: NSLocalizedString("mode.select.patient", comment: "Patient mode"),
                        subtitle: NSLocalizedString("mode.select.patient.subtitle", comment: "Patient subtitle"),
                        tint: Color(red: 0.0, green: 0.55, blue: 0.50)
                    ) {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                            sessionStore.setMode(.patient)
                        }
                    }

                    modeCard(
                        illustration: .family,
                        title: NSLocalizedString("mode.select.caregiver", comment: "Caregiver mode"),
                        subtitle: NSLocalizedString("mode.select.caregiver.subtitle", comment: "Caregiver subtitle"),
                        tint: Color.orange
                    ) {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                            sessionStore.setMode(.caregiver)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 40)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.white)
    }

    private var header: some View {
        VStack(spacing: 12) {
            Text(NSLocalizedString("mode.select.title", comment: "Mode selection title"))
                .font(.system(size: 42, weight: .bold, design: .rounded))
                .foregroundStyle(.primary)
                .multilineTextAlignment(.center)
                .lineSpacing(5)
                .lineLimit(2)
                .minimumScaleFactor(0.82)
        }
        .padding(.horizontal, 28)
    }

    private func modeCard(
        illustration: RoleIllustration,
        title: String,
        subtitle: String,
        tint: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(alignment: .center, spacing: 10) {
                RoleIllustrationView(kind: illustration, tint: tint)
                    .frame(width: 132, height: 132)

                VStack(alignment: .leading, spacing: 8) {
                    Text(title)
                        .font(.title2.weight(.bold))
                        .foregroundStyle(tint)
                        .lineLimit(1)
                        .minimumScaleFactor(0.88)
                    Text(subtitle)
                        .font(.title3)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                        .minimumScaleFactor(0.70)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.trailing, 38)
            }
            .padding(.vertical, 16)
            .padding(.leading, 14)
            .padding(.trailing, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(minHeight: 186)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(alignment: .trailing) {
                Image(systemName: "chevron.right")
                    .font(.headline.weight(.bold))
                    .foregroundStyle(tint)
                    .frame(width: 34, height: 34)
                    .background(tint.opacity(0.10), in: Circle())
                    .padding(.trailing, 12)
            }
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(tint.opacity(0.72), lineWidth: 1.2)
            )
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
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(tint.opacity(0.09))

            Image(kind.assetName)
                .resizable()
                .scaledToFill()
                .padding(kind == .patient ? 6 : 1)
                .clipShape(RoundedRectangle(cornerRadius: 17, style: .continuous))
        }
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(tint.opacity(0.20), lineWidth: 1)
        )
    }
}
