import SwiftUI
import UIKit

struct PatientLinkCodeView: View {
    let code: LinkingCodeDTO
    @State private var showCopiedAlert = false

    private let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = AppConstants.japaneseLocale
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter
    }()

    var body: some View {
        VStack(spacing: 20) {
            // Header
            VStack(spacing: 8) {
                Image(systemName: "link.badge.plus")
                    .font(.system(size: 36))
                    .foregroundStyle(.tint)
                    .symbolRenderingMode(.hierarchical)
                Text(NSLocalizedString("caregiver.patients.code.title", comment: "Linking code title"))
                    .font(.title2.weight(.bold))
                Text(NSLocalizedString("caregiver.patients.code.subtitle", comment: "Code subtitle"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            // Code digits display
            HStack(spacing: 8) {
                ForEach(Array(code.code), id: \.self) { char in
                    Text(String(char))
                        .font(.title.weight(.bold).monospacedDigit())
                        .frame(width: 44, height: 56)
                        .background(.fill.quaternary)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }

            // Action buttons
            HStack(spacing: 12) {
                Button {
                    UIPasteboard.general.string = code.code
                    showCopiedAlert = true
                } label: {
                    Label(NSLocalizedString("caregiver.patients.code.copy", comment: "Copy code"), systemImage: "doc.on.doc")
                        .font(.subheadline.weight(.medium))
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(Color.accentColor.opacity(0.12))
                        .foregroundStyle(.tint)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)

                ShareLink(
                    item: code.code,
                    label: {
                        Label(NSLocalizedString("caregiver.patients.code.share", comment: "Share code"), systemImage: "square.and.arrow.up")
                            .font(.subheadline.weight(.medium))
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(Color.accentColor.opacity(0.12))
                            .foregroundStyle(.tint)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                )
                .buttonStyle(.plain)
            }

            // Expiration
            HStack(spacing: 6) {
                Image(systemName: "clock")
                    .font(.caption)
                Text(
                    String(
                        format: NSLocalizedString("caregiver.patients.code.expires", comment: "Expires"),
                        formatter.string(from: code.expiresAt)
                    )
                )
                .font(.subheadline)
            }
            .foregroundStyle(.secondary)
        }
        .padding(24)
        .glassEffect(.regular, in: .rect(cornerRadius: 20))
        .padding(16)
        .alert(
            NSLocalizedString("caregiver.patients.code.copied", comment: "Copied alert"),
            isPresented: $showCopiedAlert
        ) {
            Button(NSLocalizedString("common.ok", comment: "OK"), role: .cancel) {}
        }
        .accessibilityIdentifier("PatientLinkCodeView")
    }
}
