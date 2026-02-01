import SwiftUI

struct PatientLinkCodeView: View {
    let code: LinkingCodeDTO

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
            Text(
                String(
                    format: NSLocalizedString("caregiver.patients.code.expires", comment: "Expires"),
                    formatter.string(from: code.expiresAt)
                )
            )
            .font(.subheadline)
            .foregroundColor(.secondary)
        }
        .padding()
        .accessibilityIdentifier("PatientLinkCodeView")
    }
}
