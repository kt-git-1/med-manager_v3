import SwiftUI

struct ModeSelectView: View {
    @EnvironmentObject private var sessionStore: SessionStore

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            // Header
            VStack(spacing: 16) {
                Image(systemName: "cross.case.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(.tint)
                    .symbolRenderingMode(.hierarchical)

                VStack(spacing: 8) {
                    Text(NSLocalizedString("mode.select.title", comment: "Mode selection title"))
                        .font(.largeTitle.weight(.bold))
                    Text(NSLocalizedString("mode.select.subtitle", comment: "Mode selection subtitle"))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
            }

            Spacer()
                .frame(maxHeight: 48)

            // Mode cards
            VStack(spacing: 14) {
                modeCard(
                    icon: "person.2.fill",
                    title: NSLocalizedString("mode.select.caregiver", comment: "Caregiver mode"),
                    subtitle: NSLocalizedString("mode.select.caregiver.subtitle", comment: "Caregiver subtitle"),
                    filled: true
                ) {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                        sessionStore.setMode(.caregiver)
                    }
                }

                modeCard(
                    icon: "person.fill",
                    title: NSLocalizedString("mode.select.patient", comment: "Patient mode"),
                    subtitle: NSLocalizedString("mode.select.patient.subtitle", comment: "Patient subtitle"),
                    filled: false
                ) {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                        sessionStore.setMode(.patient)
                    }
                }
            }
            .padding(.horizontal, 24)

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func modeCard(
        icon: String,
        title: String,
        subtitle: String,
        filled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
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
        .buttonStyle(.plain)
    }
}
