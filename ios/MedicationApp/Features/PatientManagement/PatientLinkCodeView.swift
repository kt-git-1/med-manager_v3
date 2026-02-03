import SwiftUI
import UIKit

struct PatientLinkCodeView: View {
    let code: LinkingCodeDTO
    @State private var showCopiedAlert = false

    private let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ja_JP")
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter
    }()

    var body: some View {
        VStack(spacing: 12) {
            Text(NSLocalizedString("caregiver.patients.code.title", comment: "Linking code title"))
                .font(.title2)
            Text(code.code)
                .font(.largeTitle)
                .monospacedDigit()
            HStack(spacing: 12) {
                Button(NSLocalizedString("caregiver.patients.code.copy", comment: "Copy code")) {
                    UIPasteboard.general.string = code.code
                    showCopiedAlert = true
                }
                .buttonStyle(.bordered)
                ShareLink(
                    item: code.code,
                    label: {
                        Text(NSLocalizedString("caregiver.patients.code.share", comment: "Share code"))
                    }
                )
                .buttonStyle(.bordered)
            }
            Text(
                String(
                    format: NSLocalizedString("caregiver.patients.code.expires", comment: "Expires"),
                    formatter.string(from: code.expiresAt)
                )
            )
            .font(.subheadline)
            .foregroundColor(.secondary)
        }
        .padding(20)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: Color.black.opacity(0.08), radius: 10, y: 4)
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
